package org.gatt_ip;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.UUID;

import org.json.JSONException;
import org.json.JSONObject;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;

public class Util {
    
    public static String peripheralStateStringFromPeripheralState(BluetoothGatt gatt)
    {
        switch(gatt.getConnectionState(gatt.getDevice()))
        {
            case BluetoothProfile.STATE_CONNECTED :
                return Constants.kConnected;
            case BluetoothProfile.STATE_CONNECTING :
                return Constants.kConnecting;
            case BluetoothProfile.STATE_DISCONNECTED :
                return Constants.kDisconnect;
            default :
                return null;
        }
    }
    
    public static String peripheralUUIDStringFromPeripheral(BluetoothDevice device)
    {
        return device.getAddress();
    }
    
    //get the bluetooth device from list of devcie for a specific address
    public static BluetoothGatt peripheralIn(ArrayList<BluetoothGatt> peripheralCollection, String deviceAddress)
    {
        BluetoothGatt gatt = null;
        for(BluetoothGatt bGatt : peripheralCollection)
        {
            if(bGatt.getDevice().getAddress().equals(deviceAddress))
            {
                gatt = bGatt;
                break;
            }
        }
        return gatt;
    }
    
    public static HashMap<BluetoothGatt, BluetoothGattService> serviceIn (ArrayList<BluetoothGatt> peripheralCollection, UUID serviceUUID)
    {
        HashMap<BluetoothGatt, BluetoothGattService> servicesList = null;
        for(BluetoothGatt bGatt : peripheralCollection)
        {
            List<BluetoothGattService> gattservices = bGatt.getServices();
            ListIterator<BluetoothGattService> iterator = null;
            iterator = gattservices.listIterator();
            while (iterator.hasNext()) {
                BluetoothGattService service = iterator.next();
                if(service.getUuid().equals(serviceUUID))
                {
                    servicesList = new HashMap<BluetoothGatt, BluetoothGattService>();
                    servicesList.put(bGatt, service);
                }
            }
        }
        return servicesList;
    }
    
    public static HashMap<BluetoothGatt, BluetoothGattCharacteristic> characteristicIn (ArrayList<BluetoothGatt> peripheralCollection , UUID characteristicUUID)
    {
        HashMap<BluetoothGatt, BluetoothGattCharacteristic> characteristicsList = null;
        for(BluetoothGatt bGatt : peripheralCollection)
        {
            List<BluetoothGattService> gattservices = bGatt.getServices();
            ListIterator<BluetoothGattService> iterator = null;
            iterator = gattservices.listIterator();
            while (iterator.hasNext()) {
                BluetoothGattService service = iterator.next();
                List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                ListIterator< BluetoothGattCharacteristic> itr;
                itr = characteristics.listIterator();
                while (itr.hasNext()) {
                    BluetoothGattCharacteristic characteristic = itr.next();
                    if(characteristic.getUuid().equals(characteristicUUID))
                    {
                        characteristicsList = new HashMap<BluetoothGatt, BluetoothGattCharacteristic>();
                        characteristicsList.put(bGatt, characteristic);
                    }
                }
            }
        }
        return characteristicsList;
    }
    
    public static HashMap<BluetoothGatt, BluetoothGattDescriptor> descriptorIn (ArrayList<BluetoothGatt> peripheralCollection ,UUID descriptorUUID)
    {
        HashMap<BluetoothGatt, BluetoothGattDescriptor> descriptorsList = null;
        for(BluetoothGatt bGatt : peripheralCollection)
        {
            List<BluetoothGattService> gattservices = bGatt.getServices();
            ListIterator<BluetoothGattService> iterator = null;
            iterator = gattservices.listIterator();
            while (iterator.hasNext()) {
                BluetoothGattService service = iterator.next();
                List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                ListIterator< BluetoothGattCharacteristic> itr;
                itr = characteristics.listIterator();
                while (itr.hasNext()) {
                    BluetoothGattCharacteristic characteristic = itr.next();
                    List<BluetoothGattDescriptor> descriptors = characteristic.getDescriptors();
                    ListIterator<BluetoothGattDescriptor> listitr = null;
                    listitr = descriptors.listIterator();
                    while (listitr.hasNext()) {
                        BluetoothGattDescriptor descriptor = listitr.next();
                        if(descriptor.getUuid().equals(descriptorUUID))
                        {
                            descriptorsList = new HashMap<BluetoothGatt, BluetoothGattDescriptor>();
                            descriptorsList.put(bGatt, descriptor);
                        }
                    }
                }
            }
        }
        return descriptorsList;
    }
    
    public static List<JSONObject> listOfJsonServicesFrom(List<BluetoothGattService> services)
    {
        List<JSONObject> jsonList = new ArrayList<JSONObject>();
        ListIterator<BluetoothGattService> iterator = null;
        iterator = services.listIterator();
        while (iterator.hasNext()) {
            JSONObject jsonObj = new JSONObject();
            BluetoothGattService service = iterator.next();
            int isPrimary;
            if(service.getType() == BluetoothGattService.SERVICE_TYPE_PRIMARY)
                isPrimary = 1;
            else
                isPrimary = 0;
            String serviceUUIDString = service.getUuid().toString().toUpperCase(Locale.getDefault());
            try {
                jsonObj.put(Constants.kServiceUUID,serviceUUIDString);
                jsonObj.put(Constants.kIsPrimaryKey, isPrimary);
                jsonList.add(jsonObj);
                
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return jsonList;
    }
    
    public static List<JSONObject> listOfJsonCharacteristicsFrom (List<BluetoothGattCharacteristic> characteristics)
    {
        List<JSONObject> jsonList = new ArrayList<JSONObject>();
        ListIterator<BluetoothGattCharacteristic> iterator = null;
        iterator = characteristics.listIterator();
        while (iterator.hasNext()) {
            JSONObject jsonObj = new JSONObject();
            BluetoothGattCharacteristic characteristic = iterator.next();
            String characterisUUIDString = characteristic.getUuid().toString().toUpperCase(Locale.getDefault());
            String characteristcProperty = ""+characteristic.getProperties();
            if(characteristcProperty.equals("34"))
                characteristcProperty = "18";
            try {
                jsonObj.put(Constants.kCharacteristicUUID,characterisUUIDString);
                jsonObj.put(Constants.kIsNotifying, 0);
                jsonObj.put(Constants.kProperties, characteristcProperty);
                jsonObj.put(Constants.kValue, "");
                jsonList.add(jsonObj);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return jsonList;
    }
    
    public static List<JSONObject> listOfJsonDescriptorsFrom(List<BluetoothGattDescriptor> descriptors)
    {
        List<JSONObject> jsonList = new ArrayList<JSONObject>();
        ListIterator<BluetoothGattDescriptor> iterator = null;
        iterator = descriptors.listIterator();
        while (iterator.hasNext()) {
            JSONObject jsonObj = new JSONObject();
            BluetoothGattDescriptor descriptor = iterator.next();
            try {
                jsonObj.put(Constants.kDescriptorUUID,descriptor.getUuid().toString());
                jsonList.add(jsonObj);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return jsonList;
    }
    public static String centralStateStringFromCentralState(int centralState)
    {
        switch(centralState)
        {
            case BluetoothAdapter.STATE_OFF :
                return Constants.kPoweredOff;
            case BluetoothAdapter.STATE_ON :
                return Constants.kPoweredOn;
            default :
                return "";
        }
    }
    
    public static String byteArrayToHex(byte[] data)
    {
        final StringBuilder hexStr = new StringBuilder(data.length);
        if (data != null && data.length > 0) {
            for (byte byteChar : data)
                hexStr.append(String.format("%X", byteChar));
        }
        return new String(hexStr);
    }
    
    public static byte[] hexStringToByteArray(String hex)
    {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i+1), 16));
        }
        return data;
    }
    
    public static String humanReadableFormatFromHex(String hexString)
    {
        HashMap<String, String> methods = new HashMap<String, String>();
        methods.put("aa", "Configure");
        methods.put("ab", "ScanForPeripherals");
        methods.put("ac", "StopScanning");
        methods.put("ad", "Connect");
        methods.put("ae", "Disconnect");
        methods.put("af", "CentralState");
        methods.put("ag", "GetConnectedPeripherals");
        methods.put("ah", "GetPerhipheralsWithServices");
        methods.put("ai", "GetPerhipheralsWithIdentifiers");
        methods.put("ak", "GetServices");
        methods.put("al", "GetIncludedServices");
        methods.put("am", "GetCharacteristics");
        methods.put("an", "GetDescriptors");
        methods.put("ao", "GetCharacteristicValue");
        methods.put("ap", "GetDescriptorValue");
        methods.put("aq", "WriteCharacteristicValue");
        methods.put("ar", "WriteDescriptorValue");
        methods.put("as", "SetValueNotification");
        methods.put("at", "GetPeripheralState");
        methods.put("au", "GetRSSI");
        methods.put("av", "InvalidatedServices");
        methods.put("aw", "peripheralNameUpdate");
        
        HashMap<String, String> keys = new HashMap<String, String>();
        keys.put("ba", "centralUUID");
        keys.put("bb", "peripheralUUID");
        keys.put("bc", "PeripheralName");
        keys.put("bd", "PeripheralUUIDs");
        keys.put("be", "ServiceUUID");
        keys.put("bf", "ServiceUUIDs");
        keys.put("bg", "peripherals");
        keys.put("bh", "IncludedServiceUUIDs");
        keys.put("bi", "CharacteristicUUID");
        keys.put("bj", "CharacteristicUUIDs");
        keys.put("bk", "DescriptorUUID");
        keys.put("bl", "Services");
        keys.put("bm", "Characteristics");
        keys.put("bn", "Descriptors");
        keys.put("bo", "Properties");
        keys.put("bp", "Value");
        keys.put("bq", "State");
        keys.put("br", "StateInfo");
        keys.put("bs", "StateField");
        keys.put("bt", "WriteType");
        keys.put("bu", "RSSIkey");
        keys.put("bv", "IsPrimaryKey");
        keys.put("bw", "IsBroadcasted");
        keys.put("bx", "IsNotifying");
        keys.put("by", "ShowPowerAlert");
        keys.put("bz", "IdentifierKey");
        keys.put("b0", "ScanOptionAllowDuplicatesKey");
        keys.put("b1", "ScanOptionSolicitedServiceUUIDs");
        keys.put("b2", "AdvertisementDataKey");
        keys.put("b3", "CBAdvertisementDataManufacturerDataKey");
        keys.put("b4", "CBAdvertisementDataServiceUUIDsKey");
        keys.put("b5", "CBAdvertisementDataServiceDataKey");
        keys.put("b6", "CBAdvertisementDataOverflowServiceUUIDsKey");
        keys.put("b7", "CBAdvertisementDataSolicitedServiceUUIDsKey");
        keys.put("b8", "CBAdvertisementDataIsConnectable");
        keys.put("b9", "CBAdvertisementDataTxPowerLevel");
        keys.put("da", "CBCentralManagerRestoredStatePeripheralsKey");
        keys.put("db", "CBCentralManagerRestoredStateScanServicesKey");
        
        HashMap<String, String> values = new HashMap<String, String>();
        values.put("cc", "WriteWithResponse");
        values.put("cd", "WriteWithoutResponse");
        values.put("ce", "NotifyOnConnection");
        values.put("cf", "NotifyOnDisconnection");
        values.put("cg", "NotifyOnNotification");
        values.put("ch", "Disconnected");
        values.put("ci", "Connecting");
        values.put("cj", "Connected");
        values.put("ck", "Unknown");
        values.put("cl", "Resetting");
        values.put("cm", "Unsupported");
        values.put("cn", "Unauthorized");
        values.put("co", "PoweredOff");
        values.put("cp", "PoweredOn");
        
        if(methods.get(hexString) != null)
        {
            return methods.get(hexString);
        } else if(keys.get(hexString) != null)
        {
            return keys.get(hexString);
        } else if(values.get(hexString) != null)
        {
            return values.get(hexString);
        } else if(hexString != null)
        {
            return hexString;
        } else
            return "";			
        
    }
    
}


