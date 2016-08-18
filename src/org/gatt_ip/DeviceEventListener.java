package org.gatt_ip;

import android.bluetooth.le.ScanRecord;

import java.util.List;

/**
 * Created by vensi on 9/25/15.
 */
public interface DeviceEventListener {
    void onDeviceFound(String deviceIdentifier, String deviceName, int deviceSignal, List<String> serviceUUIDs, ScanRecord record, byte[] deviceData);
    void onDeviceConnection(String deviceName, String deviceIdentifier);
    void onDeviceDisconnection(String deviceName, String deviceIdentifier);
    void onDeviceConnectionFailure(String deviceName, String deviceIdentifier, int status);
    void onDeviceUnexpectedlyDisconnection(String deviceName, String deviceIdentifier, int status);
    void onDeviceServices(String deviceIdentifier, List services, int status);
    void onDeviceAttributes(String deviceIdentifier, String serviceIdentifier, List characteristics);
    void onDevcieAttributeRead(String deviceIdentifier, String serviceIdentifier, String attribIdentifier, byte[] attribValue, int status);
    void onDeviceAttributeWrite(String deviceIdentifier, String serviceIdentifier, String attribIdentifier, int status);
    void onDeviceAttributeChanged(String deviceIdentifier, String serviceIdentifier, String attribIdentifier, byte[] attribValue, int status);
    void onDeviceAttributeDescriptors(String deviceIdentifier, String serviceIdentifier, String attribIdentifier, List attribDescriptors);
    void onDeviceAttributeDescriptorRead(String deviceIdentifier, String serviceIdentifier, String attribIdentifier, String attribDescriptorIdentifier, byte[] attributeDescriptorValue, int status);
    void onDeviceAttributeDescriptoWrite(String deviceIdentifier, String serviceIdentifier, String attribIdentifier, String attribDescriptorIdentifier, int status);
    void onDeviceSignal(String deviceIdentifier, String deviceName, int signal, int status);
    void onError(Enum error);
    void sendResponseForWriteTypeNoReponse();
    void noConnectedDevices();
}
