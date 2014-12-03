package org.gatt_ip;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.util.Log;

public class GATTIP {
	BluetoothAdapter mBlueToothAdapter;
	public static Context context;
	public static String message, sUUID, cUUID, value, gattMsg, address;
	public static boolean enable;
	public static GATTIPListener listener;
	public static ArrayList<BluetoothDevice> availableDevices;
	public static ArrayList<BluetoothGatt> connectedDevices;
	public static ArrayList<String> commandsArray;
	public static boolean notifications;
	int MAX_NUMBER_OF_REQUESTS = 60;
	public List<JSONObject> previousRequests;

	public GATTIP(Context ctx) {
		context = ctx;
	}

	// set the reference for listener when we got request from client
	public void setGATTIPListener(GATTIPListener GATTIPlistener) {
		listener = GATTIPlistener;
	}
	
	public static void startBroadcasting()
	{
		// Register for broadcasts on BluetoothAdapter state change
	    IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
	    context.registerReceiver(bReceiver, filter);
	}
	
	public static void stopBroadcasting()
	{
		context.unregisterReceiver(bReceiver);
	}
	
	// method to call request coming from client
	public void request(String gattipMesg) {
		if (gattipMesg == null) {
			try {
				JSONObject errorObj = new JSONObject();
				errorObj.put(Constants.kCode, Constants.kInvalidRequest);
				JSONObject jsonData = new JSONObject();
				jsonData.put(Constants.kError, errorObj);
				sendResponse(jsonData);
			} catch (JSONException je) {
				je.printStackTrace();
			}
			return;
		}
		try {
			// for handling multiple commands from client
			JSONObject reqObj = new JSONObject(gattipMesg);
			// handle log messages to display in LogView
			handleLoggingRequestAndResponse(reqObj);
			if (reqObj != null) {
				String method = reqObj.getString(Constants.kMethod);
				String[] request = method.split(",");
				for (int i = 0; i < request.length; i++) {
					message = request[i];
					if (message == null) {
						try {
							JSONObject errorObj = new JSONObject();
							errorObj.put(Constants.kCode,Constants.kInvalidRequest);
							JSONObject jsonData = new JSONObject();
							jsonData.put(Constants.kError, errorObj);
							jsonData.put(Constants.kIdField, null);
							sendResponse(jsonData);
						} catch (JSONException je) {
							je.printStackTrace();
						}
						return;
					}

					if (message.equals(Constants.kConfigure)) {
						configure(reqObj);
					} else if (message.equals(Constants.kConnect)) {
						connectStick(reqObj);
					} else if (message.equals(Constants.kDisconnect)) {
						disconnectStick(reqObj);
					} else if (message.equals(Constants.kGetPerhipheralsWithServices)) {
						// getPerhipheralsWithServices(reqObj);
					} else if (message.equals(Constants.kGetPerhipheralsWithIdentifiers)) {
						// getPerhipheralsWithIdentifiers(reqObj);
					} else if (message.equals(Constants.kScanForPeripherals)) {
						scanForPeripherals(reqObj);
					} else if (message.equals(Constants.kStopScanning)) {
						stopScanning(reqObj);
					} else if (message.equals(Constants.kCentralState)) {
						getConnectionState(reqObj);
					} else if (message.equals(Constants.kGetConnectedPeripherals)) {
						getConnectedPeripherals(reqObj);
					} else if (message.equals(Constants.kGetServices)) {
						getServices(reqObj);
					} else if (message != null&& message.equals(Constants.kGetIncludedServices)) {
						getIncludedServices(reqObj);
					} else if (message.equals(Constants.kGetCharacteristics)) {
						getCharacteristics(reqObj);
					} else if (message.equals(Constants.kGetDescriptors)) {
						getDescriptors(reqObj);
					} else if (message.equals(Constants.kGetCharacteristicValue)) {
						getCharacteristicValue(reqObj);
					} else if (message.equals(Constants.kGetDescriptorValue)) {
						getDescriptorValue(reqObj);
					} else if (message.equals(Constants.kWriteCharacteristicValue)) {
						writeCharacteristicValue(reqObj);
					} else if (message.equals(Constants.kSetValueNotification)) {
						getValueNotification(reqObj);
					} else if (message.equals(Constants.kGetPeripheralState)) {
						getPeripheralState(reqObj);
					} else if (message.equals(Constants.kGetRSSI)) {
						getRSSI(reqObj);
					} else {
						JSONObject invalidMethod = new JSONObject();
						invalidMethod.put("Error", "Your Method is invalid");
						sendResponse(invalidMethod);
					}
				}
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	// sending response to client for requested command
	public void sendResponse(JSONObject jsonData) {
		// handle log messages to display in LogView
		handleLoggingRequestAndResponse(jsonData);
		
		if (listener == null) {
			return;
		}
		try {
			jsonData.put(Constants.kJsonrpc, Constants.kJsonrpcVersion);
		} catch (JSONException e) {
			e.printStackTrace();
		}
		listener.response(jsonData.toString());
	}

	// attempt to scan ble devices which are near to our device
	public void scanBLEDevices(final boolean enable) {
		final BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
		mBlueToothAdapter = bluetoothManager.getAdapter();
		if (enable) {
			// start scanning for BLE devices which are available
			if (mBlueToothAdapter.isEnabled()) {
				mBlueToothAdapter.startLeScan(mLeScanCallback);
			}
		} else {
			// stop scanning BLE devices
			mBlueToothAdapter.stopLeScan(mLeScanCallback);
			JSONObject jsonData = new JSONObject();
			try {
				// get the json data to send client
				jsonData.put(Constants.kResult, Constants.kStopScanning);
				// send response to client
				sendResponse(jsonData);
			} catch (JSONException je) {
				je.printStackTrace();
			}
		}
	}

	// Device scan callback.
	private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
		@Override
		public void onLeScan(final BluetoothDevice device, int rssi,byte[] scanRed) {
			// scan bluetooth devices
			if (availableDevices.contains(device)) {
				for (int i = 0; i < availableDevices.size(); i++) {
					if (availableDevices.get(i).equals(device))
						availableDevices.set(i, availableDevices.get(i));
				}
			} else {
				availableDevices.add(device);
			}
			JSONObject response = new JSONObject();
			JSONObject mutatedAdevertismentData = new JSONObject();
			try {
				String peripheralUUIDString = device.getAddress().toUpperCase(Locale.getDefault());
				String peripheralNameString = device.getName();
				// get the json data to send client
				JSONObject parameters = new JSONObject();
				parameters.put(Constants.kAdvertisementDataKey,mutatedAdevertismentData);
				parameters.put(Constants.kRSSIkey, rssi);
				parameters.put(Constants.kPeripheralUUID, peripheralUUIDString);
				parameters.put(Constants.kPeripheralName, peripheralNameString);
				response.put(Constants.kResult, Constants.kScanForPeripherals);
				response.put(Constants.kParams, parameters);
				// send response to client
				sendResponse(response);
			} catch (JSONException je) {
				je.printStackTrace();
			}
		}
	};

	private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
		@Override
		public void onConnectionStateChange(BluetoothGatt gatt, int status,int newState) {
			BluetoothDevice device = gatt.getDevice();
			JSONObject response = new JSONObject();
			// get the json data to send client
			JSONObject parameters = new JSONObject();
			if (newState == BluetoothProfile.STATE_CONNECTED) {
				if (connectedDevices.contains(gatt)) {
					for (int i = 0; i < connectedDevices.size(); i++) {
						if (connectedDevices.get(i).equals(gatt))
							connectedDevices.set(i, connectedDevices.get(i));
					}
				} else {
					connectedDevices.add(gatt);
				}
				// send connected response to client
				try {
					parameters.put(Constants.kPeripheralUUID,device.getAddress());
					parameters.put(Constants.kPeripheralName, device.getName());
					response.put(Constants.kResult, Constants.kConnect);
					response.put(Constants.kParams, parameters);
					sendResponse(response);
				} catch (JSONException je) {
					je.printStackTrace();
				}
			} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
				// send response to client
				try {
					// get the json data to send client
					parameters.put(Constants.kPeripheralUUID,device.getAddress());
					parameters.put(Constants.kPeripheralName, device.getName());
					response.put(Constants.kResult, Constants.kDisconnect);
					response.put(Constants.kParams, parameters);
					sendResponse(response);
				} catch (JSONException je) {
					je.printStackTrace();
				}
				// delete connected device from list after disconnect device.
				if (connectedDevices.contains(gatt)) {
					for (int i = 0; i < connectedDevices.size(); i++) {
						if (connectedDevices.get(i).equals(gatt))
							connectedDevices.remove(i);
					}
				}
			}
		}

		@Override
		public void onServicesDiscovered(BluetoothGatt gatt, int status) {
			JSONObject response = new JSONObject();
			JSONObject errorCode = new JSONObject();
			if (status == BluetoothGatt.GATT_SUCCESS) {
				String peripheralUUIDString = gatt.getDevice().getAddress().toUpperCase(Locale.getDefault());
				List<JSONObject> services = Util.listOfJsonServicesFrom(gatt.getServices());
				try {
					JSONObject parameters = new JSONObject();
					parameters.put(Constants.kPeripheralUUID,peripheralUUIDString);
					parameters.put(Constants.kServices, new JSONArray(services));
					response.put(Constants.kResult, Constants.kGetServices);
					response.put(Constants.kParams, parameters);
				} catch (JSONException e) {
					e.printStackTrace();
				}
				sendResponse(response);
				return;
			}
			try {
				errorCode.put(Constants.kCode, Constants.kError32603);
				errorCode.put(Constants.kMessageField, "failed to get services");
				response.put(Constants.kResult, Constants.kGetServices);
				response.put(Constants.kPeripheralUUID, gatt.getDevice().getAddress());
				response.put(Constants.kError, errorCode);
				sendResponse(response);
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}

		@Override
		// Result of a characteristic read operation
		public void onCharacteristicRead(BluetoothGatt gatt,BluetoothGattCharacteristic characteristic, int status) {
			JSONObject response = new JSONObject();
			JSONObject parameters = new JSONObject();
			String characteristicUUIDString = characteristic.getUuid().toString().toUpperCase(Locale.getDefault());
			if (status == BluetoothGatt.GATT_SUCCESS) {
				// broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
				byte[] characteristicValue = characteristic.getValue();
				String characteristicValueString = Util.byteArrayToHex(characteristicValue);
				try {
					String characteristicProperty = ""+ characteristic.getProperties();
					String peripheralUUIDString = gatt.getDevice().getAddress().toUpperCase(Locale.getDefault());
					String serviceUUIDString = characteristic.getService().getUuid().toString().toUpperCase(Locale.getDefault());
					int characteristicIsNotifying;
					if (notifications)
						characteristicIsNotifying = 1;
					else
						characteristicIsNotifying = 0;
					
					parameters.put(Constants.kIsNotifying,characteristicIsNotifying);
					parameters.put(Constants.kProperties,characteristicProperty);
					parameters.put(Constants.kCharacteristicUUID,characteristicUUIDString);
					parameters.put(Constants.kPeripheralUUID,peripheralUUIDString);
					parameters.put(Constants.kServiceUUID, serviceUUIDString);
					parameters.put(Constants.kValue, characteristicValueString);
					response.put(Constants.kResult,Constants.kGetCharacteristicValue);
					response.put(Constants.kParams, parameters);
					sendResponse(response);
					return;
				} catch (JSONException je) {
					je.printStackTrace();
				}
			}
			try {
				JSONObject errorCode = new JSONObject();
				errorCode.put(Constants.kCode, Constants.kError32603);
				errorCode.put(Constants.kMessageField, "write data failed");
				parameters.put(Constants.kCharacteristicUUID,characteristicUUIDString);
				response.put(Constants.kResult,Constants.kGetCharacteristicValue);
				response.put(Constants.kParams, parameters);
				response.put(Constants.kError, errorCode);
				sendResponse(response);
			} catch (JSONException je) {
				je.printStackTrace();
			}
		}

		@Override
		public void onCharacteristicWrite(BluetoothGatt gatt,BluetoothGattCharacteristic characteristic, int status) {
			String characteristicUUIDString = characteristic.getUuid().toString().toUpperCase(Locale.getDefault());
			String peripheralUUIDString = gatt.getDevice().getAddress().toUpperCase(Locale.getDefault());
			JSONObject parameters = new JSONObject();
			JSONObject response = new JSONObject();
			if (status == BluetoothGatt.GATT_SUCCESS) {
				try {
					parameters.put(Constants.kCharacteristicUUID,characteristicUUIDString);
					parameters.put(Constants.kPeripheralUUID,peripheralUUIDString);
					response.put(Constants.kResult,Constants.kWriteCharacteristicValue);
					response.put(Constants.kParams, parameters);
					sendResponse(response);
					return;
				} catch (JSONException je) {
					je.printStackTrace();
				}
			}
			// send error message when write characteristic not supported for
			// specified data
			try {
				parameters.put(Constants.kCharacteristicUUID,characteristicUUIDString);
				parameters.put(Constants.kPeripheralUUID, peripheralUUIDString);
				JSONObject errorCode = new JSONObject();
				errorCode.put(Constants.kCode, Constants.kError32603);
				errorCode.put(Constants.kMessageField, "write data failed");
				response.put(Constants.kResult,Constants.kWriteCharacteristicValue);
				response.put(Constants.kParams, parameters);
				response.put(Constants.kError, errorCode);
				sendResponse(response);
			} catch (JSONException je) {
				je.printStackTrace();
			}
		}

		// method call when update the value for characteristic
		@Override
		public void onCharacteristicChanged(BluetoothGatt gatt,BluetoothGattCharacteristic characteristic) {
			JSONObject response = new JSONObject();
			JSONObject parameters = new JSONObject();
			String characteristicUUIDString = characteristic.getUuid().toString().toUpperCase(Locale.getDefault());
			String peripheralUUIDString = gatt.getDevice().getAddress().toUpperCase(Locale.getDefault());
			String serviceUUIDString = characteristic.getService().getUuid().toString().toUpperCase(Locale.getDefault());
			byte[] characteristicValue = characteristic.getValue();
			String characteristicValueString = Util.byteArrayToHex(characteristicValue);
			String characteristicProperty = "" + characteristic.getProperties();
			int characteristicIsNotifying;
			if (notifications)
				characteristicIsNotifying = 1;
			else
				characteristicIsNotifying = 0;

			try {
				parameters.put(Constants.kIsNotifying,characteristicIsNotifying);
				parameters.put(Constants.kProperties, characteristicProperty);
				parameters.put(Constants.kCharacteristicUUID,characteristicUUIDString);
				parameters.put(Constants.kPeripheralUUID, peripheralUUIDString);
				parameters.put(Constants.kServiceUUID, serviceUUIDString);
				parameters.put(Constants.kValue, characteristicValueString);
				response.put(Constants.kResult, Constants.kSetValueNotification);
				response.put(Constants.kParams, parameters);
				sendResponse(response);
				return;
			} catch (JSONException je) {
				je.printStackTrace();
			}
			// error occur when we set notification for a characteristic
			try {
				parameters.put(Constants.kCharacteristicUUID,characteristicUUIDString);
				parameters.put(Constants.kServiceUUID, serviceUUIDString);
				parameters.put(Constants.kPeripheralUUID, peripheralUUIDString);
				JSONObject errorCode = new JSONObject();
				errorCode.put(Constants.kCode, Constants.kError32603);
				errorCode.put(Constants.kMessageField, "write data failed");
				response.put(Constants.kResult, Constants.kSetValueNotification);
				response.put(Constants.kParams, parameters);
				response.put(Constants.kError, errorCode);
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}

		@Override
		public void onDescriptorRead(BluetoothGatt gatt,BluetoothGattDescriptor descriptor, int status) {
			JSONObject response = new JSONObject();
			if (status == BluetoothGatt.GATT_SUCCESS) {
				String desriptorUUID = descriptor.getUuid().toString();
				byte[] byteValue = descriptor.getValue();
				try {
					String descriptorValue = new String(byteValue, "UTF-8");
					response.put(Constants.kResult,Constants.kGetDescriptorValue);
					response.put(Constants.kDescriptorUUID, desriptorUUID);
					response.put(Constants.kValue, descriptorValue);
					sendResponse(response);
					return;
				} catch (UnsupportedEncodingException e) {
					e.printStackTrace();
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
			JSONObject errorObj = new JSONObject();
			try {
				errorObj.put(Constants.kCode, Constants.kError32603);
				errorObj.put(Constants.kMessageField, "");
				response.put(Constants.kResult, Constants.kGetDescriptorValue);
				response.put(Constants.kError, errorObj);
				sendResponse(response);
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}

		@Override
		public void onDescriptorWrite(BluetoothGatt gatt,
				BluetoothGattDescriptor descriptor, int status) {
			// Temp comment
			/*
			 * JSONObject response = new JSONObject(); if (status ==
			 * BluetoothGatt.GATT_SUCCESS) { try { String desriptorUUID =
			 * descriptor.getUuid().toString(); String peripheralUUIDString =
			 * gatt.getDevice().getAddress();
			 * response.put(Constants.kResult,Constants.kWriteDescriptorValue);
			 * response.put(Constants.kDescriptorUUID, desriptorUUID);
			 * response.put(Constants.kPeripheralUUID,peripheralUUIDString);
			 * sendResponse(response, null); return; } catch (JSONException e) {
			 * e.printStackTrace(); } } JSONObject errorObj = new JSONObject();
			 * try { errorObj.put(Constants.kCode, Constants.kError32603);
			 * errorObj.put(Constants.kMessageField, "");
			 * response.put(Constants.kResult, Constants.kWriteDescriptorValue);
			 * response.put(Constants.kError, errorObj); sendResponse(response,
			 * null); } catch (JSONException e) { e.printStackTrace(); }
			 */
			if (!notifications) {
				BluetoothGattCharacteristic characteristic = descriptor.getCharacteristic();
				JSONObject response = new JSONObject();
				String characteristicUUIDString = characteristic.getUuid().toString().toUpperCase(Locale.getDefault());
				String peripheralUUIDString = gatt.getDevice().getAddress().toUpperCase(Locale.getDefault());
				String serviceUUIDString = characteristic.getService().getUuid().toString().toUpperCase(Locale.getDefault());
				String characteristicValueString = Util.byteArrayToHex(characteristic.getValue());
				String characteristicProperty = ""+ characteristic.getProperties();
				int characteristicIsNotifying;
				if (notifications)
					characteristicIsNotifying = 1;
				else
					characteristicIsNotifying = 0;
				JSONObject parameters = new JSONObject();
				try {
					parameters.put(Constants.kIsNotifying,characteristicIsNotifying);
					parameters.put(Constants.kProperties,characteristicProperty);
					parameters.put(Constants.kCharacteristicUUID,characteristicUUIDString);
					parameters.put(Constants.kPeripheralUUID,peripheralUUIDString);
					parameters.put(Constants.kServiceUUID, serviceUUIDString);
					parameters.put(Constants.kValue, characteristicValueString);
					response.put(Constants.kResult,Constants.kGetCharacteristicValue);
					response.put(Constants.kParams, parameters);
					sendResponse(response);
					return;
				} catch (JSONException je) {
					je.printStackTrace();
				}
				// error occur when we set notification for a characteristic
				try {
					parameters.put(Constants.kCharacteristicUUID,characteristicUUIDString);
					parameters.put(Constants.kServiceUUID, serviceUUIDString);
					parameters.put(Constants.kPeripheralUUID,peripheralUUIDString);
					JSONObject errorCode = new JSONObject();
					errorCode.put(Constants.kCode, Constants.kError32603);
					errorCode.put(Constants.kMessageField, "write data failed");
					response.put(Constants.kResult,Constants.kSetValueNotification);
					response.put(Constants.kParams, parameters);
					response.put(Constants.kError, errorCode);
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
		}

		@Override
		public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
			JSONObject response = new JSONObject();
			if (status == BluetoothGatt.GATT_SUCCESS) {
				String peripheralUUIDString = gatt.getDevice().getAddress().toUpperCase(Locale.getDefault());
				String peripheralName = gatt.getDevice().getName();
				try {
					response.put(Constants.kResult, Constants.kGetRSSI);
					response.put(Constants.kPeripheralUUID,peripheralUUIDString);
					response.put(Constants.kPeripheralName, peripheralName);
					response.put(Constants.kRSSIkey, rssi);
					sendResponse(response);
					return;
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
			JSONObject errorObj = new JSONObject();
			try {
				errorObj.put(Constants.kCode, Constants.kError32603);
				errorObj.put(Constants.kMessageField, "");
				response.put(Constants.kResult, Constants.kGetRSSI);
				response.put(Constants.kError, errorObj);
				sendResponse(response);
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
	};

	public void configure(JSONObject reqObj) {
		availableDevices = new ArrayList<BluetoothDevice>();
		connectedDevices = new ArrayList<BluetoothGatt>();
		commandsArray = new ArrayList<String>();
		previousRequests = new ArrayList<JSONObject>();
		JSONObject response = new JSONObject();
		try {
			response.put(Constants.kResult, Constants.kConfigure);
			getState();
		} catch (JSONException e) {
			e.printStackTrace();
		}
		sendResponse(response);
	}

	public void getState() {
		JSONObject parameters = new JSONObject();
		JSONObject response = new JSONObject();
		String stateString;
		PackageManager manager = context.getPackageManager();
		final BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
		mBlueToothAdapter = bluetoothManager.getAdapter();
		if (mBlueToothAdapter == null) {
			stateString = Constants.kUnknown;
		} else if (!manager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
			stateString = Constants.kUnsupported;
		} else {
			stateString = Util.centralStateStringFromCentralState(mBlueToothAdapter.getState());
		}

		try {
			parameters.put(Constants.kState, stateString);
			response.put(Constants.kParams, parameters);
			response.put(Constants.kResult, Constants.kCentralState);
			sendResponse(response);
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	public void connectStick(JSONObject reqObj) {
		if (!isPoweredOn()) {
			sendReasonForFailedCall();
			return;
		}
		try {
			JSONObject parameters = reqObj.getJSONObject(Constants.kParams);
			address = parameters.getString(Constants.kPeripheralUUID);
		} catch (JSONException e) {
			e.printStackTrace();
		}
		for (BluetoothDevice bdevice : availableDevices) {
			if (bdevice.getAddress().equals(address)) {
				bdevice.connectGatt(context, true, mGattCallback);
			}
		}
	}

	public void disconnectStick(JSONObject reqObj) {
		// handle disconnect event
		if (!isPoweredOn()) {
			sendReasonForFailedCall();
			return;
		}
		try {
			JSONObject jObj = reqObj.getJSONObject(Constants.kParams);
			address = jObj.getString(Constants.kPeripheralUUID);
		} catch (JSONException e) {
			e.printStackTrace();
		}
		for (BluetoothGatt gatt : connectedDevices) {
			BluetoothDevice device = gatt.getDevice();
			if (device.getAddress().equals(address)) {
				gatt.disconnect();
			}
		}
	}

	public void getConnectionState(JSONObject reqObj) {
		// need to comnplare with connected device address
		String stateOfPerpheral = null;
		for (BluetoothGatt gatt : connectedDevices) {
			stateOfPerpheral = Util.peripheralStateStringFromPeripheralState(gatt);
		}
		if (stateOfPerpheral != null) {
			JSONObject respObj = new JSONObject();
			try {
				respObj.put(Constants.kResult, Constants.kCentralState);
				respObj.put(Constants.kStateField, stateOfPerpheral);
				sendResponse(respObj);
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
	}

	public void getConnectedPeripherals(JSONObject reqObj) {
		JSONObject respObj = new JSONObject();
		int index = 0;
		JSONObject peripheralsObj = new JSONObject();
		JSONObject peripheralObj = new JSONObject();
		for (BluetoothGatt gatt : connectedDevices) {
			String peripheralState = Util.peripheralStateStringFromPeripheralState(gatt);
			String peripheralUUIDString = Util.peripheralUUIDStringFromPeripheral(gatt.getDevice()).toUpperCase(Locale.getDefault());
			try {
				peripheralObj.put(Constants.kStateField, peripheralState);
				peripheralObj.put(Constants.kPeripheralUUID,peripheralUUIDString);
				peripheralsObj.put("" + index, peripheralObj);
				index++;
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
		try {
			respObj.put(Constants.kPeripherals, peripheralsObj);
			sendResponse(respObj);
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	public void scanForPeripherals(JSONObject reqObj) {
		if (!isPoweredOn()) {
			sendReasonForFailedCall();
			return;
		}
		scanBLEDevices(true);
	}

	public void stopScanning(JSONObject reqObj) {
		if (!isPoweredOn()) {
			sendReasonForFailedCall();
			return;
		}
		scanBLEDevices(false);
	}

	public void getServices(JSONObject reqObj) {
		JSONObject jObj;
		try {
			jObj = reqObj.getJSONObject(Constants.kParams);
			String address = jObj.getString(Constants.kPeripheralUUID);
			BluetoothGatt gatt = Util.peripheralIn(connectedDevices, address);
			if (gatt == null) {
				sendPeripheralNotFoundErrorMessage();
				return;
			}
			gatt.discoverServices();
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	public void getIncludedServices(JSONObject reqObj) {
		JSONObject parameters;
		try {
			parameters = reqObj.getJSONObject(Constants.kParams);
			String serviceUUIDString = parameters.getString(Constants.kServiceUUID).toUpperCase(Locale.getDefault());
			UUID serviceUUID = UUID.fromString(serviceUUIDString);
			HashMap<BluetoothGatt, BluetoothGattService> requestedPeripheralAndService = Util.serviceIn(connectedDevices, serviceUUID);
			Set<BluetoothGatt> keySet = requestedPeripheralAndService.keySet();
			BluetoothGatt bGatt = null;
			for (BluetoothGatt gatt : keySet) {
				bGatt = gatt;
			}
			if (bGatt == null) {
				sendPeripheralNotFoundErrorMessage();
				return;
			}
			BluetoothGattService service = requestedPeripheralAndService
					.get(bGatt);
			if (service == null) {
				sendServiceNotFoundErrorMessage();
				return;
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	public void getCharacteristics(JSONObject reqObj) {
		JSONObject reqParameters;
		try {
			reqParameters = reqObj.getJSONObject(Constants.kParams);
			String serviceUUIDString = reqParameters.getString(Constants.kServiceUUID).toUpperCase(Locale.getDefault());
			UUID serviceUUID = UUID.fromString(serviceUUIDString);
			HashMap<BluetoothGatt, BluetoothGattService> requestedPeripheralAndService = Util.serviceIn(connectedDevices, serviceUUID);
			Set<BluetoothGatt> keySet = requestedPeripheralAndService.keySet();
			BluetoothGatt bGatt = null;
			for (BluetoothGatt gatt : keySet) {
				bGatt = gatt;
			}
			if (bGatt == null) {
				sendPeripheralNotFoundErrorMessage();
				return;
			}
			BluetoothGattService requestedService = requestedPeripheralAndService.get(bGatt);
			if (requestedService == null) {
				sendServiceNotFoundErrorMessage();
				return;
			}
			List<JSONObject> listOfCharacteristics = Util.listOfJsonCharacteristicsFrom(requestedService.getCharacteristics());
			JSONObject parameters = new JSONObject();
			JSONObject response = new JSONObject();
			parameters.put(Constants.kPeripheralUUID, bGatt.getDevice().getAddress());
			parameters.put(Constants.kServiceUUID, requestedService.getUuid().toString().toUpperCase(Locale.getDefault()));
			parameters.put(Constants.kCharacteristics, new JSONArray(listOfCharacteristics));
			response.put(Constants.kResult, Constants.kGetCharacteristics);
			response.put(Constants.kParams, parameters);
			sendResponse(response);
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	public void getDescriptors(JSONObject reqObj) {
		try {
			JSONObject reqparameters = reqObj.getJSONObject(Constants.kParams);
			String UUIDString = reqparameters.getString(Constants.kCharacteristicUUID).toUpperCase(Locale.getDefault());
			UUID characteristicsUUID = UUID.fromString(UUIDString);
			HashMap<BluetoothGatt, BluetoothGattCharacteristic> peripheralAndCharacteristic = Util.characteristicIn(connectedDevices, characteristicsUUID);
			Set<BluetoothGatt> keySet = peripheralAndCharacteristic.keySet();
			BluetoothGatt gatt = null;
			for (BluetoothGatt bGatt : keySet) {
				gatt = bGatt;
			}
			if (gatt == null) {
				sendPeripheralNotFoundErrorMessage();
				return;
			}
			BluetoothGattCharacteristic characteristic = peripheralAndCharacteristic.get(gatt);
			if (characteristic == null) {
				sendCharacteristicNotFoundErrorMessage();
				return;
			}
			List<JSONObject> descriptorArray = Util.listOfJsonDescriptorsFrom(characteristic.getDescriptors());
			String characteristicUUIDString = characteristic.getUuid().toString().toUpperCase(Locale.getDefault());
			String peripheralUUIDString = gatt.getDevice().getAddress();
			String serviceUUIDString = characteristic.getService().getUuid().toString().toUpperCase(Locale.getDefault());
			JSONObject parameters = new JSONObject();
			JSONObject response = new JSONObject();
			parameters.put(Constants.kCharacteristicUUID,characteristicUUIDString);
			parameters.put(Constants.kPeripheralUUID, peripheralUUIDString);
			parameters.put(Constants.kServiceUUID, serviceUUIDString);
			parameters.put(Constants.kDescriptors, new JSONArray(descriptorArray));
			response.put(Constants.kResult, Constants.kGetDescriptors);
			response.put(Constants.kParams, parameters);
			sendResponse(response);
			return;
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	public void getCharacteristicValue(JSONObject reqObj) {
		try {
			JSONObject jObj = reqObj.getJSONObject(Constants.kParams);
			String uuidString = jObj.getString(Constants.kCharacteristicUUID).toUpperCase(Locale.getDefault());
			UUID characteristicUUID = UUID.fromString(uuidString);
			HashMap<BluetoothGatt, BluetoothGattCharacteristic> characteristics = Util.characteristicIn(connectedDevices, characteristicUUID);
			Set<BluetoothGatt> keySet = characteristics.keySet();
			for (BluetoothGatt bGatt : keySet) {
				BluetoothGattCharacteristic characteristic = characteristics.get(bGatt);
				if (characteristic == null) {
					sendCharacteristicNotFoundErrorMessage();
					return;
				}
				// call to read characteristic
				bGatt.readCharacteristic(characteristic);
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	public void writeCharacteristicValue(JSONObject reqObj) {
		try {
			JSONObject jObj = reqObj.getJSONObject(Constants.kParams);
			String uuidString = jObj.getString(Constants.kCharacteristicUUID).toUpperCase(Locale.getDefault());
			UUID characteristicUUID = UUID.fromString(uuidString);
			HashMap<BluetoothGatt, BluetoothGattCharacteristic> characteristics = Util.characteristicIn(connectedDevices, characteristicUUID);
			Set<BluetoothGatt> keySet = characteristics.keySet();
			for (BluetoothGatt bGatt : keySet) {
				BluetoothGattCharacteristic characteristic = characteristics.get(bGatt);
				if (characteristic == null) {
					sendCharacteristicNotFoundErrorMessage();
					return;
				}
				// call to write characteristic
				byte[] writeData = Util.hexStringToByteArray(jObj.getString(Constants.kValue));
				characteristic.setValue(writeData);
				bGatt.writeCharacteristic(characteristic);
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	public void getValueNotification(JSONObject reqObj) {
		if (notifications)
			notifications = false;
		else
			notifications = true;
		try {
			JSONObject jObj = reqObj.getJSONObject(Constants.kParams);
			String uuidString = jObj.getString(Constants.kCharacteristicUUID).toUpperCase(Locale.getDefault());
			String subscribe = jObj.getString(Constants.kValue);
			Boolean subscribeBOOL;
			if (subscribe.equals("true"))
				subscribeBOOL = true;
			else
				subscribeBOOL = false;
			UUID characteristicUUID = UUID.fromString(uuidString);
			HashMap<BluetoothGatt, BluetoothGattCharacteristic> characteristics = Util.characteristicIn(connectedDevices, characteristicUUID);
			Set<BluetoothGatt> keySet = characteristics.keySet();
			for (BluetoothGatt bGatt : keySet) {
				BluetoothGattCharacteristic characteristic = characteristics.get(bGatt);
				if (characteristic == null) {
					sendCharacteristicNotFoundErrorMessage();
					return;
				}
				// set notification for characteristic
				if (!bGatt.setCharacteristicNotification(characteristic,
						subscribeBOOL)) // returns true
					Log.v("GATT-IP", "Setup failed.");
				// client characteristic configuration.
				UUID descUUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
				BluetoothGattDescriptor desc = characteristic.getDescriptor(descUUID);
				// check whether characteristic having notify or indicate
				// property
				if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0) {
					desc.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
				} else {
					desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
				}

				if (!bGatt.writeDescriptor(desc)) // returns true
					Log.v("GATT-IP", "write error");
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	public void getDescriptorValue(JSONObject reqObj) {
		JSONObject jObj;
		try {
			jObj = reqObj.getJSONObject(Constants.kParams);
			String uuidString = jObj.getString(Constants.kDescriptorUUID).toUpperCase(Locale.getDefault());
			UUID descriptorUUID = UUID.fromString(uuidString);
			HashMap<BluetoothGatt, BluetoothGattDescriptor> descriptors = Util.descriptorIn(connectedDevices, descriptorUUID);
			Set<BluetoothGatt> keySet = descriptors.keySet();
			for (BluetoothGatt gatt : keySet) {
				BluetoothGattDescriptor desc = descriptors.get(keySet);
				if (desc == null) {
					sendDescriptorNotFoundErrorMessage();
					return;
				}
				gatt.readDescriptor(desc);
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	public void getPeripheralState(JSONObject reqObj) {
		JSONObject jObj;
		try {
			jObj = reqObj.getJSONObject(Constants.kParams);
			String address = jObj.getString(Constants.kPeripheralUUID);
			BluetoothGatt gatt = Util.peripheralIn(connectedDevices, address);
			if (gatt == null) {
				sendPeripheralNotFoundErrorMessage();
				return;
			}
			String peripheralState = Util.peripheralStateStringFromPeripheralState(gatt);
			JSONObject respObj = new JSONObject();
			respObj.put(Constants.kStateField, peripheralState);
			respObj.put(Constants.kError, null);
			respObj.put(Constants.kResult, Constants.kGetPeripheralState);
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	public void getRSSI(JSONObject reqObj) {
		JSONObject jObj;
		try {
			jObj = reqObj.getJSONObject(Constants.kParams);
			String address = jObj.getString(Constants.kPeripheralUUID);
			BluetoothGatt gatt = Util.peripheralIn(connectedDevices, address);
			if (gatt == null) {
				sendPeripheralNotFoundErrorMessage();
				return;
			}
			// call to read RSSI values
			gatt.readRemoteRssi();
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	public void sendPeripheralNotFoundErrorMessage() {
		JSONObject errorObj = new JSONObject();
		JSONObject errorResponse = new JSONObject();
		try {
			errorObj.put(Constants.kCode, "" + Constants.kError32001);
			errorResponse.put(Constants.kError, errorObj);
			errorResponse.put(Constants.kJsonrpcVersion, Constants.kJsonrpc);
			sendResponse(errorResponse);
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	public void sendServiceNotFoundErrorMessage() {
		JSONObject errorObj = new JSONObject();
		JSONObject errorResponse = new JSONObject();
		try {
			errorObj.put(Constants.kCode, "" + Constants.kError32002);
			errorResponse.put(Constants.kError, errorObj);
			errorResponse.put(Constants.kJsonrpcVersion, Constants.kJsonrpc);
			sendResponse(errorResponse);
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	public void sendCharacteristicNotFoundErrorMessage() {
		JSONObject errorObj = new JSONObject();
		JSONObject errorResponse = new JSONObject();
		try {
			errorObj.put(Constants.kCode, "" + Constants.kError32003);
			errorResponse.put(Constants.kError, errorObj);
			errorResponse.put(Constants.kJsonrpcVersion, Constants.kJsonrpc);
			sendResponse(errorResponse);
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	public void sendDescriptorNotFoundErrorMessage() {
		JSONObject errorObj = new JSONObject();
		JSONObject errorResponse = new JSONObject();
		try {
			errorObj.put(Constants.kCode, "" + Constants.kError32001);
			errorResponse.put(Constants.kError, errorObj);
			errorResponse.put(Constants.kJsonrpcVersion, Constants.kJsonrpc);
			sendResponse(errorResponse);
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	public boolean isPoweredOn() {
		// checking whether bluetooth is connected or not
		mBlueToothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (mBlueToothAdapter == null) {
			// bluetooth not supported
		}
		// if bluetooth is not enable then enable the bluetooth
		if (mBlueToothAdapter.isEnabled())
			return true;
		else
			return false;
	}

	public void sendReasonForFailedCall() {
		JSONObject errorObj = new JSONObject();
		JSONObject errorResponse = new JSONObject();
		try {
			String errorMessage = Util.centralStateStringFromCentralState(mBlueToothAdapter.getState());
			errorObj.put(Constants.kCode, "" + Constants.kError32001);
			errorObj.put(Constants.kMessageField, errorMessage);
			errorResponse.put(Constants.kError, errorObj);
			errorResponse.put(Constants.kJsonrpcVersion, Constants.kJsonrpc);
			sendResponse(errorResponse);
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Handles logging of Requests and Responses including releasing the oldest
	 * 25% of the Requests/Respnoses if/when the buffer overflows
	 * 
	 * @param input  Response/Request
	 */
	public void handleLoggingRequestAndResponse(JSONObject input)
	{
		if(previousRequests != null)
		{
			if(previousRequests.size() > MAX_NUMBER_OF_REQUESTS)
			{
			for(int i=0;i<MAX_NUMBER_OF_REQUESTS/4.0;i++)
			{
				previousRequests.remove(i);
			}
			}
			previousRequests.add(convertToHumanReadableFormat(input));
		}
		
	}

	public JSONObject convertToHumanReadableFormat(JSONObject input) {
		JSONObject outputRespnose = new JSONObject();
		String humanReadableKey;
		@SuppressWarnings("unchecked")
		Iterator<String> allKeys = input.keys();
		List<String> keys = new ArrayList<String>();
		while (allKeys.hasNext()) {
			keys.add(allKeys.next());
		}
		for (String key : keys) {
			humanReadableKey = Util.humanReadableFormatFromHex(key);
			try {
				Object inputobj = input.get(key);
				if (inputobj instanceof String) {
					String value = input.getString(key);
					String humanReadableValue = Util.humanReadableFormatFromHex(value);
					outputRespnose.put(humanReadableKey, humanReadableValue);
				} else if (inputobj instanceof JSONObject) {
					JSONObject value = (JSONObject) input.get(key);
					JSONObject humanReadableValue;
					humanReadableValue = convertToHumanReadableFormat(value);
					outputRespnose.put(humanReadableKey, humanReadableValue);
				}
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
		return outputRespnose;
	}
	
	public List<JSONObject> listOFResponseAndRequests()
	{
		if(previousRequests.size() >0)
	    return previousRequests;
		else return null;
	}

	public static BroadcastReceiver bReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
				final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
				JSONObject parameters = new JSONObject();
				JSONObject response = new JSONObject();
				String stateString = Util.centralStateStringFromCentralState(state);
				if (!stateString.equals("") && stateString != null) {
					try {
						parameters.put(Constants.kState, stateString);
						response.put(Constants.kParams, parameters);
						response.put(Constants.kResult, Constants.kCentralState);
						response.put(Constants.kJsonrpc,Constants.kJsonrpcVersion);
						sendResponse(response);
					} catch (JSONException e) {
						e.printStackTrace();
					}
				}
			}
		}

		// sending response to client for requested command
		public void sendResponse(JSONObject jsonData) {
			if (listener == null) {
				return;
			}
			try {
				jsonData.put(Constants.kJsonrpc, Constants.kJsonrpcVersion);
			} catch (JSONException e) {
				e.printStackTrace();
			}
			listener.response(jsonData.toString());
		}
	};
}
