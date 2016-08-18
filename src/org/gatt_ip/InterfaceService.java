package org.gatt_ip;

import android.app.Service;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Binder;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by vensi on 9/25/15.
 */
public abstract class InterfaceService extends Service {
    public abstract class InterfaceBinder extends Binder {
        public abstract InterfaceService getService();
    }

    public enum ServiceState {
        SERVICE_STATE_NONE,
        SERVICE_STATE_INACTIVE,
        SERVICE_STATE_ACTIVE,
        SERVICE_STATE_UNSUPPORTED
    }

    public enum Error {

        DEVICE_NOT_FOUND,
        DEVICE_SERVICE_NOT_FOUND,
        DEVICE_ATTRIBUTES_NOT_FOUND,
        ATTRIBUTE_DESCRIPTOR_NOT_FOUND,

        ATTRIBUTE_READ_FAILED,
        ATTRIBUTE_WRITE_FAILED,
        ATTRIBUTE_NOTIFICATION_FAILED,

        ATTRIBUTE_DESCRIPTOR_READ_FAILED,
        ATTRIBUTE_DESCRIPTOR_WRITE_FAILED,

        CONNECTION_FAILED,
        PERMISSOIN_DENIED

    }

    private static final String TAG = InterfaceService.class.getName();

    protected final ArrayList<DeviceEventListener> m_listeners = new ArrayList<>();

    protected ServiceState m_current_state;// = ServiceState.SERVICE_STATE_ACTIVE;

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

    public void registerDeviceEventListener(DeviceEventListener listener)
    {
        m_listeners.add(listener);
    }

    public boolean unregisterDeviceEventListener(DeviceEventListener listener)
    {
        return m_listeners.remove(listener);
    }

    public void setServiceState(ServiceState state){
        m_current_state = state;
    }

    public ServiceState getServiceState()
    {
        return m_current_state;
    }

    public abstract void startDeviceDiscovery(int timeout, boolean duplicates);

    public abstract void stopDeviceDiscovery();

    public abstract void connectDevice(String deviceIdentifier);

    public abstract void disconnectDevice(String deviceIdentifier);

    public abstract void getDeviceServices(String deviceIdentifier);

    public abstract void getDeviceAttributes(String serviceIdentifier);

    public abstract void getDeviceAttributeDescriptors(String attributeIdentifier);

    public abstract void getDeviceAttributeValue(String attributeIdentifier);

    public abstract void getDeviceAttributeNotifications(String attributeIdentifier, boolean enable);

    public abstract void writeDeviceAttributeValue(String attributeIdentifier, String writeType, byte[] data);

    public abstract void getDeviceAttributeDescriptorValue(String attributeDescriptorIdentifier, String attributeIdentifier, String serviceIdentifier);

    public abstract void writeDeviceAttributeDescriptorValue(String attributeDescriptorIdentifier, byte[] data);

    public abstract void getDeviceSignal(String deviceIdentifier);
}
