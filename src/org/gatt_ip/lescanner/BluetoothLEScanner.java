package org.gatt_ip.lescanner;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.util.List;

/**
 * Created by vensi on 4/21/15.
 */
public abstract class BluetoothLEScanner {
    private static final String TAG = BluetoothLEScanner.class.getName();

    protected Context mContext;
    protected LEScanListener mListener;
    protected BluetoothAdapter mBluetoothAdapter;

    public BluetoothLEScanner(Context context) {
        mContext = context;
    }

    public void startLEScan() {
        startScan();
    }

    public void stopLEScan() {
        stopScan();
    }

    public void registerScanListener(LEScanListener listener) {
        mListener = listener;
    }

    public void unregisterScanListener() {
        mListener = null;
    }


    protected abstract void startScan();

    protected abstract void stopScan();

    protected BluetoothAdapter getBluetoothAdapter() {
        if (mBluetoothAdapter == null) {
            // Initializes Bluetooth adapter.
            final BluetoothManager bluetoothManager =
                    (BluetoothManager) mContext.getApplicationContext().getSystemService(Context.BLUETOOTH_SERVICE);
            mBluetoothAdapter = bluetoothManager.getAdapter();

            if (mBluetoothAdapter == null) {
                Log.d(TAG, "Failed to construct a BluetoothAdapter");
            }
        }
        return mBluetoothAdapter;
    }


    public interface LEScanListener {
        public void onLeScan(BluetoothDevice device, int rssi,List<String> serviceUUIDs, JSONObject mutatedAdevertismentData);
    }
}
