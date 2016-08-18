package org.gatt_ip;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.le.ScanRecord;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.util.Log;
import android.util.SparseArray;

import org.gatt_ip.util.Util;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

public class GATTIP implements ServiceConnection, DeviceEventListener
{
    private static final String TAG = GATTIP.class.getName();
    private static final int MAX_NUMBER_OF_REQUESTS = 30;

    private static boolean requestIsProcessing = false;

    private final ScheduledExecutorService worker = Executors.newScheduledThreadPool(1);

    private Context m_context;

    private GATTIPListener m_listener;

    private LinkedBlockingQueue<JSONObject> m_message_queue;
    private static final int REQ_TIME_OUT_IN_MSG_QUEUE = 1000;

    public BluetoothLEService m_service;

    private List<String> m_filtered_services;

    private boolean m_notifications;

    private JSONObject m_current_request;
    private String serviceId, characteristicId;

    private JSONObject charc_json;
    private JSONArray desc_json;
    private JSONObject scanDevices;

    private ScheduledFuture<?> m_time_error_future;
    protected final ArrayList<DeviceEventListener> m_listeners = new ArrayList<>();

    private static Boolean m_isNotifying;

    /*
     * ServiceConnection Implementation
     */

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder)
    {
        try {
            m_service = (BluetoothLEService) ((BluetoothLEService.BluetoothLEBinder) iBinder).getService();
            m_service.registerDeviceEventListener(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName)
    {
        try {
            if(m_context != null) {
                m_context.stopService(new Intent(m_context,BluetoothLEService.class));
                m_context.unbindService(this);
            }
            if(m_service != null) {
                m_service.unregisterDeviceEventListener(this);
                m_service = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /*
     * Public GATT-IP functions
     */
    public GATTIP(Context ctx)
    {
        m_message_queue = new LinkedBlockingQueue<>(MAX_NUMBER_OF_REQUESTS);

        m_context = ctx;
        m_context.bindService(new Intent(m_context, BluetoothLEService.class), this, 0);
        m_context.startService(new Intent(m_context, BluetoothLEService.class));

        m_filtered_services = new ArrayList<>();
        m_current_request = new JSONObject();

        final Thread processing_thread = new Thread() {
            @Override
            public void run() {
                while (true) {
                    try {
//                        processRequest(m_message_queue.take());
                        JSONObject request = m_message_queue.poll();

                        if (request == null) {
                            request = m_message_queue.take();
                        }

                        processRequest(request);
                    } catch (JSONException | InterruptedException e) {
                        // TODO: perhaps properly handle InterruptedException
                        throw new RuntimeException(e);
                    }

                    int couter = 0;
                    while (requestIsProcessing) {
                        couter++;
                        Log.d("MSG_QUEUE :", Integer.toString(couter) +": "+ requestIsProcessing);

                        if(couter == REQ_TIME_OUT_IN_MSG_QUEUE){
                            JSONObject currentReq = new JSONObject();
                            try {
                                if (m_current_request.has(Constants.kMethod)) {
                                    String method = m_current_request.getString(Constants.kMethod);
                                    currentReq.put(Constants.kResult, method);
                                    JSONObject errorObj = new JSONObject();
                                    errorObj.put(Constants.kMessageField, "Timed out while processing the Request");
                                    currentReq.put(Constants.kError, errorObj);
                                    sendResponse(currentReq, false);
                                }else{
                                    Log.w(TAG,"Timeout occurred, but we are not sending the response");
                                    requestIsProcessing = false;
                                }
                            }catch (JSONException ex){
                                ex.printStackTrace();
                            }
                            break;
                        }

                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        };

        processing_thread.start();
    }

    // set the reference for listener when we got request from client
    public void setGATTIPListener(GATTIPListener GATTIPlistener)
    {
        m_listener = GATTIPlistener;
    }

    public boolean isPoweredOn() {
        boolean isPowerOn = false;
        if (m_service != null) {
            m_service.getBluetoothAdapterState();
            if (m_service.getServiceState() == InterfaceService.ServiceState.SERVICE_STATE_ACTIVE) {
                isPowerOn = true;
            } else {
                isPowerOn = false;
            }
        }

        return isPowerOn;
    }


    // method to call request coming from client
    public void request(String gattipMesg) throws JSONException
    {
        if (gattipMesg == null) {
            sendInvalidRequest();
            return;
        }

        try {
            // for handling multiple commands from client
            JSONObject request = new JSONObject(gattipMesg);

            //Before processing req, check if the BLEService is exists
            if(m_service == null) {
                String method = request.getString(Constants.kMethod);
                String requestID = getRequestID(request);
                String requestSessionId = getRequestSessionID(request);
                sendGatewayDownResponse(method, requestID, requestSessionId);
                return;
            }

            if (!m_message_queue.offer(request)) {
                Log.d(TAG, "LinkedBlockingQueue.offer: capacity full.");

                // If queue is full, we wait until we can add element.
                m_message_queue.put(request);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

//        TODO: Evaluate proper time out error.
//        m_time_error_future = worker.schedule(m_time_out_error_thread, 10L, TimeUnit.SECONDS);
    }

    /*
     * Private GATT-IP functions
     */

    private void sendResponse(JSONObject jsonData, boolean notification) throws JSONException
    {
        String request="", response = "";

        if (m_listener == null) {
            return;
        }

//        if (m_time_error_future != null) {
//            m_time_error_future.cancel(true);
//            m_time_error_future = null;
//        }

        jsonData.put(Constants.kJsonrpc, Constants.kJsonrpcVersion);

        if(m_current_request.has(Constants.kMethod))
            request = m_current_request.getString(Constants.kMethod);
        if(jsonData.has(Constants.kResult))
            response = jsonData.getString(Constants.kResult);

        if (request.equals(response) && !notification) {
            String requestID = getRequestID(m_current_request);
            String requestSessionId = getRequestSessionID(m_current_request);

            if (requestID != null) {
                jsonData.put(Constants.kRequestId, requestID);
            }
            if (requestSessionId != null) {
                jsonData.put(Constants.kRequestSessionId, requestSessionId);
            }
            requestIsProcessing = false;
        }else if(!notification){
            Log.w(TAG, "---->>>>>>>>>>>>>>> Req & Res are not matching");
            requestIsProcessing = false;
        }
        Log.v(TAG, "Sending Response : ---------->"+jsonData.toString());
        m_listener.response(jsonData.toString());
    }

    // sending response to client for requested command
    private void sendResponse(JSONObject jsonData) throws JSONException
    {
        sendResponse(jsonData, false);
    }

    private void processRequest(JSONObject request) throws JSONException
    {
        Log.d(TAG, "Processing Request :  <--------"+request.toString());
        String method = null;
        requestIsProcessing = true;

        m_current_request = request;

        try {
            method = m_current_request.getString(Constants.kMethod);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        if (method == null) {
            JSONObject errorObj = new JSONObject();
            errorObj.put(Constants.kCode,Constants.kInvalidRequest);

            JSONObject jsonData = new JSONObject();
            jsonData.put(Constants.kError, errorObj);

            String requestID = getRequestID(m_current_request);
            String requestSessionID = getRequestSessionID(m_current_request);

            if(requestID != null) {
                jsonData.put(Constants.kRequestId, requestID);
            }
            if(requestSessionID != null) {
                jsonData.put(Constants.kRequestSessionId, requestSessionID);
            }

            sendResponse(jsonData);
        } else switch (method) {
            case Constants.kConfigure:
                configure(request);
                break;
            case Constants.kConnect:
                connectPeripheral(request);
                break;
            case Constants.kDisconnect:
                disconnectPeripheral(request);
                break;
            case Constants.kScanForPeripherals:
                scanForPeripherals(request);
                break;
            case Constants.kStopScanning:
                stopScanning(request);
                break;
            case Constants.kCentralState:
                getState();
                break;
            case Constants.kGetServices:
                getServices(request);
                break;
            case Constants.kGetCharacteristics:
                getCharacteristics(request);
                break;
            case Constants.kGetDescriptors:
                getDescriptors(request);
                break;
            case Constants.kGetCharacteristicValue:
                getCharacteristicValue(request);
                break;
            case Constants.kGetDescriptorValue:
                getDescriptorValue(request);
                break;
            case Constants.kWriteDescriptorValue:
                writeDescriptorValue(request);
                break;
            case Constants.kWriteCharacteristicValue:
                writeCharacteristicValue(request);
                break;
            case Constants.kSetValueNotification:
                setValueNotification(request);
                break;
            case Constants.kGetPeripheralState:
                getPeripheralState(request);
                break;
            case Constants.kGetRSSI:
                getRSSI(request);
                break;
            default:
                JSONObject invalidMethod = new JSONObject();
                invalidMethod.put("Error", "Your Method is invalid");
                sendResponse(invalidMethod);
                break;
        }

        Log.d(TAG, "Proccessing complete");
    }

    private void configure(JSONObject reqObj) throws JSONException   {
        //        TODO: Needs to be discussed.
        List<BluetoothGatt> connected_devices = new ArrayList<>();
        if(m_service!=null) {
            connected_devices = m_service.getConnectedDevices();
        }

        for (int i = 0; i < connected_devices.size(); i++) {
            BluetoothGatt gatt = connected_devices.get(i);
            if (m_service != null && gatt != null) {
                m_service.disconnectDevice(gatt.getDevice().getAddress());
            }
        }
        JSONObject response = new JSONObject();
        response.put(Constants.kResult, Constants.kConfigure);
        sendResponse(response);
    }

    private void getState() throws JSONException
    {
        JSONObject parameters = new JSONObject();
        JSONObject response = new JSONObject();
        String stateString = null;

        if(m_service!=null){
            stateString = Util.centralStateStringFromCentralState(m_service.getServiceState());
        }
        parameters.put(Constants.kState, stateString);

        response.put(Constants.kParams, parameters);
        response.put(Constants.kResult, Constants.kCentralState);

        sendResponse(response);
    }

    private void scanForPeripherals(JSONObject reqObj) throws JSONException
    {
        String requestID = getRequestID(reqObj);
        String requestSessionId = getRequestSessionID(reqObj);
        if(scanDevices!=null) {
            scanDevices = null;
        }
        scanDevices = new JSONObject();

        JSONObject params;
        JSONArray servcieUUIDArray;
        boolean duplicates;

        if (!isPoweredOn()) {
            sendReasonForFailedCall(Constants.kScanForPeripherals, requestID, requestSessionId);
            return;
        }

        // Clear the list of service IDs that are used for filtering scan.
        m_filtered_services.clear();

        if (!reqObj.has(Constants.kParams)) {
            sendInvalidParameters(Constants.kScanForPeripherals, requestID, requestSessionId);
            return;
        }

        params = reqObj.getJSONObject(Constants.kParams);

        duplicates = params.has(Constants.kScanOptionAllowDuplicatesKey) && params.getBoolean(Constants.kScanOptionAllowDuplicatesKey);

        if (params.has(Constants.kServiceUUIDs)) {
            servcieUUIDArray = params.getJSONArray(Constants.kServiceUUIDs);

            for (int i = 0; i < servcieUUIDArray.length(); i++) {
                m_filtered_services.add(servcieUUIDArray.getString(i));
            }
        }

        m_service.startDeviceDiscovery(0, duplicates);
        JSONObject response = new JSONObject();
        response.put(Constants.kResult, Constants.kScanForPeripherals);
        sendResponse(response);
    }

    private void stopScanning(JSONObject reqObj) throws JSONException
    {
        JSONObject response;
        String requestID = getRequestID(reqObj);
        String requestSessionId = getRequestSessionID(reqObj);

        if (!isPoweredOn()) {
            sendReasonForFailedCall(Constants.kStopScanning, requestID, requestSessionId);
            return;
        }

        response = new JSONObject();

        response.put(Constants.kResult, Constants.kStopScanning);
        sendResponse(response);

        m_service.stopDeviceDiscovery();
    }

    private void connectPeripheral(JSONObject reqObj) throws JSONException
    {
        String requestID = getRequestID(reqObj);
        String requestSessionId = getRequestSessionID(reqObj);

        if (!isPoweredOn()) {
            sendReasonForFailedCall(Constants.kConnect, requestID, requestSessionId);
        } else {
            if (reqObj.has(Constants.kParams)) {
                JSONObject parameters = reqObj.getJSONObject(Constants.kParams);

                if (parameters.has(Constants.kPeripheralUUID)) {
                    String address = parameters.getString(Constants.kPeripheralUUID);
                    m_service.connectDevice(address);
                } else {
                    sendInvalidParameters(Constants.kConnect, requestID, requestSessionId);
                }
            } else {
                sendInvalidParameters(Constants.kConnect, requestID, requestSessionId);
            }
        }
    }

    private void disconnectPeripheral(JSONObject reqObj) throws JSONException
    {
        // handle disconnect event
        String requestID = getRequestID(reqObj);
        String requestSessionId = getRequestSessionID(reqObj);

        if (!isPoweredOn()) {
            sendReasonForFailedCall(Constants.kDisconnect, requestID, requestSessionId);
        } else {
            if(reqObj.has(Constants.kParams)) {
                JSONObject jObj = reqObj.getJSONObject(Constants.kParams);

                if(jObj.has(Constants.kPeripheralUUID)) {
                    String address = jObj.getString(Constants.kPeripheralUUID);
                    m_service.disconnectDevice(address);
                } else {
                    sendInvalidParameters(Constants.kDisconnect, requestID, requestSessionId);
                }
            } else {
                sendInvalidParameters(Constants.kDisconnect, requestID, requestSessionId);
            }
        }
    }

    private void getServices(JSONObject reqObj) throws JSONException
    {
        String requestID = getRequestID(reqObj);
        String requestSessionId = getRequestSessionID(reqObj);

        if(reqObj.has(Constants.kParams)) {
            JSONObject jObj = reqObj.getJSONObject(Constants.kParams);

            if(jObj.has(Constants.kPeripheralUUID)) {
                String peripheralAddress = jObj.getString(Constants.kPeripheralUUID);
                m_service.getDeviceServices(peripheralAddress);
            } else {
                sendInvalidParameters(Constants.kGetServices, requestID, requestSessionId);
            }
        } else {
            sendInvalidParameters(Constants.kGetServices, requestID, requestSessionId);
        }
    }

    private void getCharacteristics(JSONObject reqObj) throws JSONException
    {
        String serviceUUIDString;
        JSONObject reqParameters;

        String requestID = getRequestID(reqObj);
        String requestSessionId = getRequestSessionID(reqObj);

        if(reqObj.has(Constants.kParams)){
            reqParameters = reqObj.getJSONObject(Constants.kParams);

            if(reqParameters.has(Constants.kServiceUUID)) {
                serviceUUIDString = Util.ConvertUUID_16bitInto128bit(reqParameters.getString(Constants.kServiceUUID).toUpperCase(Locale.getDefault()));
                m_service.getDeviceAttributes(serviceUUIDString);
            } else {
                sendServiceNotFoundErrorMessage(Constants.kGetCharacteristics, requestID, requestSessionId);
            }
        } else {
            sendInvalidParameters(Constants.kGetCharacteristics, requestID, requestSessionId);
        }
    }

    private void getDescriptors(JSONObject reqObj) throws JSONException
    {
        String requestID = getRequestID(reqObj);
        String requestSessionId = getRequestSessionID(reqObj);

        if (reqObj.has(Constants.kParams)) {
            JSONObject reqparameters = reqObj.getJSONObject(Constants.kParams);

            if (reqparameters.has(Constants.kCharacteristicUUID)) {
                String characteristicsUUIDString = Util.ConvertUUID_16bitInto128bit(reqparameters.getString(Constants.kCharacteristicUUID).toUpperCase(Locale.getDefault()));
                m_service.getDeviceAttributeDescriptors(characteristicsUUIDString);
            } else {
                sendCharacteristicNotFoundErrorMessage(Constants.kGetDescriptors, requestID, requestSessionId);
            }
        } else {
            sendInvalidParameters(Constants.kGetDescriptors, requestID, requestSessionId);
        }
    }

    private void getCharacteristicValue(JSONObject reqObj) throws JSONException
    {
        String requestID = getRequestID(reqObj);
        String requestSessionId = getRequestSessionID(reqObj);

        if(reqObj.has(Constants.kParams)) {
            JSONObject jObj = reqObj.getJSONObject(Constants.kParams);

            if(jObj.has(Constants.kCharacteristicUUID)) {
                String characteristicUUID =  Util.ConvertUUID_16bitInto128bit(jObj.getString(Constants.kCharacteristicUUID).toUpperCase(Locale.getDefault()));
                m_service.getDeviceAttributeValue(characteristicUUID);
            } else {
                sendCharacteristicNotFoundErrorMessage(Constants.kGetCharacteristicValue, requestID, requestSessionId);
            }
        } else {
            sendInvalidParameters(Constants.kGetCharacteristicValue,requestID, requestSessionId);
        }
    }

    private void writeCharacteristicValue(JSONObject reqObj) throws JSONException
    {
        String requestID = getRequestID(reqObj);
        String requestSessionId = getRequestSessionID(reqObj);

        if(reqObj.has(Constants.kParams)) {
            JSONObject jObj = reqObj.getJSONObject(Constants.kParams);

            if(jObj.has(Constants.kCharacteristicUUID) && jObj.has(Constants.kValue)) {
                String writeType = null;
                String characteristicUUID = Util.ConvertUUID_16bitInto128bit(jObj.getString(Constants.kCharacteristicUUID).toUpperCase(Locale.getDefault()));

                int length = jObj.getString(Constants.kValue).length();
                if(length%2 != 0) {
                    sendInvalidLength(Constants.kWriteCharacteristicValue ,requestID, requestSessionId);
                    return;
                }
                byte[] writeData = Util.hexStringToByteArray(jObj.getString(Constants.kValue));

                if(jObj.has(Constants.kWriteType)) {
                    writeType = Util.writeTypeForCharacteristicGiven(jObj.getString(Constants.kWriteType));
                }

                m_service.writeDeviceAttributeValue(characteristicUUID, writeType, writeData);
            } else {
                sendInvalidParameters(Constants.kWriteCharacteristicValue, requestID, requestSessionId);
            }
        } else {
            sendInvalidParameters(Constants.kWriteCharacteristicValue,requestID, requestSessionId);
        }
    }

    private void setValueNotification(JSONObject reqObj) throws JSONException
    {
        m_notifications = !m_notifications;
        String requestID = getRequestID(reqObj);
        String requestSessionId = getRequestSessionID(reqObj);

        if(reqObj.has(Constants.kParams)) {
            JSONObject jObj = reqObj.getJSONObject(Constants.kParams);

            if(jObj.has(Constants.kCharacteristicUUID) && jObj.has(Constants.kIsNotifying)) {
                String characteristicUUID = Util.ConvertUUID_16bitInto128bit(jObj.getString(Constants.kCharacteristicUUID).toUpperCase(Locale.getDefault()));
                String subscribe = jObj.getString(Constants.kIsNotifying);
                Boolean subscribeBOOL = subscribe.equals("true");
                m_isNotifying = subscribeBOOL;
                m_service.getDeviceAttributeNotifications(characteristicUUID, subscribeBOOL);
            } else {
                sendInvalidParameters(Constants.kSetValueNotification, requestID, requestSessionId);
            }
        } else {
            sendInvalidParameters(Constants.kSetValueNotification, requestID, requestSessionId);
        }
    }

    private void getDescriptorValue(JSONObject reqObj) throws JSONException
    {
        String requestID = getRequestID(reqObj);
        String requestSessionId = getRequestSessionID(reqObj);

        if(reqObj.has(Constants.kParams)) {
            JSONObject jObj = reqObj.getJSONObject(Constants.kParams);

            if(jObj.has(Constants.kDescriptorUUID)) {
                characteristicId = jObj.getString(Constants.kCharacteristicUUID).toUpperCase(Locale.getDefault());
                serviceId = jObj.getString(Constants.kServiceUUID).toUpperCase(Locale.getDefault());

                String descriptorUUID = Util.ConvertUUID_16bitInto128bit(jObj.getString(Constants.kDescriptorUUID).toUpperCase(Locale.getDefault()));
                String characteristicUUID = Util.ConvertUUID_16bitInto128bit(jObj.getString(Constants.kCharacteristicUUID).toUpperCase(Locale.getDefault()));
                String serviceUUID = Util.ConvertUUID_16bitInto128bit(jObj.getString(Constants.kServiceUUID).toUpperCase(Locale.getDefault()));

                m_service.getDeviceAttributeDescriptorValue(descriptorUUID, characteristicUUID, serviceUUID);
            } else {
                sendDescriptorNotFoundErrorMessage(Constants.kGetDescriptorValue, requestID, requestSessionId);
            }
        } else {
            sendInvalidParameters(Constants.kGetDescriptorValue, requestID, requestSessionId);
        }
    }

    private void writeDescriptorValue(JSONObject reqObj) throws JSONException
    {
        String requestID = getRequestID(reqObj);
        String requestSessionId = getRequestSessionID(reqObj);

        if(reqObj.has(Constants.kParams)) {
            JSONObject jObj = reqObj.getJSONObject(Constants.kParams);

            if(jObj.has(Constants.kCharacteristicUUID) && jObj.has(Constants.kValue)) {
                String descriptorUUID = Util.ConvertUUID_16bitInto128bit(jObj.getString(Constants.kDescriptorUUID));
                byte[] writeData = Util.hexStringToByteArray(jObj.getString(Constants.kValue));

                m_service.writeDeviceAttributeDescriptorValue(descriptorUUID, writeData);
            } else {
                sendInvalidParameters(Constants.kWriteCharacteristicValue, requestID, requestSessionId);
            }
        } else {
            sendInvalidParameters(Constants.kWriteCharacteristicValue,requestID, requestSessionId);
        }
    }

    private void getPeripheralState(JSONObject reqObj) throws JSONException
    {
        String requestID = getRequestID(reqObj);
        String requestSessionId = getRequestSessionID(reqObj);

        // TODO: rewrte.
        try {
            if(reqObj.has(Constants.kParams)) {
                JSONObject jObj = reqObj.getJSONObject(Constants.kParams);

                if(jObj.has(Constants.kPeripheralUUID)) {
                    String peripheralAddress = jObj.getString(Constants.kPeripheralUUID);
                    BluetoothGatt gatt = Util.peripheralIn(m_service.getConnectedDevices(), peripheralAddress);

                    if (gatt == null) {
                        sendPeripheralNotFoundErrorMessage(Constants.kGetPeripheralState, requestID, requestSessionId);
                    } else {
                        String peripheralState = Util.peripheralStateStringFromPeripheralState(gatt);
                        JSONObject respObj = new JSONObject();
                        respObj.put(Constants.kStateField, peripheralState);
                        respObj.put(Constants.kError, null);
                        respObj.put(Constants.kResult, Constants.kGetPeripheralState);

                        sendResponse(respObj);
                    }
                } else {
                    sendPeripheralNotFoundErrorMessage(Constants.kGetPeripheralState, requestID, requestSessionId);
                }
            } else {
                sendInvalidParameters(Constants.kGetPeripheralState, requestID, requestSessionId);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void getRSSI(JSONObject reqObj) throws JSONException
    {
        String requestID = getRequestID(reqObj);
        String requestSessionId = getRequestSessionID(reqObj);

        if(reqObj.has(Constants.kParams)) {
            JSONObject jObj = reqObj.getJSONObject(Constants.kParams);

            if(jObj.has(Constants.kPeripheralUUID)) {
                String peripheralAddress = jObj.getString(Constants.kPeripheralUUID);
                m_service.getDeviceSignal(peripheralAddress);
            } else {
                sendPeripheralNotFoundErrorMessage(Constants.kGetRSSI, requestID, requestSessionId);
            }
        } else {
            sendInvalidParameters(Constants.kGetRSSI, requestID, requestSessionId);
        }
    }


    /*
    * Error Handling methods
    * */
    private void sendGatewayDownResponse(String method, String requestId, String requestSessionId) throws JSONException
    {
        JSONObject response = new JSONObject();
        JSONObject errorObj = new JSONObject();

        response.put(Constants.kResult, method);

        if(requestId!=null)
            response.put(Constants.kRequestId, requestId);
        if(requestSessionId!=null)
            response.put(Constants.kRequestSessionId, requestSessionId);
        errorObj.put(Constants.kCode, Constants.kError32603);
        errorObj.put(Constants.kMessageField, "Device is Down, Please restart the app");
        response.put(Constants.kError, errorObj);
        sendResponse(response);
    }

    private void sendReasonForFailedCall(String method, String requestId, String requestSessionId) throws JSONException
    {
        JSONObject response = new JSONObject();
        JSONObject errorObj = new JSONObject();

        response.put(Constants.kResult, method);

        if(requestId!=null)
            response.put(Constants.kRequestId, requestId);
        if(requestSessionId!=null)
            response.put(Constants.kRequestSessionId, requestSessionId);
        errorObj.put(Constants.kCode, Constants.kError32005);
        errorObj.put(Constants.kMessageField, "Bluetooth power is turned off");
        response.put(Constants.kError, errorObj);
        sendResponse(response);
    }

    private void sendPeripheralNotFoundErrorMessage(String method, String requestId, String requestSessionId) throws JSONException
    {
        JSONObject errorObj = new JSONObject();
        JSONObject response = new JSONObject();

        response.put(Constants.kResult, method);
        if(requestId!=null)
            response.put(Constants.kRequestId, requestId);
        if(requestSessionId!=null)
            response.put(Constants.kRequestSessionId, requestSessionId);
        errorObj.put(Constants.kCode, Constants.kError32001);
        errorObj.put(Constants.kMessageField, "Peripheral not found.");
        response.put(Constants.kError, errorObj);

        sendResponse(response);
    }

    private void sendServiceNotFoundErrorMessage(String method, String requestId, String requestSessionId) throws JSONException
    {
        JSONObject errorObj = new JSONObject();
        JSONObject response = new JSONObject();

        response.put(Constants.kResult, method);
        if(requestId!=null)
            response.put(Constants.kRequestId, requestId);
        if(requestSessionId!=null)
            response.put(Constants.kRequestSessionId, requestSessionId);
        errorObj.put(Constants.kCode, Constants.kError32002);
        errorObj.put(Constants.kMessageField, "Service not found.");
        response.put(Constants.kError, errorObj);

        sendResponse(response);
    }

    private void sendCharacteristicNotFoundErrorMessage(String method, String requestId, String requestSessionId) throws JSONException
    {
        JSONObject errorObj = new JSONObject();
        JSONObject response = new JSONObject();

        response.put(Constants.kResult, method);

        if(requestId!=null) {
            response.put(Constants.kRequestId, requestId);
        }
        if(requestSessionId!=null)
            response.put(Constants.kRequestSessionId, requestSessionId);

        errorObj.put(Constants.kCode, Constants.kError32003);
        errorObj.put(Constants.kMessageField, "Characteristic not found.");

        response.put(Constants.kError, errorObj);

        sendResponse(response);
    }

    private void sendDescriptorNotFoundErrorMessage(String method, String requestId, String requestSessionId) throws JSONException
    {
        JSONObject errorObj = new JSONObject();
        JSONObject response = new JSONObject();

        response.put(Constants.kResult, method);
        if(requestId!=null)
            response.put(Constants.kRequestId, requestId);
        if(requestSessionId!=null)
            response.put(Constants.kRequestSessionId, requestSessionId);
        errorObj.put(Constants.kCode, Constants.kError32001);
        errorObj.put(Constants.kMessageField, "Descriptor not found.");
        response.put(Constants.kError, errorObj);

        sendResponse(response);

    }

    private void sendInvalidRequest() throws JSONException
    {
        JSONObject errorObj = new JSONObject();
        errorObj.put(Constants.kCode, Constants.kInvalidRequest);
        JSONObject jsonData = new JSONObject();
        jsonData.put(Constants.kError, errorObj);
        jsonData.put(Constants.kResult, "InvalidRequest");
        sendResponse(jsonData);
    }

    private void sendInvalidParameters(String method, String requestId, String requestSessionId) throws JSONException
    {
        JSONObject errorObj = new JSONObject();
        JSONObject response = new JSONObject();
        response.put(Constants.kResult, method);
        if(requestId!=null)
            response.put(Constants.kRequestId, requestId);
        if(requestSessionId!=null)
            response.put(Constants.kRequestSessionId, requestSessionId);
        errorObj.put(Constants.kCode, Constants.kInvalidParams);
        errorObj.put(Constants.kMessageField, "Invalid parameter.");
        response.put(Constants.kError, errorObj);

        sendResponse(response);
    }

    private void sendTimeoutError() throws JSONException
    {
        JSONObject errorObj = new JSONObject();
        JSONObject response = new JSONObject();

        response.put(Constants.kResult, "Timedout");

        errorObj.put(Constants.kCode, "Timedout error");

        response.put(Constants.kError, errorObj);

        sendResponse(response);
    }

    private String getRequestID (JSONObject request) throws JSONException
    {
        String requestID = "";

        if (request != null) {
            try {
                if (request.has(Constants.kRequestId))
                    requestID = request.getString(Constants.kRequestId);
            } catch (JSONException ex) {
                ex.printStackTrace();
            }
        }

        return requestID;
    }

    private String getRequestSessionID (JSONObject request) throws JSONException
    {
        String requestSessionID = "";

        if (request != null) {
            try {
                if (request.has(Constants.kRequestSessionId))
                    requestSessionID = request.getString(Constants.kRequestSessionId);
            } catch (JSONException ex) {
                ex.printStackTrace();
            }
        }

        return requestSessionID;
    }

    private void sendInvalidLength(String method, String requestId, String requestSessionId) throws JSONException{

        JSONObject errorObj = new JSONObject();
        JSONObject response = new JSONObject();
        response.put(Constants.kResult, method);
        if(requestId!=null)
            response.put(Constants.kRequestId, requestId);
        if(requestSessionId!=null)
            response.put(Constants.kRequestSessionId, requestSessionId);

        errorObj.put(Constants.kCode, Constants.kError32603);
        errorObj.put(Constants.kMessageField, "Invalid length.");
        response.put(Constants.kError, errorObj);

        sendResponse(response);
    }

    private void sendFailedToServeCharacteristicErrorMessage(String method, String requestId, String requestSessionId, String errorMessage) throws JSONException{

        JSONObject errorObj = new JSONObject();
        JSONObject response = new JSONObject();
        response.put(Constants.kResult, method);
        if(requestId!=null)
            response.put(Constants.kRequestId, requestId);
        if(requestSessionId!=null)
            response.put(Constants.kRequestSessionId, requestSessionId);

        errorObj.put(Constants.kCode, Constants.kError32603);
        errorObj.put(Constants.kMessageField, errorMessage);
        response.put(Constants.kError, errorObj);

        sendResponse(response);
    }

    private void sendFailedToServeDescriptorErrorMessage(String method, String requestId, String requestSessionId, String errorMessage) throws JSONException{

        JSONObject errorObj = new JSONObject();
        JSONObject response = new JSONObject();
        response.put(Constants.kResult, method);
        if(requestId!=null)
            response.put(Constants.kRequestId, requestId);
        if(requestSessionId!=null)
            response.put(Constants.kRequestSessionId, requestSessionId);

        errorObj.put(Constants.kCode, Constants.kError32603);
        errorObj.put(Constants.kMessageField, errorMessage);
        response.put(Constants.kError, errorObj);

        sendResponse(response);
    }

//    final Thread m_time_out_error_thread = new Thread(new Runnable() {
//        @Override
//        public void run() {
//            try {
//                sendTimeoutError();
//            } catch (JSONException e) {
//                throw new RuntimeException(e);
//            }
//        }
//    });


    @Override
    public void onDeviceFound(String deviceIdentifier, String deviceName, int deviceSignal, List<String> serviceUUIDs, ScanRecord record, byte[] deviceData)
    {
        if(m_filtered_services.size()>0) {
            int count = 0;

            for (int i = 0; i < serviceUUIDs.size(); i++) {
                for (int j = 0; j < m_filtered_services.size(); j++) {
                    if (serviceUUIDs.get(i).contains(m_filtered_services.get(j))) {
                        count++;
                    }
                }
            }

            if (count > 0) {
                // call response method here
                sendScanResponse(deviceIdentifier, deviceName, deviceSignal, record, deviceData);
            }
        } else {
            try {

                if (scanDevices == null) {
                    scanDevices = new JSONObject();
                }

                if (scanDevices.length() > 0 && scanDevices.has(deviceIdentifier)) {

                    JSONObject tempPeripheral = scanDevices.getJSONObject(deviceIdentifier);

                    if (tempPeripheral.getString(Constants.kAdvertisementDataKey).equals(Util.byteArrayToHex(deviceData))) {

                        int old_rssi = tempPeripheral.getInt(Constants.kRSSIkey);
                        int diff_rssi;

                        if (old_rssi > deviceSignal) {
                            diff_rssi = old_rssi - deviceSignal;
                        } else {
                            diff_rssi = deviceSignal - old_rssi;
                        }

                        if (diff_rssi < 8) {
                            return;
                        }
                    }
                }

                JSONObject tempPeripheral = new JSONObject();
                tempPeripheral.put(Constants.kAdvertisementDataKey, Util.byteArrayToHex(deviceData));
                tempPeripheral.put(Constants.kRSSIkey, deviceSignal);

                scanDevices.put(deviceIdentifier, tempPeripheral);

            } catch (JSONException je){
                je.printStackTrace();
            }

            sendScanResponse(deviceIdentifier, deviceName, deviceSignal, record, deviceData);
        }
    }

    @SuppressLint("NewApi")
    public void sendScanResponse(String deviceIdentifier, String deviceName, int deviceSignal, ScanRecord scanRecord, byte[] deviceData) {
        try {
            JSONObject response = new JSONObject();
            JSONObject parameters = new JSONObject();
            JSONObject adParameters = new JSONObject();

            if(deviceIdentifier != null) {
                String btAddr = deviceIdentifier.replaceAll(":", "-");
                parameters.put(Constants.kPeripheralBtAddress, btAddr);
                parameters.put(Constants.kPeripheralUUID, deviceIdentifier);
            }

            parameters.put(Constants.kRSSIkey, deviceSignal);

            if(deviceName != null) {
                parameters.put(Constants.kPeripheralName, deviceName);
            } else {
                parameters.put(Constants.kPeripheralName, deviceIdentifier);
            }

            if(scanRecord != null){
                //Parse ADV FLAGS for discoverable mode and capability of the device.
                int advFlags = scanRecord.getAdvertiseFlags();
                Log.d("ADV FLAGS: ", Integer.toString(advFlags) );
                if(advFlags > -1){
                    if ((advFlags & 2) == 2) {
                        Log.d("discoverable mode :", "TRUE");
                        parameters.put(Constants.kCBAdvertisementDataIsConnectable, true);
                    }else{
                        Log.d("discoverable mode :", "FALSE");
                        parameters.put(Constants.kCBAdvertisementDataIsConnectable, false);
                    }
                }

                //Parse Service UUIDs
                List<ParcelUuid> sUUIDs = scanRecord.getServiceUuids();
                if(sUUIDs != null && sUUIDs.size() > 0) {
                    Log.d("sUUIDs: ", sUUIDs.toString());
                    JSONArray serviceUUIDs = Util.arrayOfServiceUUIDStringsFrom(sUUIDs);
                    parameters.put(Constants.kCBAdvertisementDataServiceUUIDsKey, serviceUUIDs);
                }

                //Parse Manufacturer Data
                SparseArray<byte[]> mfrData = scanRecord.getManufacturerSpecificData();
                if(mfrData != null && mfrData.size() > 0) {
                    JSONObject mfrObj = new JSONObject();
                    for (int i = 0; i < mfrData.size(); i++) {
                        int intKey = mfrData.keyAt(i);
                        String key = Integer.toHexString(intKey);
                        String mfrKey = ("0000" + key).substring(key.length());

                        byte[] value = mfrData.valueAt(i);
                        String mfrValue = Util.byteArrayToHex(value);

                        mfrObj.put(mfrKey, mfrValue);
                        Log.d("mfrData  : ", mfrKey + ":" + mfrValue);
                    }
                    parameters.put(Constants.kCBAdvertisementDataManufacturerDataKey, mfrObj);
                }

                //Parse Service Data
                Map<ParcelUuid, byte[]> svcData = scanRecord.getServiceData();
                if(svcData != null && svcData.size() > 0) {
                    JSONObject svcObj = new JSONObject();

                    for (ParcelUuid key : svcData.keySet()) {
                        String sUUIDString = key.toString();
                        String serviceUUIDString = Util.ConvertUUID_128bitInto16bit(sUUIDString.toUpperCase(Locale.getDefault()));

                        byte[] value = svcData.get(key);
                        String svcValue = Util.byteArrayToHex(value);

                        svcObj.put(serviceUUIDString, svcValue);
                        Log.d("svcData  : ", serviceUUIDString + ":" + svcValue);
                    }
                    parameters.put(Constants.kCBAdvertisementDataServiceDataKey, svcObj);
                }

                //Parse TxPowerLevel value
                int txPower = scanRecord.getTxPowerLevel();
                Log.d("txPower: ", Integer.toString(txPower));
                if(txPower != Integer.MIN_VALUE) {
                    parameters.put(Constants.kCBAdvertisementDataTxPowerLevel, txPower);
                }else{
                    Log.d("txPower: NOT SET ",Integer.toString(txPower));
                }

                //Parse Device Name
                //String dName = scanRecord.getDeviceName();
                //Log.d("Device Name: ", dName);
            }

            adParameters.put(Constants.kRawAdvertisementData, Util.byteArrayToHex(deviceData));
            parameters.put(Constants.kAdvertisementDataKey, adParameters);

            response.put(Constants.kResult, Constants.kScanForPeripherals);
            response.put(Constants.kParams, parameters);

            sendResponse(response, true);
        } catch (JSONException je) {
            je.printStackTrace();
        }
    }


    @Override
    public void onDeviceConnection(String deviceName, String deviceIdentifier)
    {
        try {
            if(deviceIdentifier!=null && !deviceIdentifier.isEmpty()){

                m_service.getDeviceServices(deviceIdentifier);
            }
            else {
                sendPeripheralNotFoundErrorMessage(Constants.kConnect, getRequestID(m_current_request), getRequestSessionID(m_current_request));
            }
        } catch (JSONException je) {
            je.printStackTrace();
        }
    }

    @Override
    public void onDeviceDisconnection(String deviceName, String deviceIdentifier)
    {
        JSONObject parameters = new JSONObject();
        JSONObject response = new JSONObject();

        // send response to client
        try {
            // get the json data to send client
            parameters.put(Constants.kPeripheralUUID, deviceIdentifier);

            if(deviceName != null) {
                parameters.put(Constants.kPeripheralName, deviceName);
            } else {
                parameters.put(Constants.kPeripheralName, deviceIdentifier);
            }

            response.put(Constants.kResult, Constants.kDisconnect);
            response.put(Constants.kParams, parameters);

            sendResponse(response);
        } catch (JSONException je) {
            je.printStackTrace();
        }
    }

    @Override
    public void onDeviceConnectionFailure(String deviceName, String deviceIdentifier, int status)
    {
        JSONObject response = new JSONObject();
        JSONObject errorCode = new JSONObject();
        JSONObject parameters = new JSONObject();

        try {
            errorCode.put(Constants.kCode, Constants.kError32603);
            errorCode.put(Constants.kMessageField, "failed to connect");

            parameters.put(Constants.kPeripheralUUID, deviceIdentifier);
            response.put(Constants.kParams, parameters);
            response.put(Constants.kResult, Constants.kConnect);
            response.put(Constants.kError, errorCode);

            sendResponse(response);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDeviceUnexpectedlyDisconnection(String deviceName, String deviceIdentifier, int status) {
        JSONObject response = new JSONObject();
        JSONObject errorCode = new JSONObject();
        JSONObject parameters = new JSONObject();

        try {
            errorCode.put(Constants.kCode, Constants.kError32603);
            errorCode.put(Constants.kMessageField, "Unexpectedly disconnected");

            parameters.put(Constants.kPeripheralUUID, deviceIdentifier);
            if(deviceName != null) {
                parameters.put(Constants.kPeripheralName, deviceName);
            } else {
                parameters.put(Constants.kPeripheralName, deviceIdentifier);
            }

            response.put(Constants.kParams, parameters);
            response.put(Constants.kResult, Constants.kDisconnect);
            response.put(Constants.kMessageField, "Unexpectedly disconnected");

            Log.e("onDeviceUnexpectedly()", ":" + deviceName + "-" + deviceIdentifier + "-" + status);
            sendResponse(response, true);
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onDeviceServices(String deviceIdentifier, List services, int status) {
        try {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                List<JSONObject> json_services = Util.listOfJsonServicesFrom(services);

                JSONObject response = new JSONObject();
                JSONObject parameters = new JSONObject();

                if( (m_current_request != null && m_current_request.has(Constants.kMethod)) && m_current_request.getString(Constants.kMethod).equals(Constants.kGetServices)) {
                    try {
                        parameters.put(Constants.kPeripheralUUID, deviceIdentifier);
                        parameters.put(Constants.kServices, new JSONArray(json_services));

                        response.put(Constants.kResult, Constants.kGetServices);
                        response.put(Constants.kParams, parameters);

                        sendResponse(response);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                else {
                    try {
                        JSONObject service_db = new JSONObject();

                        if (json_services.size() > 0) {
                            ListIterator<JSONObject> iterator = null;
                            iterator = json_services.listIterator();

                            while (iterator.hasNext()) {
                                JSONObject service = iterator.next();
                                m_service.getDeviceAttributes(service.getString(Constants.kServiceUUID));
                                JSONObject jObjCharacteristics = new JSONObject();
                                jObjCharacteristics.put(Constants.kServiceUUID, service.getString(Constants.kServiceUUID));
                                jObjCharacteristics.put(Constants.kCharacteristics, charc_json);
                                service_db.put(service.getString(Constants.kServiceUUID),jObjCharacteristics);
                            }
                        }
                        parameters.put(Constants.kPeripheralUUID, deviceIdentifier);
                        parameters.put(Constants.kServices, service_db);

                        response.put(Constants.kResult, Constants.kConnect);
                        response.put(Constants.kParams, parameters);
                        sendResponse(response);
                    }
                    catch (JSONException je) {
                        je.printStackTrace();
                    }
                }
            } else {
                try{
                    JSONObject errorCode = new JSONObject();
                    JSONObject parameters = new JSONObject();
                    JSONObject response = new JSONObject();

                    errorCode.put(Constants.kCode, Constants.kError32603);
                    errorCode.put(Constants.kMessageField, "Failed to get services");
                    parameters.put(Constants.kPeripheralUUID, deviceIdentifier);
                    response.put(Constants.kParams, parameters);
                    response.put(Constants.kResult, Constants.kGetServices);
                    response.put(Constants.kError, errorCode);
                    sendResponse(response);

                } catch (JSONException je) {
                    je.printStackTrace();
                }
            }

        } catch (JSONException je) {
            je.printStackTrace();
        }
    }

    @Override
    public void onDeviceAttributes(String deviceIdentifier, String serviceIdentifier, List characteristics)
    {
        try {
            List<JSONObject> listOfCharacteristics = Util.listOfJsonCharacteristicsFrom(characteristics);

            if( (m_current_request != null && m_current_request.has(Constants.kMethod)) && m_current_request.getString(Constants.kMethod).equals(Constants.kGetCharacteristics)) {
                JSONObject parameters = new JSONObject();
                JSONObject response = new JSONObject();
                String serviceUUIDString = Util.ConvertUUID_128bitInto16bit(serviceIdentifier.toUpperCase(Locale.getDefault()));
                try {
                    parameters.put(Constants.kPeripheralUUID, deviceIdentifier);
                    parameters.put(Constants.kServiceUUID, serviceUUIDString);
                    parameters.put(Constants.kCharacteristics, new JSONArray(listOfCharacteristics));
                    response.put(Constants.kResult, Constants.kGetCharacteristics);
                    response.put(Constants.kParams, parameters);

                    sendResponse(response);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
            else {
                charc_json = new JSONObject();
                if(listOfCharacteristics.size()>0) {
                    ListIterator<JSONObject> iterator = null;
                    iterator = listOfCharacteristics.listIterator();

                    while (iterator.hasNext()) {
                        JSONObject characteristic = iterator.next();
                        m_service.getDeviceAttributeDescriptors(characteristic.getString(Constants.kCharacteristicUUID));
                        characteristic.put(Constants.kDescriptors, desc_json);
                        charc_json.put(characteristic.getString(Constants.kCharacteristicUUID),characteristic);
                    }
                }
            }
        } catch(JSONException je) {
            je.printStackTrace();
        }
    }

    @Override
    public void onDevcieAttributeRead(String deviceIdentifier, String serviceIdentifier, String attribIdentifier, byte[] attribValue, int status)
    {
        String characteristicUUIDString = Util.ConvertUUID_128bitInto16bit(attribIdentifier.toUpperCase(Locale.getDefault()));
        String serviceUUIDString = Util.ConvertUUID_128bitInto16bit(serviceIdentifier.toUpperCase(Locale.getDefault()));
        if(status == BluetoothGatt.GATT_SUCCESS) {
            JSONObject response = new JSONObject();
            JSONObject parameters = new JSONObject();

            String characteristicValueString = Util.byteArrayToHex(attribValue);

            try {
                parameters.put(Constants.kCharacteristicUUID, characteristicUUIDString);
                parameters.put(Constants.kPeripheralUUID, deviceIdentifier);
                parameters.put(Constants.kServiceUUID, serviceUUIDString);
                parameters.put(Constants.kValue, characteristicValueString);

                response.put(Constants.kResult, Constants.kGetCharacteristicValue);
                response.put(Constants.kParams, parameters);

                sendResponse(response);
            } catch (JSONException je) {
                je.printStackTrace();
            }
        } else if(status == BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION || status == BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION) {
            Log.v("oncharacteristic read","GATT insufficient");

            try {
                JSONObject response = new JSONObject();
                JSONObject parameters = new JSONObject();
                JSONObject errorCode = new JSONObject();

                errorCode.put(Constants.kCode, Constants.kUnauthorized);
                errorCode.put(Constants.kMessageField, "authorization failed");

                parameters.put(Constants.kCharacteristicUUID, characteristicUUIDString);

                response.put(Constants.kResult, Constants.kGetCharacteristicValue);
                response.put(Constants.kParams, parameters);
                response.put(Constants.kError, errorCode);

                sendResponse(response);
            } catch (JSONException je) {
                je.printStackTrace();
            }
        } else {
            try {
                JSONObject response = new JSONObject();
                JSONObject parameters = new JSONObject();
                JSONObject errorCode = new JSONObject();

                errorCode.put(Constants.kCode, Constants.kError32603);
                errorCode.put(Constants.kMessageField, "Read data failed");

                parameters.put(Constants.kCharacteristicUUID, characteristicUUIDString);
                response.put(Constants.kResult, Constants.kGetCharacteristicValue);

                response.put(Constants.kParams, parameters);
                response.put(Constants.kError, errorCode);

                sendResponse(response);
            } catch (JSONException je) {
                je.printStackTrace();
            }
        }
    }

    @Override
    public void onDeviceAttributeWrite(String deviceIdentifier, String serviceIdentifier, String attribIdentifier, int status)
    {
        JSONObject parameters = new JSONObject();
        JSONObject response = new JSONObject();
        String serviceUUIDString = Util.ConvertUUID_128bitInto16bit(serviceIdentifier.toUpperCase(Locale.getDefault()));
        String characteristicUUIDString = Util.ConvertUUID_128bitInto16bit(attribIdentifier.toUpperCase(Locale.getDefault()));

        if (status == BluetoothGatt.GATT_SUCCESS) {
            try {
                parameters.put(Constants.kCharacteristicUUID, characteristicUUIDString);
                parameters.put(Constants.kServiceUUID, serviceUUIDString);
                parameters.put(Constants.kPeripheralUUID, deviceIdentifier);

                response.put(Constants.kResult, Constants.kWriteCharacteristicValue);
                response.put(Constants.kParams, parameters);

                sendResponse(response);
            } catch (JSONException je) {
                je.printStackTrace();
            }
        } else {
            // send error message when write characteristic not supported for
            // specified data
            try {
                JSONObject errorCode = new JSONObject();

                parameters.put(Constants.kCharacteristicUUID, characteristicUUIDString);
                parameters.put(Constants.kServiceUUID, serviceUUIDString);
                parameters.put(Constants.kPeripheralUUID, deviceIdentifier);

                errorCode.put(Constants.kCode, Constants.kError32603);
                errorCode.put(Constants.kMessageField, "Write data failed");

                response.put(Constants.kResult, Constants.kWriteCharacteristicValue);
                response.put(Constants.kParams, parameters);
                response.put(Constants.kError, errorCode);

                sendResponse(response);
            } catch (JSONException je) {
                je.printStackTrace();
            }
        }
    }

    @Override
    public void onDeviceAttributeChanged(String deviceIdentifier, String serviceIdentifier, String attribIdentifier, byte[] attribValue, int status)
    {
        JSONObject response = new JSONObject();
        JSONObject parameters = new JSONObject();

        String characteristicUUIDString = Util.ConvertUUID_128bitInto16bit(attribIdentifier.toUpperCase(Locale.getDefault()));
        String serviceUUIDString = Util.ConvertUUID_128bitInto16bit(serviceIdentifier.toUpperCase(Locale.getDefault()));
        String characteristicValueString = Util.byteArrayToHex(attribValue);

        if (status == BluetoothGatt.GATT_SUCCESS) {
            try {
                parameters.put(Constants.kCharacteristicUUID, characteristicUUIDString);
                parameters.put(Constants.kPeripheralUUID, deviceIdentifier);
                parameters.put(Constants.kServiceUUID, serviceUUIDString);
                parameters.put(Constants.kIsNotifying, m_isNotifying);
                parameters.put(Constants.kValue, characteristicValueString);

                response.put(Constants.kResult, Constants.kSetValueNotification);
                response.put(Constants.kParams, parameters);

                sendResponse(response, true);
            } catch (JSONException je) {
                je.printStackTrace();
            }
        } else {
            // error occur when we set notification for a characteristic
            try {
                JSONObject errorCode = new JSONObject();

                parameters.put(Constants.kCharacteristicUUID, characteristicUUIDString);
                parameters.put(Constants.kServiceUUID, serviceUUIDString);
                parameters.put(Constants.kPeripheralUUID, deviceIdentifier);

                errorCode.put(Constants.kCode, Constants.kError32603);
                errorCode.put(Constants.kMessageField, "");

                response.put(Constants.kResult, Constants.kSetValueNotification);
                response.put(Constants.kParams, parameters);
                response.put(Constants.kError, errorCode);

                sendResponse(response);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

    }

    @Override
    public void onDeviceAttributeDescriptors(String deviceIdentifier, String serviceIdentifier, String attribIdentifier, List attribDescriptors)
    {
        try {
            List<JSONObject> descriptorArray = Util.listOfJsonDescriptorsFrom(attribDescriptors);

            if( (m_current_request != null && m_current_request.has(Constants.kMethod)) && m_current_request.getString(Constants.kMethod).equals(Constants.kGetDescriptors)) {

                JSONObject parameters = new JSONObject();
                JSONObject response = new JSONObject();
                String characteristicUUIDString = Util.ConvertUUID_128bitInto16bit(attribIdentifier.toUpperCase(Locale.getDefault()));
                String serviceUUIDString = Util.ConvertUUID_128bitInto16bit(serviceIdentifier.toUpperCase(Locale.getDefault()));

                try {
                    parameters.put(Constants.kCharacteristicUUID, characteristicUUIDString);
                    parameters.put(Constants.kPeripheralUUID, deviceIdentifier);
                    parameters.put(Constants.kServiceUUID, serviceUUIDString);
                    parameters.put(Constants.kDescriptors, new JSONArray(descriptorArray));

                    response.put(Constants.kResult, Constants.kGetDescriptors);
                    response.put(Constants.kParams, parameters);

                    sendResponse(response);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
            else {
                desc_json = new JSONArray();
                if(descriptorArray.size()>0) {
                    ListIterator<JSONObject> listIterator = null;
                    listIterator = descriptorArray.listIterator();

                    while (listIterator.hasNext()) {
                        JSONObject descriptor = listIterator.next();
                        String descriptorUUID = descriptor.getString(Constants.kDescriptorUUID);
                        JSONObject desc = new JSONObject();
                        desc.put(Constants.kDescriptorUUID,descriptorUUID);
                        desc_json.put(desc);
                    }
                }
            }
        }
        catch (JSONException je) {
            je.printStackTrace();
        }
    }

    @Override
    public void onDeviceAttributeDescriptorRead(String deviceIdentifier, String serviceIdentifier, String attribIdentifier, String attribDescriptorIdentifier, byte[] attributeDescriptorValue, int status)
    {
        JSONObject response = new JSONObject();
        JSONObject parameters = new JSONObject();

        String descriptorUUIDString = Util.ConvertUUID_128bitInto16bit(attribDescriptorIdentifier.toUpperCase(Locale.getDefault()));
        String characteristicUUIDString = Util.ConvertUUID_128bitInto16bit(attribIdentifier.toUpperCase(Locale.getDefault()));
        String serviceUUIDString = Util.ConvertUUID_128bitInto16bit(serviceIdentifier.toUpperCase(Locale.getDefault()));

        if (status == BluetoothGatt.GATT_SUCCESS) {
            try {
                //String desc_value = new String(attributeDescriptorValue,"UTF-8");
                Log.d("Descriptor Value","in Hex- " + Util.byteArrayToHex(attributeDescriptorValue));

                parameters.put(Constants.kPeripheralUUID, deviceIdentifier);
                parameters.put(Constants.kServiceUUID, serviceUUIDString);
                parameters.put(Constants.kCharacteristicUUID, characteristicUUIDString);
                parameters.put(Constants.kDescriptorUUID, descriptorUUIDString);
                parameters.put(Constants.kValue, Util.byteArrayToHex(attributeDescriptorValue));

                response.put(Constants.kResult,Constants.kGetDescriptorValue);
                response.put(Constants.kParams, parameters);

                sendResponse(response);
            } catch (JSONException e) {
                e.printStackTrace();
            }

        } else {

            try {
                JSONObject errorObj = new JSONObject();

                errorObj.put(Constants.kCode, Constants.kError32603);
                errorObj.put(Constants.kMessageField, "Descriptor read failed");

                parameters.put(Constants.kDescriptorUUID, descriptorUUIDString);

                response.put(Constants.kResult, Constants.kGetDescriptorValue);
                response.put(Constants.kError, errorObj);
                response.put(Constants.kParams, parameters);

                sendResponse(response);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onDeviceAttributeDescriptoWrite(String deviceIdentifier, String serviceIdentifier, String attribIdentifier, String attribDescriptorIdentifier, int status)
    {
        try {
            JSONObject response = new JSONObject();
            JSONObject parameters = new JSONObject();

            String descriptorUUIDString = Util.ConvertUUID_128bitInto16bit(attribDescriptorIdentifier.toUpperCase(Locale.getDefault()));
            String characteristicUUIDString = Util.ConvertUUID_128bitInto16bit(attribIdentifier.toUpperCase(Locale.getDefault()));
            String serviceUUIDString = Util.ConvertUUID_128bitInto16bit(serviceIdentifier.toUpperCase(Locale.getDefault()));

            String method = m_current_request.getString(Constants.kMethod);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (method.equals(Constants.kSetValueNotification)) {
                    parameters.put(Constants.kIsNotifying, m_isNotifying);
                    parameters.put(Constants.kCharacteristicUUID, characteristicUUIDString);
                    parameters.put(Constants.kPeripheralUUID, deviceIdentifier);
                    parameters.put(Constants.kServiceUUID, serviceUUIDString);

                    response.put(Constants.kResult, Constants.kSetValueNotification);
                    response.put(Constants.kParams, parameters);

                    sendResponse(response);
                } else if (method.equals(Constants.kWriteDescriptorValue)) {
                    parameters.put(Constants.kDescriptorUUID, descriptorUUIDString);
                    parameters.put(Constants.kCharacteristicUUID, characteristicUUIDString);
                    parameters.put(Constants.kServiceUUID, serviceUUIDString);
                    parameters.put(Constants.kPeripheralUUID, deviceIdentifier);

                    response.put(Constants.kResult, Constants.kWriteDescriptorValue);
                    response.put(Constants.kParams, parameters);

                    sendResponse(response);
                }
            } else {
                sendReasonForFailedCall(method, getRequestID(m_current_request), getRequestSessionID(m_current_request));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDeviceSignal(String deviceIdentifier, String deviceName, int signal, int status)
    {
        JSONObject response = new JSONObject();
        JSONObject parameters = new JSONObject();

        if (status == BluetoothGatt.GATT_SUCCESS) {
            try {
                response.put(Constants.kResult, Constants.kGetRSSI);
                parameters.put(Constants.kPeripheralUUID, deviceIdentifier);

                if(deviceName != null) {
                    parameters.put(Constants.kPeripheralName, deviceName);
                } else {
                    parameters.put(Constants.kPeripheralName, deviceIdentifier);
                }

                parameters.put(Constants.kRSSIkey, signal);
                response.put(Constants.kParams, parameters);
                sendResponse(response);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else {
            try {
                JSONObject errorObj = new JSONObject();

                errorObj.put(Constants.kCode, Constants.kError32603);
                errorObj.put(Constants.kMessageField, "");

                parameters.put(Constants.kPeripheralUUID, deviceIdentifier);

                if (deviceName != null) {
                    parameters.put(Constants.kPeripheralName, deviceName);
                } else {
                    parameters.put(Constants.kPeripheralName, deviceIdentifier);
                }

                response.put(Constants.kResult, Constants.kGetRSSI);
                response.put(Constants.kError, errorObj);
                response.put(Constants.kParams, parameters);

                sendResponse(response);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void sendResponseForWriteTypeNoReponse()
    {
        try {
            JSONObject parameters = new JSONObject();
            JSONObject response = new JSONObject();

            if(m_current_request.has(Constants.kPeripheralUUID) && m_current_request.has(Constants.kServiceUUID) && m_current_request.has(Constants.kCharacteristicUUID)) {
                String deviceUUIDString = m_current_request.getString(Constants.kPeripheralUUID);
                String serviceUUIDString = m_current_request.getString(Constants.kServiceUUID);
                String characteristicUUIDString = m_current_request.getString(Constants.kCharacteristicUUID);

                parameters.put(Constants.kCharacteristicUUID, characteristicUUIDString);
                parameters.put(Constants.kServiceUUID, serviceUUIDString);
                parameters.put(Constants.kPeripheralUUID, deviceUUIDString);

                response.put(Constants.kResult, Constants.kWriteCharacteristicValue);
                response.put(Constants.kParams, parameters);
                sendResponse(response);
            }
        }catch (JSONException ex){
            ex.printStackTrace();
        }
    }

    @Override
    public void noConnectedDevices()
    {
        try {
            String method = this.m_current_request.getString(Constants.kMethod);
            String requestId = getRequestID(this.m_current_request);
            String requestSessionID = getRequestSessionID(this.m_current_request);
            sendPeripheralNotFoundErrorMessage(method, requestId, requestSessionID);
        }catch (JSONException ex){
            ex.printStackTrace();
        }
    }

    @Override
    public void onError(Enum error)
    {
        try {
            String requestId = getRequestID(this.m_current_request);
            String requestSessionID = getRequestSessionID(this.m_current_request);
            String method = this.m_current_request.getString(Constants.kMethod);

            if(error.equals(InterfaceService.Error.DEVICE_NOT_FOUND)){
                sendPeripheralNotFoundErrorMessage(method, requestId, requestSessionID);
            } else if(error.equals(InterfaceService.Error.DEVICE_SERVICE_NOT_FOUND)) {
                sendServiceNotFoundErrorMessage(method, requestId, requestSessionID);
            } else if(error.equals(InterfaceService.Error.DEVICE_ATTRIBUTES_NOT_FOUND)) {
                sendCharacteristicNotFoundErrorMessage(method, requestId, requestSessionID);
            } else if(error.equals(InterfaceService.Error.ATTRIBUTE_DESCRIPTOR_NOT_FOUND)) {
                sendDescriptorNotFoundErrorMessage(method, requestId, requestSessionID);
            } else if(error.equals(InterfaceService.Error.ATTRIBUTE_READ_FAILED)) {
                sendFailedToServeCharacteristicErrorMessage(method, requestId, requestSessionID, "Failed to Read the Characteristic value");
            } else if(error.equals(InterfaceService.Error.ATTRIBUTE_WRITE_FAILED)) {
                sendFailedToServeCharacteristicErrorMessage(method, requestId, requestSessionID, "Failed to Write the value to the Characteristic");
            } else if(error.equals(InterfaceService.Error.ATTRIBUTE_NOTIFICATION_FAILED)) {
                sendFailedToServeCharacteristicErrorMessage(method, requestId, requestSessionID, "Failed to Set notifications for Characteristic");
            } else if(error.equals(InterfaceService.Error.ATTRIBUTE_DESCRIPTOR_READ_FAILED)) {
                sendFailedToServeDescriptorErrorMessage(method, requestId, requestSessionID, "Failed to Read the descriptor value");
            } else if(error.equals(InterfaceService.Error.ATTRIBUTE_DESCRIPTOR_WRITE_FAILED)) {
                sendFailedToServeDescriptorErrorMessage(method, requestId, requestSessionID, "Failed to Write the value to the descriptor");
            } else if(error.equals(InterfaceService.Error.PERMISSOIN_DENIED)) {

            } else if(error.equals(InterfaceService.Error.CONNECTION_FAILED)) {

            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
