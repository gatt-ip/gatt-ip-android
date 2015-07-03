package org.gatt_ip.lescanner;

import android.annotation.TargetApi;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.gatt_ip.Constants;
import org.gatt_ip.Util;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by vensi on 4/21/15.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public final class BluetoothLEScannerForLollipop extends BluetoothLEScanner {
    private static final String TAG = BluetoothLEScannerForLollipop.class.getName();
    private long mScanPeriod = 2000l;
    private long mScanStopTime = 0l;
    private boolean mScanning;
    private Handler mHandler;
    // This should probably be set up with parameters on creation.
    private ScanSettings mScanSettings;
    private List<ScanFilter> mScanFilters = new ArrayList<ScanFilter>();

    public BluetoothLEScannerForLollipop(Context context) {
        super(context);
        mHandler = new Handler(Looper.getMainLooper());
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void startScan() {
        if (getBluetoothAdapter() != null) {
            if (getBluetoothAdapter().isEnabled()) {
                ScanSettings.Builder scanSettingsBuilder = new ScanSettings.Builder();
                scanSettingsBuilder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
                mScanSettings = scanSettingsBuilder.build();

                getBluetoothAdapter().getBluetoothLeScanner().startScan(mScanFilters, mScanSettings, mScanCallback);
                mScanStopTime = new Date().getTime() + mScanPeriod;

                mScanning = true;

                handleIntervalScanning();

            }
        }
    }

    @Override
    protected void stopScan() {
        if (getBluetoothAdapter() != null) {
            getBluetoothAdapter().getBluetoothLeScanner().stopScan(mScanCallback);
            if(mScanning)
                mScanning = false;
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
                        getBluetoothAdapter().getBluetoothLeScanner().stopScan(mScanCallback);

                        startScan();
                    }
                }
            }
        }
    }


    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.e(TAG, "Scan failed");
        }

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            JSONObject mutatedAdevertismentData = new JSONObject();
            List<String> serviceUUIDs = new ArrayList<>();
            if (mListener != null) {
                try {
                    ScanRecord record = result.getScanRecord();
                    byte[] scanRecord = record.getBytes();
                    ByteBuffer wrapped = ByteBuffer.wrap(scanRecord); // big-endian by default
                    wrapped.order(ByteOrder.BIG_ENDIAN);
                    byte[] scanRed = new byte[scanRecord.length];
                    for(int i = 0; i < scanRecord.length; i++) {
                        scanRed[i] = wrapped.get(i);
                    }
                    String scanRecordString = Util.byteArrayToHex(scanRed);

                    mutatedAdevertismentData.put(Constants.kRawAdvertisementData, scanRecordString);
                } catch (JSONException je) {
                    je.printStackTrace();
                }
                    mListener.onLeScan(result.getDevice(), result.getRssi(), mutatedAdevertismentData);
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
        }
    };
}
