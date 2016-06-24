package org.gatt_ip;

import android.bluetooth.BluetoothGatt;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import org.gatt_ip.util.Util;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

public class GATTIP implements ServiceConnection, DeviceEventListener
{
    private static final String TAG = GATTIP.class.getName();
    private static final int MAX_NUMBER_OF_REQUESTS = 30;

    private static boolean isRequestExist = true;

    private final ScheduledExecutorService worker = Executors.newScheduledThreadPool(1);

    private Context m_context;

    private GATTIPListener m_listener;

    private LinkedBlockingQueue<JSONObject> m_message_queue;

    private BluetoothLEService m_service;

    private List<String> m_filtered_services;

    private boolean m_notifications;

    private JSONObject m_current_request;

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

                    while (!isRequestExist) {
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
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

    public boolean isPoweredOn()
    {
        return m_service.getServiceState() == InterfaceService.ServiceState.SERVICE_STATE_ACTIVE;
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

            if (requestID != null) {
                jsonData.put(Constants.kRequestId, requestID);
            }

            isRequestExist = true;
        }
        Log.v("response string", jsonData.toString());
        m_listener.response(jsonData.toString());
    }

    // sending response to client for requested command
    private void sendResponse(JSONObject jsonData) throws JSONException
    {
        sendResponse(jsonData, false);
    }

    private void processRequest(JSONObject request) throws JSONException
    {
        Log.d(TAG, "Processing GATT-IP request.");
        String method = null;
        isRequestExist = false;

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

            if(requestID != null) {
                jsonData.put(Constants.kRequestId, requestID);
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
        List<BluetoothGatt> connected_devices = m_service.getConnectedDevices();

        for (int i = 0; i < connected_devices.size(); i++) {
            BluetoothGatt gatt = connected_devices.get(i);
            if (m_service != null && gatt != null) {
                m_service.disconnectDevice(gatt.getDevice().getAddress());
            }
        }
        JSONObject response = new JSONObject();
        response.put(Constants.kResult, Constants.kConfigure);
        isRequestExist = true;
        sendResponse(response);
    }

    private void getState() throws JSONException
    {
        JSONObject parameters = new JSONObject();
        JSONObject response = new JSONObject();
        String stateString;

        stateString = Util.centralStateStringFromCentralState(m_service.getServiceState());

        parameters.put(Constants.kState, stateString);

        response.put(Constants.kParams, parameters);
        response.put(Constants.kResult, Constants.kCentralState);

        sendResponse(response);
    }

    private void scanForPeripherals(JSONObject reqObj) throws JSONException
    {
        String requestID = getRequestID(reqObj);
        JSONObject params;
        JSONArray servcieUUIDArray;
        boolean duplicates;

        if (!isPoweredOn()) {
            sendReasonForFailedCall(Constants.kScanForPeripherals, requestID);
            return;
        }

        // Clear the list of service IDs that are used for filtering scan.
        m_filtered_services.clear();

        if (!reqObj.has(Constants.kParams)) {
            sendInvalidParameters(Constants.kScanForPeripherals, requestID);
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

        isRequestExist = true;
    }

    private void stopScanning(JSONObject reqObj) throws JSONException
    {
        JSONObject response;
        String requestID = getRequestID(reqObj);

        if (!isPoweredOn()) {
            sendReasonForFailedCall(Constants.kStopScanning, requestID);
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
        if (!isPoweredOn()) {
            sendReasonForFailedCall(Constants.kConnect, requestID);
        } else {
            if (reqObj.has(Constants.kParams)) {
                JSONObject parameters = reqObj.getJSONObject(Constants.kParams);

                if (parameters.has(Constants.kPeripheralUUID)) {
                    String address = parameters.getString(Constants.kPeripheralUUID);
                    m_service.connectDevice(address);
                } else {
                    sendInvalidParameters(Constants.kConnect, requestID);
                }
            } else {
                sendInvalidParameters(Constants.kConnect, requestID);
            }
        }
    }

    private void disconnectPeripheral(JSONObject reqObj) throws JSONException
    {
        // handle disconnect event
        String requestID = getRequestID(reqObj);
        if (!isPoweredOn()) {
            sendReasonForFailedCall(Constants.kDisconnect, requestID);
        } else {
            if(reqObj.has(Constants.kParams)) {
                JSONObject jObj = reqObj.getJSONObject(Constants.kParams);

                if(jObj.has(Constants.kPeripheralUUID)) {
                    String address = jObj.getString(Constants.kPeripheralUUID);
                    m_service.disconnectDevice(address);
                } else {
                    sendInvalidParameters(Constants.kDisconnect, requestID);
                }
            } else {
                sendInvalidParameters(Constants.kDisconnect, requestID);
            }
        }
    }

    private void getServices(JSONObject reqObj) throws JSONException
    {
        String requestID = getRequestID(reqObj);
        if(reqObj.has(Constants.kParams)) {
            JSONObject jObj = reqObj.getJSONObject(Constants.kParams);

            if(jObj.has(Constants.kPeripheralUUID)) {
                String peripheralAddress = jObj.getString(Constants.kPeripheralUUID);
                m_service.getDeviceServices(peripheralAddress);
            } else {
                sendInvalidParameters(Constants.kGetServices, requestID);
            }
        } else {
            sendInvalidParameters(Constants.kGetServices, requestID);
        }
    }

    private void getCharacteristics(JSONObject reqObj) throws JSONException
    {
        String serviceUUIDString;
        JSONObject reqParameters;

        reqParameters = reqObj.getJSONObject(Constants.kParams);
        serviceUUIDString = reqParameters.getString(Constants.kServiceUUID);

        m_service.getDeviceAttributes(serviceUUIDString);
    }

    private void getDescriptors(JSONObject reqObj) throws JSONException
    {
        String requestID = getRequestID(reqObj);
        if (reqObj.has(Constants.kParams)) {
            JSONObject reqparameters = reqObj.getJSONObject(Constants.kParams);

            if (reqparameters.has(Constants.kCharacteristicUUID)) {
                String characteristicsUUIDString = reqparameters.getString(Constants.kCharacteristicUUID).toUpperCase(Locale.getDefault());
                m_service.getDeviceAttributeDescriptors(characteristicsUUIDString);
            } else {
                sendInvalidParameters(Constants.kGetDescriptors, requestID);
            }
        } else {
            sendInvalidParameters(Constants.kGetDescriptors, requestID);
        }
    }

    private void getCharacteristicValue(JSONObject reqObj) throws JSONException
    {
        String requestID = getRequestID(reqObj);

        if(reqObj.has(Constants.kParams)) {
            JSONObject jObj = reqObj.getJSONObject(Constants.kParams);

            if(jObj.has(Constants.kCharacteristicUUID)) {
                String characteristicUUID =  Util.ConvertUUID_16bitInto128bit(jObj.getString(Constants.kCharacteristicUUID).toUpperCase(Locale.getDefault()));
                m_service.getDeviceAttributeValue(characteristicUUID);
            } else {
                sendInvalidParameters(Constants.kGetCharacteristicValue,requestID);
            }
        } else {
            sendInvalidParameters(Constants.kGetCharacteristicValue,requestID);
        }
    }

    private void writeCharacteristicValue(JSONObject reqObj) throws JSONException
    {
        String requestID = getRequestID(reqObj);
        if(reqObj.has(Constants.kParams)) {
            JSONObject jObj = reqObj.getJSONObject(Constants.kParams);

            if(jObj.has(Constants.kCharacteristicUUID) && jObj.has(Constants.kValue)) {
                String writeType = null;
                String characteristicUUID = Util.ConvertUUID_16bitInto128bit(jObj.getString(Constants.kCharacteristicUUID).toUpperCase(Locale.getDefault()));

                int length = jObj.getString(Constants.kValue).length();
                if(length%2 != 0) {
                    sendInvalidLength(Constants.kWriteCharacteristicValue ,requestID);
                    return;
                }
                byte[] writeData = Util.hexStringToByteArray(jObj.getString(Constants.kValue));

                if(jObj.has(Constants.kWriteType)) {
                    writeType = Util.writeTypeForCharacteristicGiven(jObj.getString(Constants.kWriteType));
                }

                m_service.writeDeviceAttributeValue(characteristicUUID, writeType, writeData);
            } else {
                sendInvalidParameters(Constants.kWriteCharacteristicValue,requestID);
            }
        } else {
            sendInvalidParameters(Constants.kWriteCharacteristicValue,requestID);
        }
    }

    private void setValueNotification(JSONObject reqObj) throws JSONException
    {
        m_notifications = !m_notifications;
        String requestID = getRequestID(reqObj);

        if(reqObj.has(Constants.kParams)) {
            JSONObject jObj = reqObj.getJSONObject(Constants.kParams);

            if(jObj.has(Constants.kCharacteristicUUID) && jObj.has(Constants.kValue)) {
                String characteristicUUID = Util.ConvertUUID_16bitInto128bit(jObj.getString(Constants.kCharacteristicUUID).toUpperCase(Locale.getDefault()));
                String subscribe = jObj.getString(Constants.kValue);
                Boolean subscribeBOOL = subscribe.equals("true");
                m_isNotifying = subscribeBOOL;
                m_service.getDeviceAttributeNotifications(characteristicUUID, subscribeBOOL);
            } else {
                sendInvalidParameters(Constants.kSetValueNotification, requestID);
            }
        } else {
            sendInvalidParameters(Constants.kSetValueNotification, requestID);
        }

        isRequestExist = true;
    }

    private void getDescriptorValue(JSONObject reqObj) throws JSONException
    {
        String requestID = getRequestID(reqObj);
        if(reqObj.has(Constants.kParams)) {
            JSONObject jObj = reqObj.getJSONObject(Constants.kParams);

            if(jObj.has(Constants.kDescriptorUUID)) {
                String descriptorUUID = Util.ConvertUUID_16bitInto128bit(jObj.getString(Constants.kDescriptorUUID).toUpperCase(Locale.getDefault()));
                m_service.getDeviceAttributeDescriptorValue(descriptorUUID);
            } else {
                sendInvalidParameters(Constants.kGetDescriptorValue,requestID);
            }
        } else {
            sendInvalidParameters(Constants.kGetDescriptorValue,requestID);
        }
    }

    private void writeDescriptorValue(JSONObject reqObj) throws JSONException
    {
        String requestID = getRequestID(reqObj);
        if(reqObj.has(Constants.kParams)) {
            JSONObject jObj = reqObj.getJSONObject(Constants.kParams);

            if(jObj.has(Constants.kCharacteristicUUID) && jObj.has(Constants.kValue)) {
                String descriptorUUID = Util.ConvertUUID_16bitInto128bit(jObj.getString(Constants.kDescriptorUUID));
                byte[] writeData = Util.hexStringToByteArray(jObj.getString(Constants.kValue));

                m_service.writeDeviceAttributeDescriptorValue(descriptorUUID, writeData);
            } else {
                sendInvalidParameters(Constants.kWriteCharacteristicValue,requestID);
            }
        } else {
            sendInvalidParameters(Constants.kWriteCharacteristicValue,requestID);
        }
    }

    private void getPeripheralState(JSONObject reqObj) throws JSONException
    {
        String requestID = getRequestID(reqObj);
    // TODO: rewrte.
        try {
            if(reqObj.has(Constants.kParams)) {
                JSONObject jObj = reqObj.getJSONObject(Constants.kParams);

                if(jObj.has(Constants.kPeripheralUUID)) {
                    String address = jObj.getString(Constants.kPeripheralUUID);
                    BluetoothGatt gatt = Util.peripheralIn(m_service.getConnectedDevices(), address);

                    if (gatt == null) {
                        sendPeripheralNotFoundErrorMessage(Constants.kGetPeripheralState,requestID);
                        return;
                    }

                    String peripheralState = Util.peripheralStateStringFromPeripheralState(gatt);
                    JSONObject respObj = new JSONObject();

                    respObj.put(Constants.kStateField, peripheralState);
                    respObj.put(Constants.kError, null);
                    respObj.put(Constants.kResult, Constants.kGetPeripheralState);

                    sendResponse(respObj);
                } else {
                    sendInvalidParameters(Constants.kGetPeripheralState,requestID);
                    return;
                }
            } else {
                sendInvalidParameters(Constants.kGetPeripheralState,requestID);
                return;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void getRSSI(JSONObject reqObj) throws JSONException
    {
        String requestID = getRequestID(reqObj);

        if(reqObj.has(Constants.kParams)) {
            JSONObject jObj = reqObj.getJSONObject(Constants.kParams);

            if(jObj.has(Constants.kPeripheralUUID)) {
                String address = jObj.getString(Constants.kPeripheralUUID);
                m_service.getDeviceSignal(address);
            } else {
                sendInvalidParameters(Constants.kGetRSSI, requestID);
            }
        } else {
            sendInvalidParameters(Constants.kGetRSSI, requestID);
        }
    }

    private void sendReasonForFailedCall(String method, String requestId) throws JSONException
    {
        JSONObject response = new JSONObject();
        JSONObject errorObj = new JSONObject();

        response.put(Constants.kResult, method);

        if(requestId!=null)
            response.put(Constants.kRequestId, requestId);
        errorObj.put(Constants.kCode, Constants.kError32001);
        errorObj.put(Constants.kMessageField, "ERROR");
        response.put(Constants.kError, errorObj);
        sendResponse(response);
    }

    private void sendPeripheralNotFoundErrorMessage(String method, String requestId) throws JSONException
    {
        JSONObject errorObj = new JSONObject();
        JSONObject response = new JSONObject();

        response.put(Constants.kResult, method);
        if(requestId!=null)
            response.put(Constants.kRequestId, requestId);
        errorObj.put(Constants.kCode, Constants.kError32001);
        errorObj.put(Constants.kMessageField, "Peripheral not found.");
        response.put(Constants.kError, errorObj);

        sendResponse(response);
    }

    private void sendServiceNotFoundErrorMessage(String method, String requestId) throws JSONException
    {
        JSONObject errorObj = new JSONObject();
        JSONObject response = new JSONObject();

        response.put(Constants.kResult, method);
        if(requestId!=null)
            response.put(Constants.kRequestId, requestId);
        errorObj.put(Constants.kCode, Constants.kError32002);
        errorObj.put(Constants.kMessageField, "Service not found.");
        response.put(Constants.kError, errorObj);

        sendResponse(response);
    }

    private void sendCharacteristicNotFoundErrorMessage(String method, String requestId) throws JSONException
    {
        JSONObject errorObj = new JSONObject();
        JSONObject response = new JSONObject();

        response.put(Constants.kResult, method);

        if(requestId!=null) {
            response.put(Constants.kRequestId, requestId);
        }

        errorObj.put(Constants.kCode, Constants.kError32003);
        errorObj.put(Constants.kMessageField, "Characteristic not found.");

        response.put(Constants.kError, errorObj);

        sendResponse(response);
    }

    private void sendDescriptorNotFoundErrorMessage(String method, String requestId) throws JSONException
    {
        JSONObject errorObj = new JSONObject();
        JSONObject response = new JSONObject();

        response.put(Constants.kResult, method);
        if(requestId!=null)
            response.put(Constants.kRequestId, requestId);
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
        sendResponse(jsonData);
    }

    private void sendInvalidParameters(String method, String requestId) throws JSONException
    {
        JSONObject errorObj = new JSONObject();
        JSONObject response = new JSONObject();
        response.put(Constants.kResult, method);
        if(requestId!=null)
            response.put(Constants.kRequestId, requestId);
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

    private void sendInvalidLength(String method, String requestId) throws JSONException{

        JSONObject errorObj = new JSONObject();
        JSONObject response = new JSONObject();
        response.put(Constants.kResult, method);
        if(requestId!=null)
            response.put(Constants.kRequestId, requestId);
        errorObj.put(Constants.kCode, Constants.kError32603);
        errorObj.put(Constants.kMessageField, "Invalid length.");
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
   public void onDeviceFound(String deviceIdentifier, String deviceName, int deviceSignal, List<String> serviceUUIDs, byte[] deviceData)
   {

       if(serviceUUIDs.size() > 0) {
           Log.v("service UUIDs", ""+serviceUUIDs.size());
       }
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
               sendScanResponse(deviceIdentifier, deviceName, deviceSignal, deviceData);
           }
       } else {
           sendScanResponse(deviceIdentifier, deviceName, deviceSignal, deviceData);
       }
   }

    public void sendScanResponse(String deviceIdentifier, String deviceName, int deviceSignal, byte[] deviceData) {
        try {
            JSONObject response = new JSONObject();
            JSONObject parameters = new JSONObject();
            JSONObject adParameters = new JSONObject();

            if(deviceIdentifier != null) {
                String btAddr = deviceIdentifier.replaceAll(":", "-");
                parameters.put(Constants.kPeripheralBtAddress, btAddr);
                parameters.put(Constants.kPeripheralUUID, deviceIdentifier);
            }

            adParameters.put(Constants.kRawAdvertisementData, Util.byteArrayToHex(deviceData));

            parameters.put(Constants.kAdvertisementDataKey, adParameters);
            parameters.put(Constants.kRSSIkey, deviceSignal);

            if(deviceName != null) {
                parameters.put(Constants.kPeripheralName, deviceName);
            } else {
                parameters.put(Constants.kPeripheralName, "Unknown");
            }

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
        JSONObject parameters = new JSONObject();
        JSONObject response = new JSONObject();

        // send connected response to client
        try {
            parameters.put(Constants.kPeripheralUUID, deviceIdentifier);

            if(deviceName != null) {
                parameters.put(Constants.kPeripheralName, deviceName);
            } else {
                parameters.put(Constants.kPeripheralName, "");
            }

            response.put(Constants.kResult, Constants.kConnect);
            response.put(Constants.kParams, parameters);

            sendResponse(response);

            m_service.stopDeviceDiscovery();
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
                parameters.put(Constants.kPeripheralName, "");
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
    public void onDeviceServices(String deviceIdentifier, List services, int status) {
        JSONObject response = new JSONObject();
        JSONObject errorCode = new JSONObject();
        JSONObject parameters = new JSONObject();

        if (status == BluetoothGatt.GATT_SUCCESS) {
            List<JSONObject> json_services = Util.listOfJsonServicesFrom(services);

            try {
                parameters.put(Constants.kPeripheralUUID, deviceIdentifier);
                parameters.put(Constants.kServices, new JSONArray(json_services));

                response.put(Constants.kResult, Constants.kGetServices);
                response.put(Constants.kParams, parameters);

                sendResponse(response);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else {
            try {
                errorCode.put(Constants.kCode, Constants.kError32603);
                errorCode.put(Constants.kMessageField, "Failed to get services");
                parameters.put(Constants.kPeripheralUUID, deviceIdentifier);
                response.put(Constants.kParams, parameters);
                response.put(Constants.kResult, Constants.kGetServices);
                response.put(Constants.kError, errorCode);

                sendResponse(response);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onDeviceAttributes(String deviceIdentifier, String serviceIdentifier, List characteristics)
    {
        List<JSONObject> listOfCharacteristics = Util.listOfJsonCharacteristicsFrom(characteristics);
        JSONObject parameters = new JSONObject();
        JSONObject response = new JSONObject();
        String serviceUUISString = Util.ConvertUUID_128bitInto16bit(serviceIdentifier.toUpperCase(Locale.getDefault()));


        try {
            parameters.put(Constants.kPeripheralUUID, deviceIdentifier);
            parameters.put(Constants.kServiceUUID, serviceUUISString);
            parameters.put(Constants.kCharacteristics, new JSONArray(listOfCharacteristics));
            response.put(Constants.kResult, Constants.kGetCharacteristics);
            response.put(Constants.kParams, parameters);

            sendResponse(response);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onDevcieAttributeRead(String deviceIdentifier, String serviceIdentifier, String attribIdentifier, byte[] attribValue, int status)
    {
        String characteristicUUIDString = Util.ConvertUUID_128bitInto16bit(attribIdentifier.toUpperCase(Locale.getDefault()));
        String serviceUUISString = Util.ConvertUUID_128bitInto16bit(serviceIdentifier.toUpperCase(Locale.getDefault()));

        if(status == BluetoothGatt.GATT_SUCCESS) {
            JSONObject response = new JSONObject();
            JSONObject parameters = new JSONObject();

            String characteristicValueString = Util.byteArrayToHex(attribValue);

            try {
                parameters.put(Constants.kCharacteristicUUID, characteristicUUIDString);
                parameters.put(Constants.kPeripheralUUID, deviceIdentifier);
                parameters.put(Constants.kServiceUUID, serviceUUISString);
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

        String characteristicUUIDString = Util.ConvertUUID_128bitInto16bit(attribIdentifier.toUpperCase(Locale.getDefault()));


        if (status == BluetoothGatt.GATT_SUCCESS) {
            try {
                parameters.put(Constants.kCharacteristicUUID, characteristicUUIDString);
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
        String serviceUUISString = Util.ConvertUUID_128bitInto16bit(serviceIdentifier.toUpperCase(Locale.getDefault()));
        String characteristicValueString = Util.byteArrayToHex(attribValue);

        if (status == BluetoothGatt.GATT_SUCCESS) {
            try {
                parameters.put(Constants.kCharacteristicUUID, characteristicUUIDString);
                parameters.put(Constants.kPeripheralUUID, deviceIdentifier);
                parameters.put(Constants.kServiceUUID, serviceUUISString);
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
                parameters.put(Constants.kServiceUUID, serviceIdentifier);
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
        JSONObject parameters = new JSONObject();
        JSONObject response = new JSONObject();
        List<JSONObject> descriptorArray = Util.listOfJsonDescriptorsFrom(attribDescriptors);
        String characteristicUUIDString = Util.ConvertUUID_128bitInto16bit(attribIdentifier.toUpperCase(Locale.getDefault()));
        String serviceUUISString = Util.ConvertUUID_128bitInto16bit(serviceIdentifier.toUpperCase(Locale.getDefault()));

        try {
            parameters.put(Constants.kCharacteristicUUID, characteristicUUIDString);
            parameters.put(Constants.kPeripheralUUID, deviceIdentifier);
            parameters.put(Constants.kServiceUUID, serviceUUISString);
            parameters.put(Constants.kDescriptors, new JSONArray(descriptorArray));

            response.put(Constants.kResult, Constants.kGetDescriptors);
            response.put(Constants.kParams, parameters);

            sendResponse(response);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onDeviceAttributeDescriptorRead(String deviceIdentifier, String serviceIdentifier, String attribIdentifier, String attribDescriptorIdentifier, byte[] attributeDescriptorValue, int status)
    {
        JSONObject response = new JSONObject();
        JSONObject parameters = new JSONObject();

        if (status == BluetoothGatt.GATT_SUCCESS) {
            try {
                String descriptorValue = new String(attributeDescriptorValue, "UTF-8");

                parameters.put(Constants.kDescriptorUUID, attribDescriptorIdentifier);
                parameters.put(Constants.kValue, descriptorValue);

                response.put(Constants.kResult,Constants.kGetDescriptorValue);
                response.put(Constants.kParams, parameters);

                sendResponse(response);
            } catch (UnsupportedEncodingException | JSONException e) {
                e.printStackTrace();
            }
        } else {
            try {
                JSONObject errorObj = new JSONObject();

                errorObj.put(Constants.kCode, Constants.kError32603);
                errorObj.put(Constants.kMessageField, "");

                parameters.put(Constants.kDescriptorUUID, attribDescriptorIdentifier);

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
            String method = m_current_request.getString(Constants.kMethod);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (method.equals(Constants.kSetValueNotification)) {
                    parameters.put(Constants.kIsNotifying, m_isNotifying);
                    parameters.put(Constants.kCharacteristicUUID, attribIdentifier);
                    parameters.put(Constants.kPeripheralUUID, deviceIdentifier);
                    parameters.put(Constants.kServiceUUID, serviceIdentifier);

                    response.put(Constants.kResult, Constants.kSetValueNotification);
                    response.put(Constants.kParams, parameters);

                    sendResponse(response);
                } else if (method.equals(Constants.kWriteDescriptorValue)) {
                    parameters.put(Constants.kCharacteristicUUID, attribIdentifier);
                    parameters.put(Constants.kPeripheralUUID, deviceIdentifier);
                    parameters.put(Constants.kServiceUUID, serviceIdentifier);

                    response.put(Constants.kResult, Constants.kWriteDescriptorValue);
                    response.put(Constants.kParams, parameters);

                    sendResponse(response);
                }
            } else {
                sendReasonForFailedCall(method, getRequestID(m_current_request));
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
                    parameters.put(Constants.kPeripheralName, "");
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
                    parameters.put(Constants.kPeripheralName, "");
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
    public void onError(Enum error)
    {
        try {
            String requestId = getRequestID(this.m_current_request);
            String method = this.m_current_request.getString(Constants.kMethod);

            if(error.equals(InterfaceService.Error.DEVICE_NOT_FOUND)){
                sendPeripheralNotFoundErrorMessage(method, requestId);
            } else if(error.equals(InterfaceService.Error.DEVICE_SERVICE_NOT_FOUND)) {
                sendServiceNotFoundErrorMessage(method, requestId);
            } else if(error.equals(InterfaceService.Error.DEVICE_ATTRIBUTES_NOT_FOUND)) {
                sendCharacteristicNotFoundErrorMessage(method, requestId);
            } else if(error.equals(InterfaceService.Error.ATTRIBUTE_DESCRIPTOR_NOT_FOUND)) {
                sendDescriptorNotFoundErrorMessage(method, requestId);
            } else if(error.equals(InterfaceService.Error.ATTRIBUTE_READ_FAILED)) {

            } else if(error.equals(InterfaceService.Error.ATTRIBUTE_WRITE_FAILED)) {

            } else if(error.equals(InterfaceService.Error.ATTRIBUTE_DESCRIPTOR_READ_FAILED)) {

            } else if(error.equals(InterfaceService.Error.ATTRIBUTE_DESCRIPTOR_WRITE_FAILED)) {

            } else if(error.equals(InterfaceService.Error.PERMISSOIN_DENIED)) {

            } else if(error.equals(InterfaceService.Error.CONNECTION_FAILED)) {

            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
