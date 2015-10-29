package org.gatt_ip.util;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;

import org.gatt_ip.Constants;
import org.gatt_ip.InterfaceService;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.UUID;
import java.math.BigInteger;
import java.math.BigDecimal;

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
    public static BluetoothGatt peripheralIn(List<BluetoothGatt> peripheralCollection, String deviceAddress)
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
    
    public static HashMap<BluetoothGatt, BluetoothGattService> serviceIn (List<BluetoothGatt> peripheralCollection, UUID serviceUUID)
    {
        HashMap<BluetoothGatt, BluetoothGattService> servicesList = new HashMap<BluetoothGatt, BluetoothGattService>();
        for(BluetoothGatt bGatt : peripheralCollection)
        {
            List<BluetoothGattService> gattservices = bGatt.getServices();
            ListIterator<BluetoothGattService> iterator = null;
            iterator = gattservices.listIterator();
            while (iterator.hasNext()) {
                BluetoothGattService service = iterator.next();
                if(service.getUuid().equals(serviceUUID))
                {
                    servicesList.put(bGatt, service);
                }
            }
        }
        return servicesList;
    }
    
    public static HashMap<BluetoothGatt, BluetoothGattCharacteristic> characteristicIn (List<BluetoothGatt> peripheralCollection , UUID characteristicUUID)
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
    
    public static HashMap<BluetoothGatt, BluetoothGattDescriptor> descriptorIn (List<BluetoothGatt> peripheralCollection ,UUID descriptorUUID)
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
                jsonObj.put(Constants.kServiceUUID,ConvertUUID_128bitInto16bit(serviceUUIDString));
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
                jsonObj.put(Constants.kCharacteristicUUID,ConvertUUID_128bitInto16bit(characterisUUIDString));
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
            String descriptorUUIDString = descriptor.getUuid().toString().toUpperCase(Locale.getDefault());
            try {
                jsonObj.put(Constants.kDescriptorUUID,ConvertUUID_128bitInto16bit(descriptorUUIDString));
                jsonList.add(jsonObj);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return jsonList;
    }

    public static List<String> listOfServiceUUIDStringsFrom(JSONArray listOfServiceUUIDStrings) {

        List<String> listOfServcieUUIDs = new ArrayList<String>();
        for(int i = 0; i < listOfServiceUUIDStrings.length(); i++) {
            try {
                listOfServcieUUIDs.add(listOfServiceUUIDStrings.getString(i));
            } catch (JSONException je) {
                je.printStackTrace();
            }
        }
        return listOfServcieUUIDs;
    }

    public static List<BluetoothGatt> retrieveConnectedPeripheralsWithServices(List<BluetoothGatt> mConnectedDevices, List<String> listOfServcieUUIDStrings) {

        List<BluetoothGatt> peripheralsWithServcies = new ArrayList<>();
        for(int i = 0; i < mConnectedDevices.size(); i++) {
            BluetoothGatt gatt = mConnectedDevices.get(i);
            List<BluetoothGattService> servcies = gatt.getServices();
            for(int j = 0; j < servcies.size(); j++) {
                BluetoothGattService servcie = servcies.get(i);
                for(String servcieUUIDString : listOfServcieUUIDStrings) {
                    if(servcie.getUuid().toString().equals(servcieUUIDString)) {
                        peripheralsWithServcies.add(gatt);
                    }
                }
            }
        }
        return  peripheralsWithServcies;
    }

    public static List<String> listOfPeripheralUUIDStringsFrom(JSONArray listOfPeripherUUIDStrings) {

        List<String> listOfPeripheralUUIDs = new ArrayList<String>();
        for(int i = 0; i < listOfPeripherUUIDStrings.length(); i++) {
            try {
                listOfPeripheralUUIDs.add(listOfPeripherUUIDStrings.getString(i));
            } catch (JSONException je) {
                je.printStackTrace();
            }
        }
        return listOfPeripheralUUIDs;
    }

    public static List<BluetoothGatt> retrievePeripheralsWithIdentifiers(List<BluetoothGatt> mConnectedDevices, List<String> listOfperipheralUUIDStrings) {
        List<BluetoothGatt> peripheralsWithIdentifiers = new ArrayList<>();
        for(int i = 0; i < mConnectedDevices.size(); i++) {
            BluetoothGatt gatt = mConnectedDevices.get(i);
            BluetoothDevice device = gatt.getDevice();
            for(String address : listOfperipheralUUIDStrings) {
                if(address.equals(device.getAddress())) {
                    peripheralsWithIdentifiers.add(gatt);
                }
            }
        }
        return peripheralsWithIdentifiers;
    }

    public static String centralStateStringFromCentralState(InterfaceService.ServiceState state)
    {
        String state_string = "";

        switch(state) {
            case SERVICE_STATE_NONE:
                state_string = Constants.kUnknown;
                break;
            case SERVICE_STATE_INACTIVE:
                state_string = Constants.kPoweredOff;
                break;
            case SERVICE_STATE_ACTIVE:
                state_string = Constants.kPoweredOn;
                break;
            case SERVICE_STATE_UNSUPPORTED:
                state_string = Constants.kUnsupported;
                break;
        }
        return state_string;
    }

    public static String writeTypeForCharacteristicGiven(String writeType)
    {

        if(writeType.equals(Constants.kWriteWithoutResponse))
             return Constants.kWriteWithoutResponse;
        else
            return Constants.kWriteWithResponse;
    }

    public static String byteArrayToHex(byte[] data)
    {
        String hex = "";
        if (data != null && data.length > 0) {
            final StringBuilder hexStr = new StringBuilder(data.length);
            for (byte byteChar : data)
                hexStr.append(String.format("%02X", byteChar));
            hex = new String(hexStr);
        }
        return hex;
    }

    public static byte[] hexStringToByteArray(String hex)
    {
        hex = hex.replaceAll("\\s","");
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i+1), 16));
        }
        return data;
    }

    public static String hexToASCII(String hexValue)
    {
        StringBuilder output = new StringBuilder("");
        for (int i = 0; i < hexValue.length(); i += 2)
        {
            String str = hexValue.substring(i, i + 2);
            output.append((char) Integer.parseInt(str, 16));
        }
        return output.toString();
    }

    public static String ConvertUUID_128bitInto16bit(String UUIDString) {
        //String _UUIDString = UUIDString;
        String reqValueFinal_16bit = "";
        String BluetoothBaseUUID = ("00000000-0000-1000-8000-00805F9B34FB").toString().replaceAll("-", "");
        BigInteger _BluetoothBaseUUIDInIntFormat = new BigInteger(BluetoothBaseUUID ,16);
        String _UUIDString = UUIDString.replaceAll("-", "");
        BigInteger UUIDInIntFormat = new BigInteger(_UUIDString , 16);
        BigInteger ResultToCheckDefinedUUID = UUIDInIntFormat.and(_BluetoothBaseUUIDInIntFormat);
        if(ResultToCheckDefinedUUID.equals(_BluetoothBaseUUIDInIntFormat)) {
            BigDecimal base = new BigDecimal("2");
            BigInteger divisor = base.pow(96).toBigInteger();
            BigInteger reqValue16bit = (UUIDInIntFormat.subtract(_BluetoothBaseUUIDInIntFormat)).divide(divisor);

            reqValueFinal_16bit = reqValue16bit.toString(16);
        } else {
            reqValueFinal_16bit = UUIDString;
        }
        return reqValueFinal_16bit.toUpperCase(Locale.getDefault());
    }

    public static String ConvertUUID_16bitInto128bit(String UUIDString) {
        String _UUIDString = UUIDString;
        String reqValueFinal_128bit = "";

        if(UUIDString.length() == 4) {
            String BluetoothBaseUUID = ("00000000-0000-1000-8000-00805F9B34FB").toString().replaceAll("-", "");
            BigInteger bluetoothBaseInIntegerFormat = new BigInteger(BluetoothBaseUUID, 16);
            BigInteger UUIDInIntFormat = new BigInteger(UUIDString, 16);
            BigDecimal base = new BigDecimal("2");
            BigInteger multiplier = base.pow(96).toBigInteger();
            BigInteger reqValue_128bit = (UUIDInIntFormat.multiply(multiplier)).add(bluetoothBaseInIntegerFormat);
            reqValueFinal_128bit = "0000" + reqValue_128bit.toString(16).toUpperCase(Locale.getDefault());

            String str1 = reqValueFinal_128bit.substring(0, 8);
            String str2 = reqValueFinal_128bit.substring(8, 12);
            String str3 = reqValueFinal_128bit.substring(12, 16);
            String str4 = reqValueFinal_128bit.substring(16, 20);
            String str5 = reqValueFinal_128bit.substring(20);
            reqValueFinal_128bit = str1 + "-" + str2 + "-" + str3 + "-" + str4 + "-" + str5;
        }
        else {
            reqValueFinal_128bit = _UUIDString;
        }
        return reqValueFinal_128bit.toUpperCase(Locale.getDefault());
    }

   public static String humanReadableFormatFromHex(String hexString)
    {
        HashMap<String, String> methods = new HashMap<String, String>();
        methods.put(Constants.kConfigure, "Configure");
        methods.put(Constants.kScanForPeripherals, "ScanForPeripherals");
        methods.put(Constants.kStopScanning, "StopScanning");
        methods.put(Constants.kConnect, "Connect");
        methods.put(Constants.kDisconnect, "Disconnect");
        methods.put(Constants.kCentralState, "CentralState");
        methods.put(Constants.kGetConnectedPeripherals, "GetConnectedPeripherals");
        methods.put(Constants.kGetPerhipheralsWithServices, "GetPerhipheralsWithServices");
        methods.put(Constants.kGetPerhipheralsWithIdentifiers, "GetPerhipheralsWithIdentifiers");
        methods.put(Constants.kGetServices, "GetServices");
        methods.put(Constants.kGetIncludedServices, "GetIncludedServices");
        methods.put(Constants.kGetCharacteristics, "GetCharacteristics");
        methods.put(Constants.kGetDescriptors, "GetDescriptors");
        methods.put(Constants.kGetCharacteristicValue, "GetCharacteristicValue");
        methods.put(Constants.kGetDescriptorValue, "GetDescriptorValue");
        methods.put(Constants.kWriteCharacteristicValue, "WriteCharacteristicValue");
        methods.put(Constants.kWriteDescriptorValue, "WriteDescriptorValue");
        methods.put(Constants.kSetValueNotification, "SetValueNotification");
        methods.put(Constants.kGetPeripheralState, "GetPeripheralState");
        methods.put(Constants.kGetRSSI, "GetRSSI");
        methods.put(Constants.kInvalidatedServices, "InvalidatedServices");
        methods.put(Constants.kPeripheralNameUpdate, "peripheralNameUpdate");

        HashMap<String, String> keys = new HashMap<String, String>();
        keys.put(Constants.kCentralUUID, "centralUUID");
        keys.put(Constants.kPeripheralUUID, "PeripheralUUID");
        keys.put(Constants.kPeripheralName, "PeripheralName");
        keys.put(Constants.kPeripheralUUIDs, "PeripheralUUIDs");
        keys.put(Constants.kServiceUUID, "ServiceUUID");
        keys.put(Constants.kServiceUUIDs, "ServiceUUIDs");
        keys.put(Constants.kPeripherals, "peripherals");
        keys.put(Constants.kIncludedServiceUUIDs, "IncludedServiceUUIDs");
        keys.put(Constants.kCharacteristicUUID, "CharacteristicUUID");
        keys.put(Constants.kCharacteristicUUIDs, "CharacteristicUUIDs");
        keys.put(Constants.kDescriptorUUID, "DescriptorUUID");
        keys.put(Constants.kServices, "Services");
        keys.put(Constants.kCharacteristics, "Characteristics");
        keys.put(Constants.kDescriptors, "Descriptors");
        keys.put(Constants.kProperties, "Properties");
        keys.put(Constants.kValue, "Value");
        keys.put(Constants.kState, "State");
        keys.put(Constants.kStateInfo, "StateInfo");
        keys.put(Constants.kStateField, "StateField");
        keys.put(Constants.kWriteType, "WriteType");
        keys.put(Constants.kRSSIkey, "RSSIkey");
        keys.put(Constants.kIsPrimaryKey, "IsPrimaryKey");
        keys.put(Constants.kIsBroadcasted, "IsBroadcasted");
        keys.put(Constants.kIsNotifying, "IsNotifying");
        keys.put(Constants.kShowPowerAlert, "ShowPowerAlert");
        keys.put(Constants.kIdentifierKey, "IdentifierKey");
        keys.put(Constants.kScanOptionAllowDuplicatesKey, "ScanOptionAllowDuplicatesKey");
        keys.put(Constants.kScanOptionSolicitedServiceUUIDs, "ScanOptionSolicitedServiceUUIDs");
        keys.put(Constants.kAdvertisementDataKey, "AdvertisementDataKey");
        keys.put(Constants.kCBAdvertisementDataManufacturerDataKey, "CBAdvertisementDataManufacturerDataKey");
        keys.put(Constants.kCBAdvertisementDataServiceUUIDsKey, "CBAdvertisementDataServiceUUIDsKey");
        keys.put(Constants.kCBAdvertisementDataServiceDataKey, "CBAdvertisementDataServiceDataKey");
        keys.put(Constants.kCBAdvertisementDataOverflowServiceUUIDsKey, "CBAdvertisementDataOverflowServiceUUIDsKey");
        keys.put(Constants.kCBAdvertisementDataSolicitedServiceUUIDsKey, "CBAdvertisementDataSolicitedServiceUUIDsKey");
        keys.put(Constants.kCBAdvertisementDataIsConnectable, "CBAdvertisementDataIsConnectable");
        keys.put(Constants.kCBAdvertisementDataTxPowerLevel, "CBAdvertisementDataTxPowerLevel");
        keys.put(Constants.kCBCentralManagerRestoredStatePeripheralsKey, "CBCentralManagerRestoredStatePeripheralsKey");
        keys.put(Constants.kCBCentralManagerRestoredStateScanServicesKey, "CBCentralManagerRestoredStateScanServicesKey");
        keys.put(Constants.kPeripheralBtAddress, "BTAddress");
        keys.put(Constants.kRawAdvertisementData, "RawAdvertisingdata");
        keys.put(Constants.kScanRecord, "ScanRecord");

        HashMap<String, String> values = new HashMap<String, String>();
        values.put(Constants.kWriteWithResponse, "WriteWithResponse");
        values.put(Constants.kWriteWithoutResponse, "WriteWithoutResponse");
        values.put(Constants.kNotifyOnConnection, "NotifyOnConnection");
        values.put(Constants.kNotifyOnDisconnection, "NotifyOnDisconnection");
        values.put(Constants.kNotifyOnNotification, "NotifyOnNotification");
        values.put(Constants.kDisconnected, "Disconnected");
        values.put(Constants.kConnecting, "Connecting");
        values.put(Constants.kConnected, "Connected");
        values.put(Constants.kUnknown, "Unknown");
        values.put(Constants.kResetting, "Resetting");
        values.put(Constants.kUnsupported, "Unsupported");
        values.put(Constants.kUnsupported, "Unauthorized");
        values.put(Constants.kPoweredOff, "PoweredOff");
        values.put(Constants.kPoweredOn, "PoweredOn");
        
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


