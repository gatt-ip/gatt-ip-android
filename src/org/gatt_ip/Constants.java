package org.gatt_ip;

public class Constants {
	
	//JSON-RPC Constants
	public static final String kJsonrpcVersion                     = "2.0";
	public static final String kJsonrpc                            = "jsonrpc";
	public static final String kMethod                             = "method";
	public static final String kParams                             = "params";
	public static final String kError                              = "error";
	public static final String kCode                               = "code";
	public static final String kMessageField                       = "message";
	public static final String kResult                             = "result";
	public static final String kIdField                            = "id";

	//-------------------------------------- Methods ----------------------------------------
	//Central Methods
	public static final String kConfigure                          = "aa";
	public static final String kCentralState                       = "af";
	public static final String kScanForPeripherals                 = "ab";
	public static final String kStopScanning                       = "ac";
	public static final String kConnect                            = "ad";
	public static final String kDisconnect                         = "ae";
	public static final String kGetConnectedPeripherals            = "ag";
	public static final String kGetPerhipheralsWithServices        = "ah";
	public static final String kGetPerhipheralsWithIdentifiers     = "ai";

	//Peripheral Methods
	public static final String kGetServices                        = "ak";
	public static final String kGetIncludedServices                = "al";
	public static final String kGetCharacteristics                 = "am";
	public static final String kGetDescriptors                     = "an";
	public static final String kGetCharacteristicValue             = "ao";
	public static final String kGetDescriptorValue                 = "ap";
	public static final String kWriteCharacteristicValue           = "aq";
	public static final String kWriteDescriptorValue               = "ar";
	public static final String kSetValueNotification               = "as";
	public static final String kGetPeripheralState                 = "at";
	public static final String kGetRSSI                            = "au";
	public static final String kInvalidatedServices                = "av";
	public static final String kPeripheralNameUpdate               = "aw";
	public static final String kMessage                            = "zz";

	//-------------------------------------- Keys ----------------------------------------
	public static final String kCentralUUID                        = "ba";
	public static final String kPeripheralUUID                     = "bb";
	public static final String kPeripheralName                     = "bc";
	public static final String kPeripheralUUIDs                    = "bd";
	public static final String kServiceUUID                        = "be";
	public static final String kServiceUUIDs                       = "bf";
	public static final String kPeripherals                        = "bg";
	public static final String kIncludedServiceUUIDs               = "bh";
	public static final String kCharacteristicUUID                 = "bi";
	public static final String kCharacteristicUUIDs                = "bj";
	public static final String kDescriptorUUID                     = "bk";
	public static final String kServices                           = "bl";
	public static final String kCharacteristics                    = "bm";
	public static final String kDescriptors                        = "bn";
	public static final String kProperties                         = "bo";
	public static final String kValue                              = "bp";
	public static final String kState                              = "bq";
	public static final String kStateInfo                          = "br";
	public static final String kStateField                         = "bs";
	public static final String kWriteType                          = "bt";

	public static final String kRSSIkey                            = "bu";
	public static final String kIsPrimaryKey                       = "bv";
	public static final String kIsBroadcasted                      = "bw";
	public static final String kIsNotifying                        = "bx";

	public static final String kShowPowerAlert                     = "by";
	public static final String kIdentifierKey                      = "bz";
	public static final String kScanOptionAllowDuplicatesKey       = "b0";
	public static final String kScanOptionSolicitedServiceUUIDs    = "b1";

	//Advertisment Data for Peripheral Keys
	public static final String kAdvertisementDataKey                           = "b2";
	public static final String kCBAdvertisementDataManufacturerDataKey         = "b3";
	public static final String kCBAdvertisementDataServiceUUIDsKey             = "b4";
	public static final String kCBAdvertisementDataServiceDataKey              = "b5";
	public static final String kCBAdvertisementDataOverflowServiceUUIDsKey     = "b6";
	public static final String kCBAdvertisementDataSolicitedServiceUUIDsKey    = "b7";
	public static final String kCBAdvertisementDataIsConnectable               = "b8";
	public static final String kCBAdvertisementDataTxPowerLevel                = "b9";
	public static final String kPeripheralBtAddress                            = "c1";
    public static final String kRawAdvertisementData                           = "c2";
    public static final String kScanRecord                                     = "c3";


    //Will Restore State Keys
	public static final String kCBCentralManagerRestoredStatePeripheralsKey    = "da";
	public static final String kCBCentralManagerRestoredStateScanServicesKey   = "db";

	//----------------------------------------- Values ------------------------------------------------
	//Characteristic Write types
	public static final String kWriteWithResponse                = "cc";
	public static final String kWriteWithoutResponse             = "cd";
	public static final String kNotifyOnConnection               = "ce";
	public static final String kNotifyOnDisconnection            = "cf";
	public static final String kNotifyOnNotification             = "cg";

	//Peripheral States
	public static final String kDisconnected                     = "ch";
	public static final String kConnecting                       = "ci";
	public static final String kConnected                        = "cj";

	//Centeral States
	public static final String kUnknown                          = "ck";
	public static final String kResetting                        = "cl";
	public static final String kUnsupported                      = "cm";
	public static final String kUnauthorized                     = "cn";
	public static final String kPoweredOff                       = "co";
	public static final String kPoweredOn                        = "cp";

	//----------------------------------------- Error Values ------------------------------------------------
	public static final String kError32001                     = "-32001";//Peripheral not Found
	public static final String kError32002                     = "-32002";//Service not found
	public static final String kError32003                     = "-32003";//Characteristic Not Found
	public static final String kError32004                     = "-32004";//Descriptor not found
	public static final String kError32005                     = "-32005";//Peripheral State is Not valid(not Powered On)
	public static final String kError32006                     = "-32006";//No Service Specified
	public static final String kError32007                     = "-32007";//No Peripheral Identifer specified
	public static final String kError32008                     = "-32008";//State restoration is only allowed with "bluetooth-central" background mode enabled

	public static final String kInvalidRequest                 = "-32600";
	public static final String kMethodNotFound                 = "-32601";
	public static final String kInvalidParams                  = "-32602";
	public static final String kError32603                     = "-32603";
	public static final String kParseError                     = "-32700";

    public static final String kGAP_ADTYPE_FLAGS                            = "01";
    public static final String kGAP_ADTYPE_INCOMPLETE_16BIT_SERVICEUUID     = "02";
    public static final String kGAP_ADTYPE_COMPLETE_16BIT_SERVICEUUID       = "03";
    public static final String kGAP_ADTYPE_INCOMPLETE_32BIT_SERVICEUUID     = "04";
    public static final String kGAP_ADTYPE_COMPLETE_32BIT_SERVICEUUID       = "05";
    public static final String kGAP_ADTYPE_INCOMPLETE_128BIT_SERVICEUUID    = "06";
    public static final String kGAP_ADTYPE_COMPLETE_128BIT_SERVICEUUID      = "07";
    public static final String kGAP_ADTYPE_POWER_LEVEL                      = "0A";
    public static final String kGAP_ADTYPE_MANUFACTURER_SPECIFIC            = "FF";
}
