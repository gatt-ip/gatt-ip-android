package org.gatt_ip;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

/**
 * Created by admin on 4/10/15.
 */
public abstract class BluetoothListener {
    public void connectLEDevice(BluetoothGatt gatt){}
    public void disconnectLEDevice(BluetoothGatt gatt){}
    public void connectionFailed(BluetoothGatt gatt){}
    public void getLEServices(BluetoothGatt gatt, int status){}
    public void readCharacteristicValue(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status){}
    public void writeCharacteristicValue(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status){}
    public void setValueNotification(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic){}
    public void readDescriptorValue(BluetoothGatt gatt,BluetoothGattDescriptor descriptor, int status){}
    public void writeDescriptorValue(BluetoothGatt gatt,BluetoothGattDescriptor descriptor, int status){}
    public void readRemoteRssi(BluetoothGatt gatt, int rssi, int status){}
}
