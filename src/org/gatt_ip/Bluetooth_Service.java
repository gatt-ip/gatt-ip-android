package org.gatt_ip;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.util.ArrayList;
import java.util.UUID;

public class Bluetooth_Service extends Service {
    private static final String TAG = Bluetooth_Service.class.getName();

    private final ArrayList<BluetoothListener> mListeners = new ArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();
        Log.v(TAG, "onCreate");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new BluetoothBinder();
    }

    public void registerListener(BluetoothListener listener) {
        mListeners.add(listener);
    }

    public boolean unregisterListener(BluetoothListener listener) {
        return mListeners.remove(listener);
    }

    public void connectLEDevice(BluetoothDevice bdevice) {
        bdevice.connectGatt(getApplicationContext(), true, mGattCallback);
    }

    public void disconnectLEDevice(BluetoothGatt gatt) {
        gatt.disconnect();
    }

    public void getServices(BluetoothGatt gatt) {
        gatt.discoverServices();
    }

    public void readCharacteristicValue(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        gatt.readCharacteristic(characteristic);
    }

    public void writeCharacteristicValue(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic,String writeType, byte[] data) {
        if (writeType == null) {
            characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        } else {
            if(writeType.equals(Constants.kWriteWithoutResponse)) {
                characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            } else if (writeType.equals(Constants.kWriteWithResponse)) {
                characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            }
        }

        // call to write characteristic
        characteristic.setValue(data);
        gatt.writeCharacteristic(characteristic);

    }

    public void setValueNotification(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, Boolean subscribeBOOL)
    {
        boolean status = false;

        // set notification for characteristic
        if (!gatt.setCharacteristicNotification(characteristic,subscribeBOOL)) {
            Log.e(TAG, "Characteristic notification set failure.");
        }

        // client characteristic configuration.
        UUID descUUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
        BluetoothGattDescriptor desc = characteristic.getDescriptor(descUUID);

        // check whether characteristic having notify or indicate property
        if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0) {
            status = desc.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
        } else {
            status = desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        }

        if (!status) {
            Log.e(TAG, "### Descriptor setvalue fail ###");
        } else {
            if (!gatt.writeDescriptor(desc)) {
                Log.e(TAG, "Descriptor write failed.");
            }
        }
    }

    public void readDescriptorValue(BluetoothGatt gatt, BluetoothGattDescriptor desc) {
        gatt.readDescriptor(desc);
    }

    public void readRemoteRssi(BluetoothGatt gatt) {
        gatt.readRemoteRssi();
    }

   private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status,int newState) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if(newState == BluetoothProfile.STATE_CONNECTED) {
                    for(BluetoothListener listener : mListeners)
                        listener.connectLEDevice(gatt);
                } else if(newState == BluetoothProfile.STATE_DISCONNECTED) {
                    gatt.close();
                    for(BluetoothListener listener : mListeners)
                        listener.disconnectLEDevice(gatt);
                }
            } else if(status == BluetoothGatt.GATT_FAILURE){
                gatt.disconnect();
                for(BluetoothListener listener : mListeners)
                    listener.connectionFailed(gatt);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            for(BluetoothListener listener : mListeners)
                listener.getLEServices(gatt,status);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,BluetoothGattCharacteristic characteristic, int status) {
            for(BluetoothListener listener : mListeners)
                listener.readCharacteristicValue(gatt, characteristic,status);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,BluetoothGattCharacteristic characteristic, int status) {
            for(BluetoothListener listener : mListeners)
                listener.writeCharacteristicValue(gatt, characteristic, status);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,BluetoothGattCharacteristic characteristic) {
            for(BluetoothListener listener : mListeners)
                listener.setValueNotification(gatt, characteristic);
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt,BluetoothGattDescriptor descriptor, int status) {
            for(BluetoothListener listener : mListeners)
                listener.readDescriptorValue(gatt, descriptor, status);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt,BluetoothGattDescriptor descriptor, int status) {
            for(BluetoothListener listener : mListeners)
                listener.writeDescriptorValue(gatt, descriptor, status);
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            for(BluetoothListener listener : mListeners)
                listener.readRemoteRssi(gatt, rssi, status);
        }
    };

    public class BluetoothBinder extends Binder {
        public Bluetooth_Service getService() {
            return Bluetooth_Service.this;
        }
    }
}
