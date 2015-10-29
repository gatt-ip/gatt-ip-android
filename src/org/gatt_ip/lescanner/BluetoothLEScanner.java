package org.gatt_ip.lescanner;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.util.Log;
import org.gatt_ip.Constants;
import org.gatt_ip.util.Util;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by vensi on 4/21/15.
 */
public abstract class BluetoothLEScanner {
    private static final String TAG = BluetoothLEScanner.class.getName();

    protected Context mContext;
    protected LEScanListener mListener;
    protected BluetoothAdapter mBluetoothAdapter;

    protected long mScanTime; // scan time in seconds ** for future use **
    protected boolean mEnableCyclicalScanning;
    public boolean mScanning;


    public BluetoothLEScanner(Context context) {
        mContext = context;
        mEnableCyclicalScanning = false;
    }

    public BluetoothLEScanner(Context context, boolean duplicates)
    {
        mContext = context;
        mEnableCyclicalScanning = duplicates;
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

    public List<String> parseAdvertisementData(byte[] data) {
        boolean flag = true;
        String scanRecordString = Util.byteArrayToHex(data);
        List<String> advdata = new ArrayList<>();
        List<String> serviceUUIDs = new ArrayList<>();

        if(scanRecordString.length()%2 == 0) {
            for(int i = 0; i < scanRecordString.length(); i = i+2) {
                StringBuilder sb = new StringBuilder(2);
                sb.append(scanRecordString.charAt(i));
                sb.append(scanRecordString.charAt(i+1));
                advdata.add(sb.toString());
            }
        } else {
            for(int i = 0; i < scanRecordString.length(); i++) {
                StringBuilder sb = new StringBuilder(2);
                sb.append(scanRecordString.charAt(2*i));
                sb.append(scanRecordString.charAt(2 * i + 1));
                advdata.add(sb.toString());
            }
        }

        do {
            if(advdata.get(1).equals(Constants.kGAP_ADTYPE_INCOMPLETE_16BIT_SERVICEUUID) || advdata.get(1).equals(Constants.kGAP_ADTYPE_COMPLETE_16BIT_SERVICEUUID)) {
                int advdataLength = Integer.parseInt(advdata.get(0),16);
                StringBuilder sb = new StringBuilder();

                for(int i = advdataLength, j = 0; i >= 2; i--) {
                    sb.append(advdata.get(i));
                    j++;
                    if(j == 2) {
                        serviceUUIDs.add(sb.toString());
                        j = 0;
                        sb.delete(0,4);
                    }
                }

                for(int i = 0; i <= advdataLength; i++) {
                    advdata.remove(0);
                }
            } else if(advdata.get(1).equals(Constants.kGAP_ADTYPE_INCOMPLETE_32BIT_SERVICEUUID) || advdata.get(1).equals(Constants.kGAP_ADTYPE_COMPLETE_32BIT_SERVICEUUID)) {
                int advdataLength = Integer.parseInt(advdata.get(0),16);
                StringBuilder sb = new StringBuilder();

                for(int i = advdataLength, j = 0; i >= 2; i--) {
                    sb.append(advdata.get(i));
                    j++;
                    if(j == 4) {
                        serviceUUIDs.add(sb.toString());
                        j = 0;
                        sb.delete(0,8);
                    }
                }

                if(sb.length() > 0) {
                    serviceUUIDs.add(sb.toString());
                }

                for(int i = 0; i <= advdataLength; i++) {
                    advdata.remove(0);
                }
            } else if(advdata.get(1).equals(Constants.kGAP_ADTYPE_INCOMPLETE_128BIT_SERVICEUUID) || advdata.get(1).equals(Constants.kGAP_ADTYPE_COMPLETE_128BIT_SERVICEUUID)) {
                int advdataLength = Integer.parseInt(advdata.get(0),16);
                StringBuilder sb = new StringBuilder();

                for(int i = advdataLength; i >= 2; i--) {
                    sb.append(advdata.get(i));
                    if (i == 14 || i == 12 || i == 10 || i == 8) {
                        sb.append("-");
                    }
                }

                if(sb.length() > 0) {
                    serviceUUIDs.add(sb.toString());
                }

                for(int i = 0; i <= advdataLength; i++) {
                    advdata.remove(0);
                }
            } else {
                int advdataLength = Integer.parseInt(advdata.get(0),16);
                for(int i = 0; i <= advdataLength; i++) {
                    advdata.remove(0);
                }
            }

            if(advdata.size() <= 1) {
                flag = false;
            }
        } while(flag);
        return serviceUUIDs;
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
        public void onLeScan(BluetoothDevice device, int rssi, byte[] adevertismentData);
    }
}
