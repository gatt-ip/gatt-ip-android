package org.gatt_ip;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import org.gatt_ip.lescanner.BluetoothLEScanner;
import org.gatt_ip.lescanner.BluetoothLEScannerForLollipop;
import org.gatt_ip.lescanner.BluetoothLEScannerForMR2;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class GATTIP implements ServiceConnection {

    private static final int MAX_NUMBER_OF_REQUESTS = 60;
    private ArrayList<BluetoothGatt> mConnectedDevices;
    private Context mContext;
    static GATTIPListener listener;
    private BluetoothAdapter mBluetoothAdapter;
    private List<BluetoothDevice> mAvailableDevices;
    private BluetoothLEScanner mLeScanner;
    private String mAddress;
    private BluetoothService mService;
    private BluetoothLEScanner.LEScanListener mLeScanlistener;
    private String sCBUUID;
    public static int scanning = 0;
    private boolean mNotifications;
    private static boolean BTConnect = true;
    private static List<JSONObject> messageQueue = new ArrayList<JSONObject>();
    private static boolean isRequestExist = true;
    final ArrayList<String> serviceList = new ArrayList<String>();

    public GATTIP(Context ctx) {
        mContext = ctx;
        previousRequests = new ArrayList<JSONObject>();
        mAvailableDevices = new ArrayList<BluetoothDevice>();
        mConnectedDevices = new ArrayList<BluetoothGatt>();
        mContext.bindService(new Intent(mContext, BluetoothService.class), this, 0);
        mContext.startService(new Intent(mContext, BluetoothService.class));
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        try {
            mService = ((BluetoothService.BluetoothBinder) iBinder).getService();
            mService.registerListener(bluetoothListener);
            if(mContext != null)
                mContext.registerReceiver(bReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        try {
            if(mLeScanner != null) {
                mLeScanner.stopLEScan();
                scanning = 0;
            }
            if(mContext != null) {
                mContext.unregisterReceiver(bReceiver);
                mContext.unbindService(this);
            }
            if(mService != null) {
                mService.unregisterListener(bluetoothListener);
                mService = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public ArrayList<BluetoothGatt> getConnectedDevices() {
        return mConnectedDevices;
    }

    // set the reference for listener when we got request from client
    public void setGATTIPListener(GATTIPListener GATTIPlistener) {
        listener = GATTIPlistener;
    }

    // sending response to client for requested command
    public void sendResponse(JSONObject jsonData) {
        // handle log messages to display in LogView
        handleLoggingRequestAndResponse(jsonData);

        if (listener == null) {
            return;
        }
        try {
            jsonData.put(Constants.kJsonrpc, Constants.kJsonrpcVersion);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        try {
            for(int i = 0; i < messageQueue.size(); i++) {
                JSONObject reqObj = messageQueue.get(i);
                String request = reqObj.getString(Constants.kMethod);
                String response = jsonData.getString(Constants.kResult);
                JSONObject requestParams = reqObj.getJSONObject(Constants.kParams);
                JSONObject responseParams = jsonData.getJSONObject(Constants.kParams);
                if(request.equals(response) && requestParams.getString(Constants.kCharacteristicUUID).equals(responseParams.getString(Constants.kCharacteristicUUID))) {
                    messageQueue.remove(i);
                    isRequestExist = true;
                    break;
                }
            }
        } catch (JSONException je) {
            je.printStackTrace();
        }
        listener.response(jsonData.toString());
        //handle multiple commands
        if (messageQueue.size() > 0 && isRequestExist) {
            try {
                requestMethod(messageQueue.get(0), messageQueue.get(0).getString(Constants.kMethod));
            } catch (JSONException je) {
                je.printStackTrace();
            }
        }
    }

    BluetoothListener bluetoothListener = new BluetoothListener() {

        @Override
        public void connectLEDevice(BluetoothGatt gatt){
            JSONObject parameters = new JSONObject();
            JSONObject response = new JSONObject();
            BluetoothDevice device = gatt.getDevice();

            if (mConnectedDevices.contains(gatt)) {
                for (int i = 0; i < mConnectedDevices.size(); i++) {
                    if (mConnectedDevices.get(i).equals(gatt))
                        mConnectedDevices.set(i, mConnectedDevices.get(i));
                }
            } else {
                mConnectedDevices.add(gatt);
            }

            // send connected response to client
            try {
                parameters.put(Constants.kPeripheralUUID,device.getAddress());
                if(device.getName() != null)
                    parameters.put(Constants.kPeripheralName, device.getName());
                else
                    parameters.put(Constants.kPeripheralName, "");
                response.put(Constants.kResult, Constants.kConnect);
                response.put(Constants.kParams, parameters);
                sendResponse(response);
            } catch (JSONException je) {
                je.printStackTrace();
            }
        }

        @Override
        public void disconnectLEDevice(BluetoothGatt gatt){
            JSONObject parameters = new JSONObject();
            JSONObject response = new JSONObject();
            BluetoothDevice device = gatt.getDevice();
            // send response to client
            try {
                // get the json data to send client
                parameters.put(Constants.kPeripheralUUID,device.getAddress());
                if(device.getName() != null)
                    parameters.put(Constants.kPeripheralName, device.getName());
                else
                    parameters.put(Constants.kPeripheralName, "");
                response.put(Constants.kResult, Constants.kDisconnect);
                response.put(Constants.kParams, parameters);
                sendResponse(response);
            } catch (JSONException je) {
                je.printStackTrace();
            }
            // delete connected device from list after disconnect device.
            if (mConnectedDevices.contains(gatt)) {
                for (int i = 0; i < mConnectedDevices.size(); i++) {
                    if (mConnectedDevices.get(i).equals(gatt))
                       mConnectedDevices.remove(i);
                 }
            }
        }

        @Override
        public void connectionFailed(BluetoothGatt gatt){
            JSONObject response = new JSONObject();
            JSONObject errorCode = new JSONObject();
            JSONObject parameters = new JSONObject();
            try {
                errorCode.put(Constants.kCode, Constants.kError32603);
                errorCode.put(Constants.kMessageField, "failed to connect");
                parameters.put(Constants.kPeripheralUUID, gatt.getDevice().getAddress());
                response.put(Constants.kParams, parameters);
                response.put(Constants.kResult, Constants.kConnect);
                response.put(Constants.kError, errorCode);

                sendResponse(response);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void getLEServices(BluetoothGatt gatt, int status){
            JSONObject response = new JSONObject();
            JSONObject errorCode = new JSONObject();
            JSONObject parameters = new JSONObject();

            if (status == BluetoothGatt.GATT_SUCCESS) {
                String peripheralUUIDString = gatt.getDevice().getAddress().toUpperCase(Locale.getDefault());
                List<JSONObject> services = Util.listOfJsonServicesFrom(gatt.getServices());
                try {
                    parameters.put(Constants.kPeripheralUUID,peripheralUUIDString);
                    parameters.put(Constants.kServices, new JSONArray(services));

                    response.put(Constants.kResult, Constants.kGetServices);
                    response.put(Constants.kParams, parameters);

                    sendResponse(response);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            } else {
                try {
                    errorCode.put(Constants.kCode, Constants.kError32603);
                    errorCode.put(Constants.kMessageField, "failed to get services");
                    parameters.put(Constants.kPeripheralUUID, gatt.getDevice().getAddress());
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
        public void readCharacteristicValue(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status){
            String characteristicUUIDString = Util.ConvertUUID_128bitInto16bit(characteristic.getUuid().toString().toUpperCase(Locale.getDefault()));
            if(status == BluetoothGatt.GATT_SUCCESS) {
                JSONObject response = new JSONObject();
                JSONObject parameters = new JSONObject();

                byte[] characteristicValue = characteristic.getValue();
                String characteristicValueString = Util.byteArrayToHex(characteristicValue);

                try {
                    String characteristicProperty = "" + characteristic.getProperties();
                    String peripheralUUIDString = gatt.getDevice().getAddress().toUpperCase(Locale.getDefault());
                    String serviceUUIDString = Util.ConvertUUID_128bitInto16bit(characteristic.getService().getUuid().toString().toUpperCase(Locale.getDefault()));
                    int characteristicIsNotifying;

                    if (mNotifications)
                        characteristicIsNotifying = 1;
                    else
                        characteristicIsNotifying = 0;

                    parameters.put(Constants.kIsNotifying, characteristicIsNotifying);
                    parameters.put(Constants.kProperties, characteristicProperty);
                    parameters.put(Constants.kCharacteristicUUID, characteristicUUIDString);
                    parameters.put(Constants.kPeripheralUUID, peripheralUUIDString);
                    parameters.put(Constants.kServiceUUID, serviceUUIDString);
                    parameters.put(Constants.kValue, characteristicValueString);

                    response.put(Constants.kResult, Constants.kGetCharacteristicValue);
                    response.put(Constants.kParams, parameters);

                    sendResponse(response);

                    return;
                } catch (JSONException je) {
                    je.printStackTrace();
                }
                try {
                    JSONObject errorCode = new JSONObject();

                    errorCode.put(Constants.kCode, Constants.kError32603);
                    errorCode.put(Constants.kMessageField, "read data failed");

                    parameters.put(Constants.kCharacteristicUUID, characteristicUUIDString);
                    response.put(Constants.kResult, Constants.kGetCharacteristicValue);

                    response.put(Constants.kParams, parameters);
                    response.put(Constants.kError, errorCode);

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
            }
        }

        @Override
        public void writeCharacteristicValue(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            String characteristicUUIDString = Util.ConvertUUID_128bitInto16bit(characteristic.getUuid().toString().toUpperCase(Locale.getDefault()));
            String peripheralUUIDString = gatt.getDevice().getAddress().toUpperCase(Locale.getDefault());

            JSONObject parameters = new JSONObject();
            JSONObject response = new JSONObject();

            if (status == BluetoothGatt.GATT_SUCCESS) {
                try {
                    parameters.put(Constants.kCharacteristicUUID, characteristicUUIDString);
                    parameters.put(Constants.kPeripheralUUID, peripheralUUIDString);

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
                    parameters.put(Constants.kPeripheralUUID, peripheralUUIDString);

                    errorCode.put(Constants.kCode, Constants.kError32603);
                    errorCode.put(Constants.kMessageField, "write data failed");

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
        public void setValueNotification(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            JSONObject response = new JSONObject();
            JSONObject parameters = new JSONObject();
            String characteristicUUIDString = Util.ConvertUUID_128bitInto16bit(characteristic.getUuid().toString().toUpperCase(Locale.getDefault()));
            String peripheralUUIDString = gatt.getDevice().getAddress().toUpperCase(Locale.getDefault());
            String serviceUUIDString = Util.ConvertUUID_128bitInto16bit(characteristic.getService().getUuid().toString().toUpperCase(Locale.getDefault()));
            byte[] characteristicValue = characteristic.getValue();
            String characteristicValueString = Util.byteArrayToHex(characteristicValue);
            String characteristicProperty = "" + characteristic.getProperties();
            int characteristicIsNotifying;

            if (mNotifications)
                characteristicIsNotifying = 1;
            else
                characteristicIsNotifying = 0;

            try {
                parameters.put(Constants.kIsNotifying, characteristicIsNotifying);
                parameters.put(Constants.kProperties, characteristicProperty);
                parameters.put(Constants.kCharacteristicUUID, characteristicUUIDString);
                parameters.put(Constants.kPeripheralUUID, peripheralUUIDString);
                parameters.put(Constants.kServiceUUID, serviceUUIDString);
                parameters.put(Constants.kValue, characteristicValueString);

                response.put(Constants.kResult, Constants.kSetValueNotification);
                response.put(Constants.kParams, parameters);

                sendResponse(response);

                return;
            } catch (JSONException je) {
                je.printStackTrace();
            }
            // error occur when we set notification for a characteristic
            try {
                JSONObject errorCode = new JSONObject();

                parameters.put(Constants.kCharacteristicUUID, characteristicUUIDString);
                parameters.put(Constants.kServiceUUID, serviceUUIDString);
                parameters.put(Constants.kPeripheralUUID, peripheralUUIDString);

                errorCode.put(Constants.kCode, Constants.kError32603);
                errorCode.put(Constants.kMessageField, "write data failed");

                response.put(Constants.kResult, Constants.kSetValueNotification);
                response.put(Constants.kParams, parameters);
                response.put(Constants.kError, errorCode);

                sendResponse(response);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void readDescriptorValue(BluetoothGatt gatt,BluetoothGattDescriptor descriptor, int status){
            JSONObject response = new JSONObject();
            JSONObject parameters = new JSONObject();
            String desriptorUUID = Util.ConvertUUID_128bitInto16bit(descriptor.getUuid().toString());
            if (status == BluetoothGatt.GATT_SUCCESS) {
                byte[] byteValue = descriptor.getValue();
                try {
                    String descriptorValue = new String(byteValue, "UTF-8");
                    parameters.put(Constants.kDescriptorUUID, desriptorUUID);
                    parameters.put(Constants.kValue, descriptorValue);
                    response.put(Constants.kResult,Constants.kGetDescriptorValue);
                    response.put(Constants.kParams, parameters);
                    sendResponse(response);
                    return;
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            try {
                JSONObject errorObj = new JSONObject();
                errorObj.put(Constants.kCode, Constants.kError32603);
                errorObj.put(Constants.kMessageField, "");
                parameters.put(Constants.kDescriptorUUID, desriptorUUID);
                response.put(Constants.kResult, Constants.kGetDescriptorValue);
                response.put(Constants.kError, errorObj);
                response.put(Constants.kParams, parameters);
                sendResponse(response);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void writeDescriptorValue(BluetoothGatt gatt,BluetoothGattDescriptor descriptor, int status){
            // Temp comment
			/*
			 * JSONObject response = new JSONObject(); if (status ==
			 * BluetoothGatt.GATT_SUCCESS) { try { String desriptorUUID =
			 * descriptor.getUuid().toString(); String peripheralUUIDString =
			 * gatt.getDevice().getAddress();
			 * response.put(Constants.kResult,Constants.kWriteDescriptorValue);
			 * response.put(Constants.kDescriptorUUID, desriptorUUID);
			 * response.put(Constants.kPeripheralUUID,peripheralUUIDString);
			 * sendResponse(response, null); return; } catch (JSONException e) {
			 * e.printStackTrace(); } } JSONObject errorObj = new JSONObject();
			 * try { errorObj.put(Constants.kCode, Constants.kError32603);
			 * errorObj.put(Constants.kMessageField, "");
			 * response.put(Constants.kResult, Constants.kWriteDescriptorValue);
			 * response.put(Constants.kError, errorObj); sendResponse(response,
			 * null); } catch (JSONException e) { e.printStackTrace(); }
			 */

            if (!mNotifications) {
                BluetoothGattCharacteristic characteristic = descriptor.getCharacteristic();
                JSONObject response = new JSONObject();
                String characteristicUUIDString = Util.ConvertUUID_128bitInto16bit(characteristic.getUuid().toString().toUpperCase(Locale.getDefault()));
                String peripheralUUIDString = gatt.getDevice().getAddress().toUpperCase(Locale.getDefault());
                String serviceUUIDString = Util.ConvertUUID_128bitInto16bit(characteristic.getService().getUuid().toString().toUpperCase(Locale.getDefault()));
                String characteristicValueString = Util.byteArrayToHex(characteristic.getValue());
                String characteristicProperty = ""+ characteristic.getProperties();
                int characteristicIsNotifying;

                if (mNotifications)
                    characteristicIsNotifying = 1;
                else
                    characteristicIsNotifying = 0;

                JSONObject parameters = new JSONObject();

                try {
                    parameters.put(Constants.kIsNotifying,characteristicIsNotifying);
                    parameters.put(Constants.kProperties,characteristicProperty);
                    parameters.put(Constants.kCharacteristicUUID,characteristicUUIDString);
                    parameters.put(Constants.kPeripheralUUID,peripheralUUIDString);
                    parameters.put(Constants.kServiceUUID, serviceUUIDString);
                    parameters.put(Constants.kValue, characteristicValueString);

                    response.put(Constants.kResult,Constants.kGetCharacteristicValue);
                    response.put(Constants.kParams, parameters);

                    sendResponse(response);

                    return;
                } catch (JSONException je) {
                    je.printStackTrace();
                }
                // error occur when we set notification for a characteristic
                try {
                    parameters.put(Constants.kCharacteristicUUID,characteristicUUIDString);
                    parameters.put(Constants.kServiceUUID, serviceUUIDString);
                    parameters.put(Constants.kPeripheralUUID,peripheralUUIDString);
                    JSONObject errorCode = new JSONObject();
                    errorCode.put(Constants.kCode, Constants.kError32603);
                    errorCode.put(Constants.kMessageField, "write data failed");
                    response.put(Constants.kResult,Constants.kSetValueNotification);
                    response.put(Constants.kParams, parameters);
                    response.put(Constants.kError, errorCode);
                    sendResponse(response);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void readRemoteRssi(BluetoothGatt gatt, int rssi, int status){
            JSONObject response = new JSONObject();
            JSONObject parameters = new JSONObject();
            String peripheralUUIDString = gatt.getDevice().getAddress().toUpperCase(Locale.getDefault());
            BluetoothDevice device = gatt.getDevice();
            if (status == BluetoothGatt.GATT_SUCCESS) {
                try {
                    response.put(Constants.kResult, Constants.kGetRSSI);
                    parameters.put(Constants.kPeripheralUUID,peripheralUUIDString);
                    if(device.getName() != null)
                        parameters.put(Constants.kPeripheralName, device.getName());
                    else
                        parameters.put(Constants.kPeripheralName, "");
                    parameters.put(Constants.kRSSIkey, rssi);
                    response.put(Constants.kParams, parameters);
                    sendResponse(response);
                    return;
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            try {
                JSONObject errorObj = new JSONObject();
                errorObj.put(Constants.kCode, Constants.kError32603);
                errorObj.put(Constants.kMessageField, "");
                parameters.put(Constants.kPeripheralUUID,peripheralUUIDString);
                if(device.getName() != null)
                    parameters.put(Constants.kPeripheralName, device.getName());
                else
                    parameters.put(Constants.kPeripheralName, "");
                response.put(Constants.kResult, Constants.kGetRSSI);
                response.put(Constants.kError, errorObj);
                response.put(Constants.kParams, parameters);
                sendResponse(response);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

    };

    // method to call request coming from client
    public void request(String gattipMesg) {
        if (gattipMesg == null) {
            invalidRequest();
            return;
        }
        try {
            // for handling multiple commands from client
            JSONObject reqObj = new JSONObject(gattipMesg);

            // handle log messages to display in LogView
            handleLoggingRequestAndResponse(reqObj);

            if (reqObj != null) {
                String method = reqObj.getString(Constants.kMethod);
                String[] request = method.split(",");

                if (method == null) {
                    try {
                        JSONObject errorObj = new JSONObject();
                        errorObj.put(Constants.kCode,Constants.kInvalidRequest);
                        JSONObject jsonData = new JSONObject();
                        jsonData.put(Constants.kError, errorObj);
                        jsonData.put(Constants.kIdField, null);
                        sendResponse(jsonData);
                    } catch (JSONException je) {
                        je.printStackTrace();
                    }

                     return;
                }
                if(method != null && method.equals(Constants.kGetCharacteristicValue) || method.equals(Constants.kWriteCharacteristicValue)) {
                    messageQueue.add(reqObj);
                }

                if(messageQueue.size() > 0 && isRequestExist) {
                    isRequestExist = false;
                    requestMethod(reqObj, method);
                } else if(isRequestExist){
                    requestMethod(reqObj, method);
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void requestMethod(JSONObject reqObj ,String message) {
        if (message.equals(Constants.kConfigure)) {
            configure(reqObj);
        } else if (message.equals(Constants.kConnect)) {
            connectStick(reqObj);
        } else if (message.equals(Constants.kDisconnect)) {
            disconnectStick(reqObj);
        } else if (message.equals(Constants.kGetPerhipheralsWithServices)) {
            getPerhipheralsWithServices(reqObj);
        } else if (message.equals(Constants.kGetPerhipheralsWithIdentifiers)) {
            getPerhipheralsWithIdentifiers(reqObj);
        } else if (message.equals(Constants.kScanForPeripherals)) {
            scanForPeripherals(reqObj);
        } else if (message.equals(Constants.kStopScanning)) {
            stopScanning(reqObj);
        } else if (message.equals(Constants.kCentralState)) {
            getConnectionState(reqObj);
        } else if (message.equals(Constants.kGetConnectedPeripherals)) {
            getConnectedPeripherals(reqObj);
        } else if (message.equals(Constants.kGetServices)) {
            getServices(reqObj);
        } else if (message != null && message.equals(Constants.kGetIncludedServices)) {
            getIncludedServices(reqObj);
        } else if (message.equals(Constants.kGetCharacteristics)) {
            getCharacteristics(reqObj);
        } else if (message.equals(Constants.kGetDescriptors)) {
            getDescriptors(reqObj);
        } else if (message.equals(Constants.kGetCharacteristicValue)) {
            getCharacteristicValue(reqObj);
        } else if (message.equals(Constants.kGetDescriptorValue)) {
            getDescriptorValue(reqObj);
        } else if (message.equals(Constants.kWriteCharacteristicValue)) {
            writeCharacteristicValue(reqObj);
        } else if (message.equals(Constants.kSetValueNotification)) {
            setValueNotification(reqObj);
        } else if (message.equals(Constants.kGetPeripheralState)) {
            getPeripheralState(reqObj);
        } else if (message.equals(Constants.kGetRSSI)) {
            getRSSI(reqObj);
        } else {
            try {
                JSONObject invalidMethod = new JSONObject();
                invalidMethod.put("Error", "Your Method is invalid");
                sendResponse(invalidMethod);
            } catch (JSONException je) {
                je.printStackTrace();
            }
        }
    }

    public void configure(JSONObject reqObj) {

        if(mConnectedDevices != null) {
            for(int i = 0; i < mConnectedDevices.size(); i++) {
                BluetoothGatt gatt = mConnectedDevices.get(i);
                if(mService != null && gatt != null) {
                    mService.disconnectLEDevice(gatt);
                }
            }
        }

        if(messageQueue != null) {
            for(int j = 0; j < messageQueue.size(); j++) {
                messageQueue.remove(j);
            }
        }

        JSONObject response = new JSONObject();

        try {
            response.put(Constants.kResult, Constants.kConfigure);
            getState();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        sendResponse(response);
    }

    public void getState() {
        JSONObject parameters = new JSONObject();
        JSONObject response = new JSONObject();
        String stateString;
        PackageManager manager = mContext.getPackageManager();

        final BluetoothManager bluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        if (mBluetoothAdapter == null) {
            stateString = Constants.kUnknown;
        } else if (!manager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            stateString = Constants.kUnsupported;
        } else {
            stateString = Util.centralStateStringFromCentralState(mBluetoothAdapter.getState());
        }

        try {
            parameters.put(Constants.kState, stateString);

            response.put(Constants.kParams, parameters);
            response.put(Constants.kResult, Constants.kCentralState);

            sendResponse(response);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void getPerhipheralsWithServices(JSONObject reqObj) {
        if (!isPoweredOn()) {
            sendReasonForFailedCall();
            return;
        }
        //extract the list of service UUID strings
        if(reqObj.has(Constants.kParams)) {
            try {
                JSONObject params = reqObj.getJSONObject(Constants.kParams);
                if(params != null && params.has(Constants.kServiceUUIDs)) {
                    JSONArray listOfServiceUUIDStrings = params.getJSONArray(Constants.kServiceUUIDs);
                    if(listOfServiceUUIDStrings.length() == 0) {
                        sendNoServiceSpecified();
                        return;
                    }
                    List<String> listOfServcieUUIDStrings = Util.listOfServiceUUIDStringsFrom(listOfServiceUUIDStrings);
                    List<BluetoothGatt> listOfperipheralsWithServices = Util.retrieveConnectedPeripheralsWithServices(mConnectedDevices, listOfServcieUUIDStrings);
                    JSONObject response = new JSONObject();
                    response.put(Constants.kResult, Constants.kGetPerhipheralsWithServices);
                    JSONObject peripheralsObject = new JSONObject();
                    JSONObject peripheralObject;
                    for(int i = 0; i < listOfperipheralsWithServices.size(); i++) {
                        BluetoothGatt gatt = listOfperipheralsWithServices.get(i);
                        String peripheralState = Util.peripheralStateStringFromPeripheralState(gatt);
                        String peripheralUUIDString = Util.peripheralUUIDStringFromPeripheral(gatt.getDevice());
                        peripheralObject = new JSONObject();
                        peripheralObject.put(Constants.kStateField, peripheralState);
                        peripheralObject.put(Constants.kPeripheralUUID, peripheralUUIDString);
                        peripheralsObject.put(Integer.toString(i), peripheralObject);
                    }
                    response.put(Constants.kPeripherals, peripheralsObject);
                    sendResponse(response);
                } else {
                    invalidParameters(Constants.kGetPerhipheralsWithServices);
                }
            } catch (JSONException je) {
                je.printStackTrace();
            }
        } else {
            invalidParameters(Constants.kGetPerhipheralsWithServices);
        }
    }

    public void getPerhipheralsWithIdentifiers(JSONObject reqObj) {
        if (!isPoweredOn()) {
            sendReasonForFailedCall();
            return;
        }
        //extract the list of service UUID strings
        if (reqObj.has(Constants.kParams)) {
            try {
                JSONObject params = reqObj.getJSONObject(Constants.kParams);
                if (params != null && params.has(Constants.kPeripheralUUIDs)) {
                    JSONArray listOfperipheralUUIDStrings = params.getJSONArray(Constants.kPeripheralUUIDs);
                    if (listOfperipheralUUIDStrings.length() == 0) {
                        sendNoPeripheralsSpecified();
                        return;
                    }
                    List<String> listOfPeripheralUUIDStrings = Util.listOfPeripheralUUIDStringsFrom(listOfperipheralUUIDStrings);
                    List<BluetoothGatt> peripheralsWithIdentifiers = Util.retrievePeripheralsWithIdentifiers(mConnectedDevices, listOfPeripheralUUIDStrings);
                    JSONObject response = new JSONObject();
                    response.put(Constants.kResult, Constants.kGetPerhipheralsWithIdentifiers);
                    JSONObject peripheralsObject = new JSONObject();
                    JSONObject peripheralObject;
                    for(int i = 0; i < peripheralsWithIdentifiers.size(); i++) {
                        BluetoothGatt gatt = peripheralsWithIdentifiers.get(i);
                        String peripheralState = Util.peripheralStateStringFromPeripheralState(gatt);
                        String peripheralUUIDString = Util.peripheralUUIDStringFromPeripheral(gatt.getDevice());
                        peripheralObject = new JSONObject();
                        peripheralObject.put(Constants.kStateField, peripheralState);
                        peripheralObject.put(Constants.kPeripheralUUID, peripheralUUIDString);
                        peripheralsObject.put(Integer.toString(i), peripheralObject);
                    }
                    response.put(Constants.kPeripherals, peripheralsObject);
                    sendResponse(response);
                } else {
                    invalidParameters(Constants.kGetPerhipheralsWithIdentifiers);
                }
            } catch (JSONException je) {
                je.printStackTrace();
            }
        } else {
            invalidParameters(Constants.kGetPerhipheralsWithIdentifiers);
        }
    }

    public void scanForPeripherals(JSONObject reqObj) {
        if (!isPoweredOn()) {
            sendReasonForFailedCall();
            return;
        }
        serviceList.clear();
        JSONObject params = null;
        if(reqObj.has(Constants.kParams)) {
            try {
                params = reqObj.getJSONObject(Constants.kParams);

                if(params.has(Constants.kScanOptionAllowDuplicatesKey)) {
                    String duplicateKey = params.getString(Constants.kScanOptionAllowDuplicatesKey);
                }
                if(params.has(Constants.kServiceUUIDs)) {
                    JSONArray servcieUUIDArray = params.getJSONArray(Constants.kServiceUUIDs);
                    for(int i = 0; i<servcieUUIDArray.length(); i++) {
                        serviceList.add(servcieUUIDArray.getString(i));
                    }
                }

            } catch (JSONException je) {
                je.printStackTrace();
            }
        } else {
            invalidParameters(Constants.kScanForPeripherals);
            return;
        }

        mLeScanlistener = new BluetoothLEScanner.LEScanListener() {
            @Override
            public void onLeScan(BluetoothDevice device, int rssi,List<String> serviceUUIDs, JSONObject mutatedAdevertismentData) {
                if(device == null) {
                    return;
                }

                if (mAvailableDevices.contains(device)) {
                    for (int i = 0; i < mAvailableDevices.size(); i++) {
                        if (mAvailableDevices.get(i).equals(device))
                            mAvailableDevices.set(i, device);
                    }
                } else {
                    mAvailableDevices.add(device);
                }
                if(serviceList.size()>0) {
                    int count = 0;

                    for (int i = 0; i < serviceUUIDs.size(); i++) {
                       for (int j = 0; j < serviceList.size(); j++) {
                           if (serviceUUIDs.get(i).contains(serviceList.get(j)) || serviceUUIDs.get(i).contains(serviceList.get(j))) {
                                count++;
                           }
                       }
                    }

                    if (count > 0) {
                        // call response method here
                        SendScanResponse(device, rssi, mutatedAdevertismentData);
                    }
                }  else {
                    SendScanResponse(device, rssi, mutatedAdevertismentData);
                }
            }
        };

       if(scanning > 0 && mLeScanner != null) {
           mLeScanner.stopLEScan();
           SystemClock.sleep(1000);
       } else {
           int apiVersion = android.os.Build.VERSION.SDK_INT;

           if (apiVersion >= Build.VERSION_CODES.LOLLIPOP) {
               mLeScanner = new BluetoothLEScannerForLollipop(mContext);
           } else {
               mLeScanner = new BluetoothLEScannerForMR2(mContext);
           }

           mLeScanner.registerScanListener(mLeScanlistener);
       }

        mLeScanner.startLEScan();

        scanning++;
    }

    // Response method definition

    public void SendScanResponse(BluetoothDevice device, int rssi, JSONObject mutatedAdevertismentData) {
        try {
            JSONObject response = new JSONObject();
            JSONObject parameters = new JSONObject();

            if(device.getAddress() != null) {
                String btAddr = device.getAddress().replaceAll(":", "-");
                parameters.put(Constants.kPeripheralBtAddress,btAddr);
                parameters.put(Constants.kPeripheralUUID, device.getAddress());
            }

            parameters.put(Constants.kAdvertisementDataKey, mutatedAdevertismentData);
            parameters.put(Constants.kRSSIkey, rssi);

            if(device.getName() != null)
                parameters.put(Constants.kPeripheralName, device.getName());
            else
                parameters.put(Constants.kPeripheralName,"Unknown");
            response.put(Constants.kResult, Constants.kScanForPeripherals);
            response.put(Constants.kParams, parameters);
            sendResponse(response);
        } catch (JSONException je) {
            je.printStackTrace();
        }

    }

    public void stopScanning(JSONObject reqObj) {
        if (!isPoweredOn()) {
            sendReasonForFailedCall();
            return;
        }
        JSONObject response = new JSONObject();
        JSONObject params = new JSONObject();
        try {
            response.put(Constants.kResult, Constants.kStopScanning);
            sendResponse(response);
        } catch (JSONException je) {
            je.printStackTrace();
        }

        mLeScanner.stopLEScan();
    }

    public void connectStick(JSONObject reqObj) {
        if (!isPoweredOn()) {
            sendReasonForFailedCall();
            return;
        }

        try {
            if(reqObj.has(Constants.kParams)) {
                JSONObject parameters = reqObj.getJSONObject(Constants.kParams);

                if(parameters.has(Constants.kPeripheralUUID)) {
                    mAddress = parameters.getString(Constants.kPeripheralUUID);

                    for (BluetoothDevice bdevice : mAvailableDevices) {
                        if (bdevice.getAddress().equals(mAddress)) {
                            mService.connectLEDevice(bdevice);
                        }
                    }
                } else {
                    invalidParameters(Constants.kConnect);
                    return;
                }
            } else {
                invalidParameters(Constants.kConnect);
                return;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void disconnectStick(JSONObject reqObj) {
        // handle disconnect event
        if (!isPoweredOn()) {
            sendReasonForFailedCall();
            return;
        }

        try {
            if(reqObj.has(Constants.kParams)) {
                JSONObject jObj = reqObj.getJSONObject(Constants.kParams);

                if(jObj.has(Constants.kPeripheralUUID)) {
                    mAddress = jObj.getString(Constants.kPeripheralUUID);
                } else {
                    invalidParameters(Constants.kDisconnect);

                    return;
                }
            } else {
                invalidParameters(Constants.kDisconnect);
                return;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        for (BluetoothGatt gatt : mConnectedDevices) {
            BluetoothDevice device = gatt.getDevice();

            if (device.getAddress().equals(mAddress)) {
                mService.disconnectLEDevice(gatt);
            }
        }
    }

    public void getConnectionState(JSONObject reqObj) {
        // need to comnplare with connected device address
        String stateOfPerpheral = null;

        for (BluetoothGatt gatt : mConnectedDevices) {
            stateOfPerpheral = Util.peripheralStateStringFromPeripheralState(gatt);
        }

        if (stateOfPerpheral != null) {
            JSONObject respObj = new JSONObject();

            try {
                respObj.put(Constants.kResult, Constants.kCentralState);
                respObj.put(Constants.kStateField, stateOfPerpheral);

                sendResponse(respObj);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    public void getConnectedPeripherals(JSONObject reqObj) {
        int index = 0;
        JSONObject respObj = new JSONObject();
        JSONObject peripheralsObj = new JSONObject();
        JSONObject peripheralObj = new JSONObject();

        for (BluetoothGatt gatt : mConnectedDevices) {
            String peripheralState = Util.peripheralStateStringFromPeripheralState(gatt);
            String peripheralUUIDString = Util.peripheralUUIDStringFromPeripheral(gatt.getDevice()).toUpperCase(Locale.getDefault());

            try {
                peripheralObj.put(Constants.kStateField, peripheralState);
                peripheralObj.put(Constants.kPeripheralUUID,peripheralUUIDString);
                peripheralsObj.put("" + index, peripheralObj);
                index++;
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        try {
            respObj.put(Constants.kPeripherals, peripheralsObj);
            sendResponse(respObj);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void getServices(JSONObject reqObj) {
        String peripheralAddress = null;

        try {
            if(reqObj.has(Constants.kParams)) {
                JSONObject jObj = reqObj.getJSONObject(Constants.kParams);

                if(jObj.has(Constants.kPeripheralUUID)) {
                    peripheralAddress = jObj.getString(Constants.kPeripheralUUID);
                    BluetoothGatt gatt = Util.peripheralIn(mConnectedDevices, peripheralAddress);

                    if (gatt == null) {
                        sendPeripheralNotFoundErrorMessage();
                        return;
                    }

                    mService.getServices(gatt);
                } else {
                    invalidParameters(Constants.kGetServices);
                    return;
                }
            } else {
                invalidParameters(Constants.kGetServices);
                return;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void getIncludedServices(JSONObject reqObj) {
        try {
            if(reqObj.has(Constants.kParams)) {
                JSONObject parameters = reqObj.getJSONObject(Constants.kParams);

                if(parameters.has(Constants.kServiceUUID)) {
                    String serviceUUIDString = parameters.getString(Constants.kServiceUUID).toUpperCase(Locale.getDefault());
                    UUID serviceUUID = UUID.fromString(serviceUUIDString);
                    HashMap<BluetoothGatt, BluetoothGattService> requestedPeripheralAndService = Util.serviceIn(mConnectedDevices, serviceUUID);
                    Set<BluetoothGatt> keySet = requestedPeripheralAndService.keySet();
                    BluetoothGatt bGatt = null;

                    for (BluetoothGatt gatt : keySet) {
                        bGatt = gatt;
                    }

                    if (bGatt == null) {
                        sendPeripheralNotFoundErrorMessage();
                        return;
                    }

                    BluetoothGattService service = requestedPeripheralAndService.get(bGatt);

                    if (service == null) {
                        sendServiceNotFoundErrorMessage();
                        return;
                    }
                } else {
                    invalidParameters(Constants.kGetIncludedServices);
                    return;
                }
            } else {
                invalidParameters(Constants.kGetIncludedServices);
                return;
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void getCharacteristics(JSONObject reqObj) {
        JSONObject reqParameters;

        try {
            reqParameters = reqObj.getJSONObject(Constants.kParams);
            String serviceUUIDString = reqParameters.getString(Constants.kServiceUUID).toUpperCase(Locale.getDefault());
            sCBUUID = serviceUUIDString;
            UUID serviceUUID = UUID.fromString(Util.ConvertUUID_16bitInto128bit(serviceUUIDString));
            HashMap<BluetoothGatt, BluetoothGattService> requestedPeripheralAndService = Util.serviceIn(mConnectedDevices, serviceUUID);
            Set<BluetoothGatt> keySet = requestedPeripheralAndService.keySet();
            BluetoothGatt bGatt = null;

            for (BluetoothGatt gatt : keySet) {
                bGatt = gatt;
            }

            if (bGatt == null) {
                sendPeripheralNotFoundErrorMessage();
                return;
            }

            BluetoothGattService requestedService = requestedPeripheralAndService.get(bGatt);

            if (requestedService == null) {
                sendServiceNotFoundErrorMessage();
                return;
            }

            List<JSONObject> listOfCharacteristics = Util.listOfJsonCharacteristicsFrom(requestedService.getCharacteristics());
            JSONObject parameters = new JSONObject();
            JSONObject response = new JSONObject();
            parameters.put(Constants.kPeripheralUUID, bGatt.getDevice().getAddress());
            parameters.put(Constants.kServiceUUID, serviceUUIDString);
            parameters.put(Constants.kCharacteristics, new JSONArray(listOfCharacteristics));
            response.put(Constants.kResult, Constants.kGetCharacteristics);
            response.put(Constants.kParams, parameters);

            sendResponse(response);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void getDescriptors(JSONObject reqObj) {
        try {
            if(reqObj.has(Constants.kParams)) {
                JSONObject reqparameters = reqObj.getJSONObject(Constants.kParams);

                if(reqparameters.has(Constants.kCharacteristicUUID)) {
                    String characteristicsUUIDString = reqparameters.getString(Constants.kCharacteristicUUID).toUpperCase(Locale.getDefault());
                    UUID characteristicsUUID = UUID.fromString(Util.ConvertUUID_16bitInto128bit(characteristicsUUIDString));
                    HashMap<BluetoothGatt, BluetoothGattCharacteristic> peripheralAndCharacteristic = Util.characteristicIn(mConnectedDevices, characteristicsUUID);
                    Set<BluetoothGatt> keySet = peripheralAndCharacteristic.keySet();
                    BluetoothGatt gatt = null;

                    for (BluetoothGatt bGatt : keySet) {
                        gatt = bGatt;
                    }

                    if (gatt == null) {
                        sendPeripheralNotFoundErrorMessage();
                        return;
                    }

                    BluetoothGattCharacteristic characteristic = peripheralAndCharacteristic.get(gatt);

                    if (characteristic == null) {
                        sendCharacteristicNotFoundErrorMessage();
                        return;
                    }
                    List<JSONObject> descriptorArray = Util.listOfJsonDescriptorsFrom(characteristic.getDescriptors());
                    String peripheralUUIDString = gatt.getDevice().getAddress();
                    String serviceUUIDString = sCBUUID.toUpperCase(Locale.getDefault());
                    JSONObject parameters = new JSONObject();
                    JSONObject response = new JSONObject();
                    parameters.put(Constants.kCharacteristicUUID, characteristicsUUIDString);
                    parameters.put(Constants.kPeripheralUUID, peripheralUUIDString);
                    parameters.put(Constants.kServiceUUID, serviceUUIDString);
                    parameters.put(Constants.kDescriptors, new JSONArray(descriptorArray));
                    response.put(Constants.kResult, Constants.kGetDescriptors);
                    response.put(Constants.kParams, parameters);

                    sendResponse(response);
                } else {
                    invalidParameters(Constants.kGetDescriptors);
                    return;
                }
            } else {
                invalidParameters(Constants.kGetDescriptors);
                return;
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void getCharacteristicValue(JSONObject reqObj) {
        try {
            if(reqObj.has(Constants.kParams)) {
                JSONObject jObj = reqObj.getJSONObject(Constants.kParams);

                if(jObj.has(Constants.kCharacteristicUUID)) {
                    String uuidString =  Util.ConvertUUID_16bitInto128bit(jObj.getString(Constants.kCharacteristicUUID).toUpperCase(Locale.getDefault()));
                    UUID characteristicUUID = UUID.fromString(uuidString);
                    HashMap<BluetoothGatt, BluetoothGattCharacteristic> characteristics = Util.characteristicIn(mConnectedDevices, characteristicUUID);
                    Set<BluetoothGatt> keySet = characteristics.keySet();

                    for (BluetoothGatt bGatt : keySet) {
                        BluetoothGattCharacteristic characteristic = characteristics.get(bGatt);
                        if (characteristic == null) {
                            sendCharacteristicNotFoundErrorMessage();
                            return;
                        }
                      mService.readCharacteristicValue(bGatt, characteristic);
                    }
                } else {
                    invalidParameters(Constants.kGetCharacteristicValue);
                    return;
                }
            } else {
                invalidParameters(Constants.kGetCharacteristicValue);
                return;
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void writeCharacteristicValue(JSONObject reqObj) {
        try {
            byte[] writeData;

            if(reqObj.has(Constants.kParams)) {
                JSONObject jObj = reqObj.getJSONObject(Constants.kParams);

                if(jObj.has(Constants.kCharacteristicUUID) && jObj.has(Constants.kValue)) {
                    String uuidString = Util.ConvertUUID_16bitInto128bit(jObj.getString(Constants.kCharacteristicUUID).toUpperCase(Locale.getDefault()));
                    writeData = Util.hexStringToByteArray(jObj.getString(Constants.kValue));
                    UUID characteristicUUID = UUID.fromString(uuidString);
                    HashMap<BluetoothGatt, BluetoothGattCharacteristic> characteristics = Util.characteristicIn(mConnectedDevices, characteristicUUID);
                    Set<BluetoothGatt> keySet = characteristics.keySet();

                    for (BluetoothGatt bGatt : keySet) {
                        BluetoothGattCharacteristic characteristic = characteristics.get(bGatt);

                        if (characteristic == null) {
                            sendCharacteristicNotFoundErrorMessage();
                            return;
                        }

                        String writeType = null;

                        if(jObj.has(Constants.kWriteType)) {
                           writeType = Util.writeTypeForCharacteristicGiven(jObj.getString(Constants.kWriteType));
                        }

                        mService.writeCharacteristicValue(bGatt, characteristic, writeType, writeData);
                    }
                } else {
                    invalidParameters(Constants.kWriteCharacteristicValue);
                    return;
                }
            } else {
                invalidParameters(Constants.kWriteCharacteristicValue);
                return;
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void setValueNotification(JSONObject reqObj) {
        if (mNotifications)
            mNotifications = false;
        else
            mNotifications = true;

        try {
            if(reqObj.has(Constants.kParams)) {
                JSONObject jObj = reqObj.getJSONObject(Constants.kParams);

                if(jObj.has(Constants.kCharacteristicUUID) && jObj.has(Constants.kValue)) {
                    String uuidString = Util.ConvertUUID_16bitInto128bit(jObj.getString(Constants.kCharacteristicUUID).toUpperCase(Locale.getDefault()));
                    String subscribe = jObj.getString(Constants.kValue);
                    Boolean subscribeBOOL;

                    if (subscribe.equals("true"))
                        subscribeBOOL = true;
                    else
                        subscribeBOOL = false;

                    UUID characteristicUUID = UUID.fromString(uuidString);
                    HashMap<BluetoothGatt, BluetoothGattCharacteristic> characteristics = Util.characteristicIn(mConnectedDevices, characteristicUUID);
                    Set<BluetoothGatt> keySet = characteristics.keySet();

                    for (BluetoothGatt bGatt : keySet) {
                        BluetoothGattCharacteristic characteristic = characteristics.get(bGatt);

                        if (characteristic == null) {
                            sendCharacteristicNotFoundErrorMessage();
                            return;
                        }

                        mService.setValueNotification(bGatt, characteristic, subscribeBOOL);
                    }
                } else {
                    invalidParameters(Constants.kSetValueNotification);
                    return;
                }
            } else {
                invalidParameters(Constants.kSetValueNotification);
                return;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void getDescriptorValue(JSONObject reqObj) {
        try {
            if(reqObj.has(Constants.kParams)) {
                JSONObject jObj = reqObj.getJSONObject(Constants.kParams);

                if(jObj.has(Constants.kDescriptorUUID)) {
                    String uuidString = Util.ConvertUUID_16bitInto128bit(jObj.getString(Constants.kDescriptorUUID).toUpperCase(Locale.getDefault()));
                    UUID descriptorUUID = UUID.fromString(uuidString);
                    HashMap<BluetoothGatt, BluetoothGattDescriptor> descriptors = Util.descriptorIn(mConnectedDevices, descriptorUUID);
                    Set<BluetoothGatt> keySet = descriptors.keySet();

                    for (BluetoothGatt gatt : keySet) {
                        BluetoothGattDescriptor desc = descriptors.get(keySet);

                        if (desc == null) {
                            sendDescriptorNotFoundErrorMessage();
                            return;
                        }

                        mService.readDescriptorValue(gatt, desc);
                    }
                } else {
                    invalidParameters(Constants.kGetDescriptorValue);
                    return;
                }
            } else {
                invalidParameters(Constants.kGetDescriptorValue);
                return;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void getPeripheralState(JSONObject reqObj) {
        try {
            if(reqObj.has(Constants.kParams)) {
                JSONObject jObj = reqObj.getJSONObject(Constants.kParams);

                if(jObj.has(Constants.kPeripheralUUID)) {
                    String address = jObj.getString(Constants.kPeripheralUUID);
                    BluetoothGatt gatt = Util.peripheralIn(mConnectedDevices, address);

                    if (gatt == null) {
                        sendPeripheralNotFoundErrorMessage();
                        return;
                    }

                    String peripheralState = Util.peripheralStateStringFromPeripheralState(gatt);
                    JSONObject respObj = new JSONObject();

                    respObj.put(Constants.kStateField, peripheralState);
                    respObj.put(Constants.kError, null);
                    respObj.put(Constants.kResult, Constants.kGetPeripheralState);

                    sendResponse(respObj);
                } else {
                    invalidParameters(Constants.kGetPeripheralState);
                    return;
                }
            } else {
                invalidParameters(Constants.kGetPeripheralState);
                return;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void getRSSI(JSONObject reqObj) {
        try {
            if(reqObj.has(Constants.kParams)) {
                JSONObject jObj = reqObj.getJSONObject(Constants.kParams);

                if(jObj.has(Constants.kPeripheralUUID)) {
                    String address = jObj.getString(Constants.kPeripheralUUID);
                    BluetoothGatt gatt = Util.peripheralIn(mConnectedDevices, address);

                    if (gatt == null) {
                        sendPeripheralNotFoundErrorMessage();
                        return;
                    }

                    mService.readRemoteRssi(gatt);
                } else {
                    invalidParameters(Constants.kGetRSSI);
                    return;
                }
            } else {
                invalidParameters(Constants.kGetRSSI);
                return;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public boolean isPoweredOn() {
        // checking whether bluetooth is connected or not
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter == null) {
            // bluetooth not supported
        }
        // if bluetooth is not enable then enable the bluetooth
        if (mBluetoothAdapter.isEnabled())
            return true;
        else
            return false;
    }

    public void sendReasonForFailedCall() {
        JSONObject errorObj = new JSONObject();
        JSONObject errorResponse = new JSONObject();

        try {
            String errorMessage = Util.centralStateStringFromCentralState(mBluetoothAdapter.getState());

            errorObj.put(Constants.kCode, Constants.kError32001);
            errorObj.put(Constants.kMessageField, errorMessage);
            errorResponse.put(Constants.kError, errorObj);

            sendResponse(errorResponse);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void sendPeripheralNotFoundErrorMessage() {
        JSONObject errorObj = new JSONObject();
        JSONObject errorResponse = new JSONObject();

        try {
            errorObj.put(Constants.kCode, Constants.kError32001);
            errorResponse.put(Constants.kError, errorObj);

            sendResponse(errorResponse);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void sendServiceNotFoundErrorMessage() {
        JSONObject errorObj = new JSONObject();
        JSONObject errorResponse = new JSONObject();

        try {
            errorObj.put(Constants.kCode, Constants.kError32002);
            errorResponse.put(Constants.kError, errorObj);

            sendResponse(errorResponse);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void sendCharacteristicNotFoundErrorMessage() {
        JSONObject errorObj = new JSONObject();
        JSONObject errorResponse = new JSONObject();

        try {
            errorObj.put(Constants.kCode, Constants.kError32003);
            errorResponse.put(Constants.kError, errorObj);

            sendResponse(errorResponse);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void sendDescriptorNotFoundErrorMessage() {
        JSONObject errorObj = new JSONObject();
        JSONObject errorResponse = new JSONObject();

        try {
            errorObj.put(Constants.kCode, Constants.kError32001);
            errorResponse.put(Constants.kError, errorObj);

            sendResponse(errorResponse);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void sendNoServiceSpecified() {
        try {
            JSONObject response = new JSONObject();
            JSONObject errorResponse = new JSONObject();
            errorResponse.put(Constants.kCode, Constants.kError32006);
            response.put(Constants.kError, errorResponse);
            sendResponse(response);
        } catch (JSONException je) {
            je.printStackTrace();
        }
    }

    public void sendNoPeripheralsSpecified() {
        try {
            JSONObject response = new JSONObject();
            JSONObject errorResponse = new JSONObject();
            errorResponse.put(Constants.kCode, Constants.kError32007);
            response.put(Constants.kError, errorResponse);
            sendResponse(response);
        } catch (JSONException je) {
            je.printStackTrace();
        }
    }

    public void invalidRequest() {
        try {
            JSONObject errorObj = new JSONObject();
            errorObj.put(Constants.kCode, Constants.kInvalidRequest);
            JSONObject jsonData = new JSONObject();
            jsonData.put(Constants.kError, errorObj);
            sendResponse(jsonData);
        } catch (JSONException je) {
            je.printStackTrace();
        }
    }

    public void invalidParameters(String method) {
        JSONObject errorObj = new JSONObject();
        JSONObject errorResponse = new JSONObject();

        try {
            errorResponse.put(Constants.kResult, method);
            errorObj.put(Constants.kCode, Constants.kInvalidParams);
            errorResponse.put(Constants.kError, errorObj);

            sendResponse(errorResponse);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }


    public List<JSONObject> listOFResponseAndRequests() {
        if(previousRequests != null && previousRequests.size() >0)
            return previousRequests;
        else return null;
    }

    private static List<JSONObject> previousRequests;
    /**
     * Handles logging of Requests and Responses including releasing the oldest
     * 25% of the Requests/Respnoses if/when the buffer overflows
     *
     * @param input  Response/Request
     */
    public static void handleLoggingRequestAndResponse(JSONObject input) {

        if(previousRequests != null)
        {
            if(previousRequests.size() > MAX_NUMBER_OF_REQUESTS)
            {
                for(int i=0;i<MAX_NUMBER_OF_REQUESTS/4.0;i++)
                {
                    previousRequests.remove(i);
                }
            }
            previousRequests.add(convertToHumanReadableFormat(input));
        }
    }

    public static JSONObject convertToHumanReadableFormat(JSONObject input) {
        JSONObject outputRespnose = new JSONObject();
        String humanReadableKey;
        @SuppressWarnings("unchecked")
        Iterator<String> allKeys = input.keys();
        List<String> keys = new ArrayList<String>();

        while (allKeys.hasNext()) {
            keys.add(allKeys.next());
        }

        for (String key : keys) {
            humanReadableKey = Util.humanReadableFormatFromHex(key);

            try {
                Object inputobj = input.get(key);

                if (inputobj instanceof String) {
                    String value = input.getString(key);
                    String humanReadableValue = Util.humanReadableFormatFromHex(value);

                    outputRespnose.put(humanReadableKey, humanReadableValue);
                } else if (inputobj instanceof JSONObject) {
                    JSONObject value = (JSONObject) input.get(key);
                    JSONObject humanReadableValue = convertToHumanReadableFormat(value);

                    outputRespnose.put(humanReadableKey, humanReadableValue);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return outputRespnose;
    }

    public BroadcastReceiver bReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);

                JSONObject parameters = new JSONObject();
                JSONObject response = new JSONObject();
                String stateString = Util.centralStateStringFromCentralState(state);

                if (!stateString.equals("") && stateString != null) {
                    if(BTConnect) {
                        BTConnect = false;
                    Log.v("action state changed", stateString);
                        try {
                            parameters.put(Constants.kState, stateString);

                            response.put(Constants.kParams, parameters);
                            response.put(Constants.kResult, Constants.kCentralState);

                            sendResponse(response);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    } else {
                        BTConnect = true;
                    }
                }
            } else if(action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);

                if(state == BluetoothDevice.BOND_BONDING) {
                    Log.v("bonding","*******state bonding");
                } else if(state == BluetoothDevice.BOND_BONDED) {
                    Log.v("bondes","******state bonded");
                } else if(state == BluetoothDevice.BOND_NONE) {
                    Log.v("none","***** no bonding");
                }

            }
        }

    };
}
