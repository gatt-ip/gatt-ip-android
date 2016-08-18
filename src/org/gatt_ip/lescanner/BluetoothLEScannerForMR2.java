package org.gatt_ip.lescanner;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.gatt_ip.Constants;
import org.gatt_ip.util.Util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by vensi on 4/21/15.
 */
public final class BluetoothLEScannerForMR2 extends BluetoothLEScanner {
    private static final String TAG = BluetoothLEScannerForMR2.class.getName();
    private long mScanPeriod = 2000l;
    private long mScanStopTime = 0l;
    private Handler mHandler;

    public BluetoothLEScannerForMR2(Context context) {
        super(context);
        mHandler = new Handler(Looper.getMainLooper());
    }

    public BluetoothLEScannerForMR2(Context context, boolean duplicates) {
        super(context, duplicates);
        mHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    protected void startScan() {
        if (getBluetoothAdapter() != null) {
            if (getBluetoothAdapter().isEnabled()) {
                getBluetoothAdapter().startLeScan(mLeScanCallback);
                mScanStopTime = new Date().getTime() + mScanPeriod;

                mScanning = true;

                handleIntervalScanning();
            }
        }
    }

    @Override
    protected void stopScan() {
        if (getBluetoothAdapter() != null) {
            if (getBluetoothAdapter().isEnabled() && mLeScanCallback != null) {
                getBluetoothAdapter().stopLeScan(mLeScanCallback);
                if(mScanning)
                    mScanning = false;
            }
        }
    }

    private void handleIntervalScanning() {
        long msUntilNextStop = mScanStopTime - (new Date().getTime());

        if (msUntilNextStop > mScanPeriod)
            msUntilNextStop =  mScanPeriod;

        if (msUntilNextStop > 0) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    handleIntervalScanning();
                }
            }, msUntilNextStop);
        } else {
            if (getBluetoothAdapter() != null) {
                if (getBluetoothAdapter().isEnabled()) {
                    if (mScanning) {
                        getBluetoothAdapter().stopLeScan(mLeScanCallback);

                        startScan();
                    }
                }
            }
        }
    }

    private String toHexString(byte[] data) {
        final StringBuilder hexStr = new StringBuilder(data.length);
        if (data != null && data.length > 0) {
            for(byte byteChar : data) {
                hexStr.append(String.format("%02X", byteChar));
            }
        }
        return new String(hexStr);
    }



    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            if (mListener != null) {
                ByteBuffer wrapped = ByteBuffer.wrap(scanRecord); // big-endian by default
                wrapped.order(ByteOrder.BIG_ENDIAN);
                byte[] scanRed = new byte[scanRecord.length];
                for(int i = 0; i < scanRecord.length; i++) {
                    scanRed[i] = wrapped.get(i);
                }

                mListener.onLeScan(device, rssi, null/*ScanRecord record*/, scanRed);
            }
        }
    };
}
