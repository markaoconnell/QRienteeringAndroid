package com.example.playgroundtwo.SI;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbManager;
import android.util.Log;

import com.example.playgroundtwo.MainActivity;
import com.example.playgroundtwo.usbhandler.UsbProber;
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

    // logging tag
    private static final String TAG = SIReader.class.getSimpleName();
    private static final String ACTION_USB_PERMISSION = "com.svenstorp.siplayground.USB_PERMISSION";

    private UsbSerialPort serialPort;

    private SIProtocol siprot;
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
        siprot = null;
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
        return siprot;
    }

    public boolean waitForCardInsert(int timeout, SiCardInfo cardInfo, UsbProber callback)
    {
        if (siprot == null) {
            return false;
        }

        byte[] reply = siprot.readMsg(timeout);
        if (reply != null && reply.length > 0) {
            switch(reply[1]) {
                case (byte) 0xe5:
                case (byte) 0xe6:
                case (byte) 0xe8:
                    cardInfo.cardId = (byteToUnsignedInt(reply[6]) << 16) + (byteToUnsignedInt(reply[7]) << 8) + byteToUnsignedInt(reply[8]);
                    cardInfo.format = reply[1];
                    Log.d(TAG, "Got card inserted event (CardID: " + cardInfo.cardId + ")");
                    //callback.updateStatus("Got card inserted event (CardID: " + cardInfo.cardId + ")");
                    return true;
                case (byte) 0xe7:
                    int tmpCardId = (byteToUnsignedInt(reply[5]) << 24) + (byteToUnsignedInt(reply[6]) << 16) + (byteToUnsignedInt(reply[7]) << 8) + byteToUnsignedInt(reply[8]);
                    Log.d(TAG, "Got card removed event (CardID: " + tmpCardId + ")");
                    //callback.updateStatus("Got card removed event (CardID: " + tmpCardId + ")");
                    break;
                default:
                    Log.d(TAG, "Got unknown command waiting for card inserted event");
                    //callback.updateStatus("Got unknown command waiting for card inserted event");
                    break;
            }
        }

        return false;
    }

    public void sendAck()
    {
        if (siprot != null) {
            siprot.writeAck();
        }
    }

    public void sendNak()
    {
        if (siprot != null) {
            siprot.writeNak();
        }
    }

    public boolean probeDevice(UsbProber callback)
    {

        boolean ret = false;
        byte[] msg;
        byte[] reply;

        siprot = new SIProtocol(serialPort, callback);

/*
        siReaderDevice.setDataBits(UsbSerialInterface.DATA_BITS_8);
        siReaderDevice.setParity(UsbSerialInterface.PARITY_NONE);
        siReaderDevice.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
 */

        // Start with determine baudrate
        if (!setBaudRate(38400)){
            callback.updateStatus("Failed to set baudRate to 38400");
            return false;
        }

        //callback.updateStatus("Writing first command");
        msg = new byte[]{0x4d};
        int result = siprot.writeMsg((byte)0xf0, msg, true);
        //callback.updateStatus(String.format("Reading first reply, write response was %d", result));
        reply = siprot.readMsg(1000, (byte)0xf0);
        //callback.updateStatus(String.format("First read returned, %d bytes read", (reply != null) ? reply.length : -1));
        if (reply == null || reply.length == 0) {
           // callback.updateStatus("First reply was empty");
            Log.d(TAG, "No response on high baudrate mode, trying low baudrate");
            if (!setBaudRate(4800)) {
                callback.updateStatus("Failed to set baudrate to 4800");
                return false;
            }
        }

        siprot.writeMsg((byte)0xf0, msg, true);
        reply = siprot.readMsg(1000, (byte)0xf0);
        //callback.updateStatus(String.format("Second read returned, %d bytes read", (reply != null) ? reply.length : -1));
        if (reply != null && reply.length > 0) {
            Log.d(TAG, "Unit responded, reading device info");
            //callback.updateStatus("Unit responded, reading device info");
            msg = new byte[]{0x00, 0x75};
            siprot.writeMsg((byte) 0x83, msg, true);
            reply = siprot.readMsg(6000, (byte) 0x83);

            if (reply != null && reply.length >= 124) {
                Log.d(TAG, "Got device info response");
                callback.updateStatus("Got device info response");
                deviceInfo = new Info();
                deviceInfo.codeNo = (byteToUnsignedInt(reply[3]) << 8) + byteToUnsignedInt(reply[4]);
                deviceInfo.type = DeviceType.values()[reply[119]];
                deviceInfo.extendedMode = (reply[122] & 0x01) == 0x01;
                deviceInfo.serialNo = (byteToUnsignedInt(reply[6]) << 24) + (byteToUnsignedInt(reply[7]) << 16) + (byteToUnsignedInt(reply[8]) << 8) + byteToUnsignedInt(reply[9]);
                ret = true;
            } else {
                Log.d(TAG, "Invalid device info response, trying short info");
                //callback.updateStatus("Invalid device info response, trying short info");

                msg = new byte[]{0x00, 0x07};
                siprot.writeMsg((byte) 0x83, msg, true);
                reply = siprot.readMsg(6000, (byte)0x83);

                if (reply != null && reply.length >= 10) {
                    Log.d(TAG, "Got device info response");
                    callback.updateStatus("Got device info the short way");
                    deviceInfo = new Info();
                    deviceInfo.codeNo = (byteToUnsignedInt(reply[3]) << 8) + byteToUnsignedInt(reply[4]);
                    deviceInfo.type = DeviceType.Unknown;
                    deviceInfo.extendedMode = false;
                    deviceInfo.serialNo = (byteToUnsignedInt(reply[6]) << 24) + (byteToUnsignedInt(reply[7]) << 16) + (byteToUnsignedInt(reply[8]) << 8) + byteToUnsignedInt(reply[9]);
                    ret = true;
                }
            }
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

