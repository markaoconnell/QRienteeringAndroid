package com.moconnell.qrienteering.SI;

import android.util.Log;

import com.moconnell.qrienteering.util.LogUtil;
import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.io.IOException;


public class SIReader {
    enum DeviceType {
        Unknown(0x00),
        Control(0x02),
        Start(0x03),
        Finish(0x04),
        Read(0x05),
        ClearStartNbr(0x06),
        Clear(0x07),
        Check(0x0a);

        private int num;

        public int getNum() {
            return this.num;
        }

        DeviceType(int num) {
            this.num = num;
        }
    }

    public static class Info {
        public DeviceType type;
        public boolean extendedMode;
        public int codeNo;
        public long serialNo;
    }

    public static class SiCardInfo {
        public long cardId;
        public byte format;
    }

    public class DeviceEntry
    {
        public String identifier;
        public String osName;
        public Info deviceInfo;
    }

    private UsbSerialPort serialPort;

    private SIProtocol siStationIoHandler;
    private Info deviceInfo;

    public SIReader(UsbSerialPort serialPort)
    {
        this.serialPort = serialPort;
    }

    public void close()
    {
        if (serialPort != null) {
            try {
                serialPort.close();
            }
            catch (IOException ioe) {
                // not really sure what to do here
            }
        }
        serialPort = null;
        siStationIoHandler = null;
        deviceInfo = null;
    }

    public boolean isConnected()
    {
        return (serialPort != null);
    }

    public Info getDeviceInfo()
    {
        return deviceInfo;
    }

    public SIProtocol getProtoObj()
    {
        return siStationIoHandler;
    }

    public boolean waitForCardInsert(int timeout, SiCardInfo cardInfo, SIStationStatusUpdateCallback callback) throws SiStationDisconnectedException
    {
        if (siStationIoHandler == null) {
            return false;
        }

        byte[] reply = siStationIoHandler.readMsg(timeout);
        if (reply != null && reply.length > 0) {
            switch(reply[1]) {
                case (byte) 0xe5:
                    // SI5 card
                    cardInfo.cardId = ((byteToUnsignedInt(reply[7]) << 8) + byteToUnsignedInt(reply[8]));
                    int highByte = byteToUnsignedInt(reply[6]);
                    if ((highByte >= 2) && (highByte <= 5)) {
                        cardInfo.cardId += (highByte * 100000);
                    }
                    cardInfo.format = reply[1];
                    Log.d(LogUtil.myLogId, "Got card inserted event (CardID: " + cardInfo.cardId + ")");
//                    callback.updateStatus("Got SI 5 card inserted event (CardID: " + cardInfo.cardId + ")");
                    return true;

                case (byte) 0xe6:
                case (byte) 0xe8:
                    cardInfo.cardId = (byteToUnsignedInt(reply[6]) << 16) + (byteToUnsignedInt(reply[7]) << 8) + byteToUnsignedInt(reply[8]);
                    cardInfo.format = reply[1];
                    Log.d(LogUtil.myLogId, "Got card inserted event (CardID: " + cardInfo.cardId + ")");
                    //callback.updateStatus("Got card inserted event (CardID: " + cardInfo.cardId + ")");
                    return true;
                case (byte) 0xe7:
                    int tmpCardId = ((byteToUnsignedInt(reply[7]) << 8) + byteToUnsignedInt(reply[8]));
                    int upperByte = byteToUnsignedInt(reply[6]);
                    if ((upperByte >= 2) && (upperByte <= 5)) {
                        tmpCardId += (upperByte * 100000);
                    }
                    else if (upperByte > 5) {
                        tmpCardId += upperByte << 16;
                    }
                    Log.d(LogUtil.myLogId, "Got card removed event (CardID: " + tmpCardId + ")");
                    //callback.updateStatus("Got card removed event (CardID: " + tmpCardId + ")");
                    break;
                default:
                    int unknownByte = byteToUnsignedInt(reply[1]);
                    Log.d(LogUtil.myLogId, String.format("Got unknown command (%d, 0x%x) waiting for card inserted event", unknownByte, unknownByte));
                    //callback.updateStatus("Got unknown command waiting for card inserted event");
                    break;
            }
        }

        return false;
    }

    public void sendAck()
    {
        if (siStationIoHandler != null) {
            siStationIoHandler.writeAck();
        }
    }

    public void sendNak()
    {
        if (siStationIoHandler != null) {
            siStationIoHandler.writeNak();
        }
    }

    public boolean probeDevice(SIStationStatusUpdateCallback statusCallback) throws SiStationDisconnectedException
    {

        boolean ret = false;
        byte[] msg;
        byte[] reply;

        siStationIoHandler = new SIProtocol(serialPort, statusCallback);

        // Start with determine baudrate
        if (!setBaudRate(38400)){
            //callback.updateStatus("Failed to set baudRate to 38400");
            Log.d(LogUtil.myLogId, "Failed to set baudRate to 38400");
            return false;
        }

        //callback.updateStatus("Writing first command");
        msg = new byte[]{0x4d};
        int result = siStationIoHandler.writeMsg((byte)0xf0, msg, true);
        //callback.updateStatus(String.format("Reading first reply, write response was %d", result));
        reply = siStationIoHandler.readMsg(1000, (byte)0xf0);
        //callback.updateStatus(String.format("First read returned, %d bytes read", (reply != null) ? reply.length : -1));
        if (reply == null || reply.length == 0) {
           // callback.updateStatus("First reply was empty");
            Log.d(LogUtil.myLogId, "No response on high baudrate mode, trying low baudrate");
            if (!setBaudRate(4800)) {
                //callback.updateStatus("Failed to set baudrate to 4800");
                Log.d(LogUtil.myLogId, "Failed to set baudRate to 4800");
                return false;
            }
        }

        siStationIoHandler.writeMsg((byte)0xf0, msg, true);
        reply = siStationIoHandler.readMsg(1000, (byte)0xf0);
        //callback.updateStatus(String.format("Second read returned, %d bytes read", (reply != null) ? reply.length : -1));
        if (reply != null && reply.length > 0) {
            Log.d(LogUtil.myLogId, "Unit responded, reading device info");
            //callback.updateStatus("Unit responded, reading device info");
            msg = new byte[]{0x00, 0x75};
            siStationIoHandler.writeMsg((byte) 0x83, msg, true);
            reply = siStationIoHandler.readMsg(6000, (byte) 0x83);

            if (reply != null && reply.length >= 124) {
                Log.d(LogUtil.myLogId, "Got device info response");
                //callback.updateStatus("Got device info response");
                deviceInfo = new Info();
                deviceInfo.codeNo = (byteToUnsignedInt(reply[3]) << 8) + byteToUnsignedInt(reply[4]);
                deviceInfo.type = DeviceType.values()[reply[119]];
                deviceInfo.extendedMode = (reply[122] & 0x01) == 0x01;
                deviceInfo.serialNo = (byteToUnsignedInt(reply[6]) << 24) + (byteToUnsignedInt(reply[7]) << 16) + (byteToUnsignedInt(reply[8]) << 8) + byteToUnsignedInt(reply[9]);
                ret = true;
            } else {
                Log.d(LogUtil.myLogId, "Invalid device info response, trying short info");
                //callback.updateStatus("Invalid device info response, trying short info");

                msg = new byte[]{0x00, 0x07};
                siStationIoHandler.writeMsg((byte) 0x83, msg, true);
                reply = siStationIoHandler.readMsg(6000, (byte)0x83);

                if (reply != null && reply.length >= 10) {
                    Log.d(LogUtil.myLogId, "Got device info response");
                   // callback.updateStatus("Got device info the short way");
                    deviceInfo = new Info();
                    deviceInfo.codeNo = (byteToUnsignedInt(reply[3]) << 8) + byteToUnsignedInt(reply[4]);
                    deviceInfo.type = DeviceType.Unknown;
                    deviceInfo.extendedMode = false;
                    deviceInfo.serialNo = (byteToUnsignedInt(reply[6]) << 24) + (byteToUnsignedInt(reply[7]) << 16) + (byteToUnsignedInt(reply[8]) << 8) + byteToUnsignedInt(reply[9]);
                    ret = true;
                }
                else {
                    Log.d(LogUtil.myLogId, "No short info response received, failing open.");
                }
            }
        }
        else {
            Log.d(LogUtil.myLogId, "No device info response received, failing open.");
        }

        if (!ret) {
            close();
        }

        return ret;
    }

    private static int byteToUnsignedInt(byte in)
    {
        return in & 0xff;
    }

    private boolean setBaudRate(int baudRate) {
        try {
            serialPort.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
        }
        catch (IOException ioe) {
            return false;
        }

        return true;
    }
}

