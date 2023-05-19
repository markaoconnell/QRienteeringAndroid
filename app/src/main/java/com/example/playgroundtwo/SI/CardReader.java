package com.example.playgroundtwo.SI;

import static java.lang.Math.min;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.playgroundtwo.usbhandler.UsbProber;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class CardReader  {
    public static class CardEntry {

        public long cardId;
        public long initialCardId;
        public long startTime;
        public long finishTime;
        public long checkTime;
        public ArrayList<Punch> punches;

        public CardEntry() {
            punches = new ArrayList<Punch>();
        }
    }

    public enum Event {
        DeviceDetected,
        ReadStarted,
        ReadCanceled,
        Readout
    }
    enum TaskState {
        Probe,
        WaitPerm,
        ProbeSI,
        ReadingCard,
        Quit
    }

    private final int HALF_DAY = 12*3600000;
//    private final String TAG = SIProtocol.class.getSimpleName();
//    private Context context;
    private long zeroTimeWeekDay;
    private long zeroTimeBase;
//    private UsbManager manager;
//    UsbDevice device;
//    private TaskState taskState;
    private SIReader siReader;
    private UsbProber callback;


//    public CardReader(Context context, Calendar zeroTime) {
//        this.context = context;
//        this.manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
//        this.taskState = TaskState.Probe;
//
//        this.zeroTimeBase = zeroTime.get(Calendar.HOUR_OF_DAY)*3600000 + zeroTime.get(Calendar.MINUTE)*60000 + zeroTime.get(Calendar.SECOND)*1000;
//        this.zeroTimeWeekDay = zeroTime.get(Calendar.DAY_OF_WEEK) % 7;
//    }

    public CardReader(SIReader siReader, UsbProber callback) {
        this.siReader = siReader;
        this.callback = callback;
        zeroTimeBase = 0;
        zeroTimeWeekDay = 0;

//        this.context = context;
//        this.manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
//        this.taskState = TaskState.Probe;
//
//        this.zeroTimeBase = zeroTime.get(Calendar.HOUR_OF_DAY)*3600000 + zeroTime.get(Calendar.MINUTE)*60000 + zeroTime.get(Calendar.SECOND)*1000;
//        this.zeroTimeWeekDay = zeroTime.get(Calendar.DAY_OF_WEEK) % 7;
    }

    protected String doInBackground(String... params) {

        /*
        while(this.taskState != TaskState.Quit) {
            if (this.taskState == TaskState.Probe) {
                if (this.probe()) {
                    Log.d(TAG, "Found USB device, waiting for permissions");
                }
            }
            if (this.taskState == TaskState.Probe || this.taskState == TaskState.WaitPerm) {
                try {
                    Thread.sleep(5000);
                }
                catch(InterruptedException e)
                {
                    Log.d(TAG, "thread sleep interrupted");
                }
            }
            if (this.taskState == TaskState.ProbeSI) {
                Log.d(TAG, "Probing for SI device under USB device");
                UsbDeviceConnection conn = manager.openDevice(device);
                if (conn != null) {
                    UsbSerialDevice port = UsbSerialDevice.createUsbSerialDevice(device, conn);
                    this.siReader = new SIReader(port);
                    if (this.siReader.probeDevice()) {
                        // Found device, continue to card reading!
                        SIReader.Info deviceInfo = this.siReader.getDeviceInfo();
                        Log.d(TAG, "Found device (serial: " + deviceInfo.serialNo + "), continue to reading card");
                        this.taskState = TaskState.ReadingCard;
                        this.emitDeviceDetected(deviceInfo);
                    }
                    else {
                        this.taskState = TaskState.Probe;
                    }
                }
            }
            if (this.taskState == TaskState.ReadingCard) {
                this.readCardOnce();
            }
        }

        if(siReader != null) {
            siReader.close();
        }

         */

        return "";
    }

    private boolean probe()
    {
        /*
        HashMap<String, UsbDevice> usbDevices = manager.getDeviceList();
        if (!usbDevices.isEmpty()) {
            for (Map.Entry<String, UsbDevice> entry : usbDevices.entrySet()) {
                device = entry.getValue();
                int deviceVID = device.getVendorId();
                int devicePID = device.getProductId();

                if (UsbSerialDevice.isSupported(device)) {
                    // There is a supported device connected - request permission to access it.
                    // Create intent (used to get USB permissions)
                    this.taskState = TaskState.WaitPerm;
                    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, new Intent(UsbBroadcastReceiver.USB_PERMISSION), 0);
                    IntentFilter filter = new IntentFilter(UsbBroadcastReceiver.USB_PERMISSION);
                    UsbBroadcastReceiver usbReceiver = new UsbBroadcastReceiver(this);
                    context.registerReceiver(usbReceiver, filter);
                    manager.requestPermission(device, pendingIntent);
                    return true;
                } else {
                    device = null;
                }
            }
        }

         */

        return false;
    }

    public CardEntry readCardOnce() throws SiStationDisconnectedException
    {
        CardEntry entry = null;
        byte[] msg;
        byte[] reply;
        SIProtocol proto = siReader.getProtoObj();
        SIReader.SiCardInfo cardInfo = new SIReader.SiCardInfo();

        if (siReader.waitForCardInsert(500, cardInfo, callback)) {
            switch(cardInfo.format) {
                case (byte)0xe5: {
                    entry = new CardEntry();

                    // EMIT card reading
                    this.emitReadStarted(cardInfo);

                    proto.writeMsg((byte) 0xb1, null, true);
                    reply = proto.readMsg(2000, (byte) 0xb1);
                    if (reply != null && card5EntryParse(reply, entry)) {
                        proto.writeAck();
                        // EMIT card read out
                        this.emitReadout(entry);
                    } else {
                        // EMIT card read failed
                        this.emitReadCanceled();
                    }
                    break;
                }
                case (byte)0xe6: {
                    entry = new CardEntry();
                    reply = new byte[7 * 128];

                    // EMIT card reading
                    this.emitReadStarted(cardInfo);

                    msg = new byte[]{0x00};
                    byte[] blocks = new byte[]{0, 6, 7, 2, 3, 4, 5};
                    for (int i = 0; i < 7; i++) {
                        msg[0] = blocks[i];
                        proto.writeMsg((byte) 0xe1, msg, true);
                        byte[] tmpReply = proto.readMsg(2000, (byte) 0xe1);
                        if (tmpReply == null || tmpReply.length != 128 + 6 + 3) {
                            // EMIT card read failed
                            reply = null;
                            this.emitReadCanceled();
                            break;
                        }
                        System.arraycopy(tmpReply, 6, reply, i * 128, 128);
                        if (i > 0) {
                            if (tmpReply[124] == (byte) 0xee &&
                                    tmpReply[125] == (byte) 0xee &&
                                    tmpReply[126] == (byte) 0xee &&
                                    tmpReply[127] == (byte) 0xee) {
                                // Stop reading, no more punches
                                break;
                            }
                        }
                    }
                    if (reply != null && card6EntryParse(reply, entry)) {
                        proto.writeAck();
                        // EMIT card readout
                        this.emitReadout(entry);
                    } else {
                        // EMIT card read failed
                        this.emitReadCanceled();
                    }
                    break;
                }
                case (byte)0xe8: {
                    entry = new CardEntry();

                    // EMIT card reading
                    this.emitReadStarted(cardInfo);

                    msg = new byte[]{0x00};
                    proto.writeMsg((byte) 0xef, msg, true);
                    byte[] tmpReply = proto.readMsg(2000, (byte) 0xef);
                    if (tmpReply == null || tmpReply.length != 128 + 6 + 3) {
                        // EMIT card read failed
                        this.emitReadCanceled();
                        break;
                    }

                    // Add 6 here, don't forget that the initial 6 bytes are protocol bytes
                    int series = tmpReply[24+6] & 0x0f;
                    recordData("FirstBlock:" + series, tmpReply);
                    int nextBlock = 1;
                    int blockCount = 1;
                    if (series == 0x0f) {
                        // siac
                        nextBlock = 4;
                        blockCount = (tmpReply[22] + 31) / 32;
                        // let's try reading differently for these cards - based on the python code
                        // Looks like a read of block 8 will read the entire card
                        reply = new byte[128 * 5];
                        msg[0] = (byte)0x8;
                        proto.writeMsg((byte)0xef, msg, true);
                        tmpReply = proto.readMsg(2000, (byte) 0xef);
                        recordData("S10Block0", tmpReply);
                        System.arraycopy(tmpReply, 6, reply, 0, 128);

                        tmpReply = proto.readMsg(2000, (byte) 0xef);
                        recordData("S10Block4", tmpReply);
                        System.arraycopy(tmpReply, 6, reply, 128 * 1, 128);

                        tmpReply = proto.readMsg(2000, (byte) 0xef);
                        recordData("S10Block5", tmpReply);
                        System.arraycopy(tmpReply, 6, reply, 128 * 2, 128);

                        tmpReply = proto.readMsg(2000, (byte) 0xef);
                        recordData("S10Block6", tmpReply);
                        System.arraycopy(tmpReply, 6, reply, 128 * 3, 128);

                        tmpReply = proto.readMsg(2000, (byte) 0xef);
                        recordData("S10Block7", tmpReply);
                        System.arraycopy(tmpReply, 6, reply, 128 * 4, 128);
                    }
                    else {
                        reply = new byte[128 * (1 + blockCount)];
                        System.arraycopy(tmpReply, 6, reply, 0, 128);

                        for (int i = nextBlock; i < nextBlock + blockCount; i++) {
                            msg[0] = (byte) i;
                            proto.writeMsg((byte) 0xef, msg, true);
                            tmpReply = proto.readMsg(2000, (byte) 0xef);
                            if (tmpReply == null || tmpReply.length != 128 + 6 + 3) {
                                // EMIT card read failed
                                reply = null;
                                this.emitReadCanceled();
                                break;
                            }
                            recordData("SI10Block" + i, tmpReply);
                            System.arraycopy(tmpReply, 6, reply, (i - nextBlock + 1) * 128, 128);
                        }
                    }
                    if (reply != null && card9EntryParse(reply, entry)) {
                        proto.writeAck();
                        // EMIT card read out
                        this.emitReadout(entry);
                    } else {
                        // EMIT card read failed
                        this.emitReadCanceled();
                    }
                    break;
                }
                default:
                    break;
            }
        }

        if (entry != null) {
            if (entry.cardId == 0) {
                entry.initialCardId = cardInfo.cardId;
            } else {
                entry.initialCardId = entry.cardId;
            }
        }

        return entry;
    }


    // package private testing method
    boolean parseArtificialCardData(int format, byte [] data, CardEntry entry) {
        boolean cardParsedCorrectly = false;
        switch((byte) format) {
            case (byte)0xe5: {
                cardParsedCorrectly = card5EntryParse(data, entry);
                break;
            }
            case (byte)0xe6: {
                cardParsedCorrectly = card6EntryParse(data, entry);
                break;
            }
            case (byte)0xe8: {
               cardParsedCorrectly = card9EntryParse(data, entry);
                break;
            }
            default:
                break;
        }

        return (cardParsedCorrectly);
    }

    private boolean card5EntryParse(byte[] data, CardEntry entry)
    {
        boolean ret = false;
        int offset = 0;

        recordData("Card5", data);

        if (data.length == 136) {
            // Start at data part
            offset += 5;
            // Get cardId
            entry.cardId = ((byteToUnsignedInt(data[offset + 4]) << 8) + byteToUnsignedInt(data[offset + 5]));
            int highByte = byteToUnsignedInt(data[offset + 6]);
            if ((highByte >= 2) && (highByte <= 5)) {
                entry.cardId += (highByte * 100000);
            }

            entry.startTime = (byteToUnsignedInt(data[offset+19]) << 8) + byteToUnsignedInt(data[offset+20]);
            entry.finishTime = (byteToUnsignedInt(data[offset+21]) << 8) + byteToUnsignedInt(data[offset+22]);
            entry.checkTime = (byteToUnsignedInt(data[offset+25]) << 8) + byteToUnsignedInt(data[offset+26]);
            int punchCount = byteToUnsignedInt(data[offset+23]) - 1;
            for (int i=0; i<punchCount && i<30; i++) {
                Punch punch = new Punch();
                int baseoffset = offset + 32 + (i/5)*16 + 1 + 3*(i%5);
                punch.code = byteToUnsignedInt(data[baseoffset]);
                punch.time = (byteToUnsignedInt(data[baseoffset+1]) << 8) + byteToUnsignedInt(data[baseoffset+2]);
                entry.punches.add(punch);
            }

            // Handle non-timed punches - this will confuse things but I guess ok??
            for (int i=30; i<punchCount; i++) {
                Punch punch = new Punch();
                int baseoffset = offset + 32 + (i-30)*16;
                punch.code = data[baseoffset];
                punch.time = 0;
                entry.punches.add(punch);
            }

            card5TimeAdjust(entry);

            ret = true;
        }

        return ret;
    }

    private boolean card6EntryParse(byte[] data, CardEntry entry)
    {
        recordData("Card6", data);

        entry.cardId = (byteToUnsignedInt(data[10]) << 24) | (byteToUnsignedInt(data[11]) << 16) | (byteToUnsignedInt(data[12]) << 8) | byteToUnsignedInt(data[13]);

        Punch startPunch = new Punch();
        Punch finishPunch = new Punch();
        Punch checkPunch = new Punch();
        parsePunch(Arrays.copyOfRange(data, 24, 28), startPunch);
        parsePunch(Arrays.copyOfRange(data, 20, 24), finishPunch);
        parsePunch(Arrays.copyOfRange(data, 28, 32), checkPunch);
        entry.startTime = startPunch.time;
        entry.finishTime = finishPunch.time;
        entry.checkTime = checkPunch.time;

        int punches = min(data[18], 192);
        for (int i=0; i<punches; i++) {
            Punch tmpPunch = new Punch();
            if (parsePunch(Arrays.copyOfRange(data, 128+4*i, 128+4*i+4), tmpPunch)) {
                entry.punches.add(tmpPunch);
            }
        }
        return true;
    }

    private boolean card9EntryParse(byte[] data, CardEntry entry)
    {
        entry.cardId = (byteToUnsignedInt(data[25]) << 16) | (byteToUnsignedInt(data[26]) << 8) | byteToUnsignedInt(data[27]);
        int series = data[24] & 0x0f;

        recordData("Card9-" + series, data);


        Punch startPunch = new Punch();
        Punch finishPunch = new Punch();
        Punch checkPunch = new Punch();
        parsePunch(Arrays.copyOfRange(data, 12, 16), startPunch);
        parsePunch(Arrays.copyOfRange(data, 16, 20), finishPunch);
        parsePunch(Arrays.copyOfRange(data, 8, 12), checkPunch);
        entry.startTime = startPunch.time;
        entry.finishTime = finishPunch.time;
        entry.checkTime = checkPunch.time;

        if (series == 1) {
            // SI card 9
            int punches = min(data[22], 50);
            for (int i=0; i<punches; i++) {
                Punch tmpPunch = new Punch();
                if (parsePunch(Arrays.copyOfRange(data, 14*4+4*i, 14*4+4*i+4), tmpPunch)) {
                    entry.punches.add(tmpPunch);
                }
            }
        }
        else if(series == 2) {
            // SI card 8
            int punches = min(data[22], 30);
            for (int i=0; i<punches; i++) {
                Punch tmpPunch = new Punch();
                if (parsePunch(Arrays.copyOfRange(data, 34*4+4*i, 34*4+4*i+4), tmpPunch)) {
                    entry.punches.add(tmpPunch);
                }
            }
        }
        else if(series == 4) {
            // pCard
            int punches = min(data[22], 20);
            for (int i=0; i<punches; i++) {
                Punch tmpPunch = new Punch();
                if (parsePunch(Arrays.copyOfRange(data, 44*4+4*i, 44*4+4*i+4), tmpPunch)) {
                    entry.punches.add(tmpPunch);
                }
            }
        }
        else if(series == 15) {
            // SI card 10, 11, siac
            int punches = min(data[22], 128);
            for (int i=0; i<punches; i++) {
                Punch tmpPunch = new Punch();
                if (parsePunch(Arrays.copyOfRange(data, 128+4*i, 128+4*i+4), tmpPunch)) {
                    entry.punches.add(tmpPunch);
                }
            }
        }

        return true;
    }

    private void card5TimeAdjust(CardEntry entry)
    {
        long pmOffset = (zeroTimeBase >= HALF_DAY) ? HALF_DAY : 0;

        if (entry.startTime != 0) {
            entry.startTime = entry.startTime * 1000 + pmOffset;
            if (entry.startTime < zeroTimeBase) {
                entry.startTime += HALF_DAY;
            }
            entry.startTime -= zeroTimeBase;
        }
        if (entry.checkTime != 0) {
            entry.checkTime = entry.checkTime * 1000 + pmOffset;
            if (entry.checkTime < zeroTimeBase) {
                entry.checkTime += HALF_DAY;
            }
            entry.checkTime -= zeroTimeBase;
        }
        long currentBase = pmOffset;
        long lastTime = zeroTimeBase;
        for (Punch punch : entry.punches) {
            long tmpTime = punch.time * 1000 + currentBase;
            if (tmpTime < lastTime) {
                currentBase += HALF_DAY;
            }
            tmpTime = punch.time * 1000 + currentBase;
            punch.time = tmpTime - zeroTimeBase;
            lastTime = tmpTime;
        }
        long tmpTime = entry.finishTime * 1000 + currentBase;
        if (tmpTime < lastTime) {
            currentBase += HALF_DAY;
        }
        tmpTime = entry.finishTime * 1000 + currentBase;
        entry.finishTime = tmpTime - zeroTimeBase;
    }

    private boolean parsePunch(byte[] data, Punch punch)
    {
        if (data[0] == (byte)0xee && data[1] == (byte)0xee && data[2] == (byte)0xee && data[3] == (byte)0xee) {
            return false;
        }
        punch.code = byteToUnsignedInt(data[1]) + 256*((byteToUnsignedInt(data[0])>>6) & 0x03);

        long basetime = ((byteToUnsignedInt(data[2]) << 8) | byteToUnsignedInt(data[3])) * 1000;
        if ((data[0] & 0x01) == 0x01) {
            basetime += HALF_DAY;
        }
        int dayOfWeek = (data[0] >> 1) & 0x07;
        if (dayOfWeek < zeroTimeWeekDay) {
            dayOfWeek += 7;
        }
        dayOfWeek -= zeroTimeWeekDay;
        basetime += dayOfWeek * 24 * 3600 * 1000;
        basetime -= zeroTimeBase;

        punch.time = basetime;

        return true;
    }

    private void emitDeviceDetected(SIReader.Info deviceInfo) {
//        Intent intent = new Intent(EVENT_IDENTIFIER);
//        intent.putExtra("Event", Event.DeviceDetected);
//        intent.putExtra("Serial", deviceInfo.serialNo);
//        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    private void emitReadStarted(SIReader.SiCardInfo cardInfo) {
//        Intent intent = new Intent(EVENT_IDENTIFIER);
//        intent.putExtra("Event", Event.ReadStarted);
//        intent.putExtra("CardId", cardInfo.cardId);
//        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    private void emitReadCanceled() {
//        Intent intent = new Intent(EVENT_IDENTIFIER);
//        intent.putExtra("Event", Event.ReadCanceled);
//        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    private void emitReadout(CardEntry entry) {
//        Intent intent = new Intent(EVENT_IDENTIFIER);
//        intent.putExtra("Event", Event.Readout);
//        intent.putExtra("Entry", entry);
//        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    private static int byteToUnsignedInt(byte in)
    {
        return in & 0xff;
    }

    private boolean debug = false;
    private void recordData(String identifier, byte[] dataToRecord) {
        if (debug) {
           // ArrayList<String> decimalString = new ArrayList<>();
            ArrayList<String> hexString = new ArrayList<>();

            for (int i = 0; i < dataToRecord.length; i++) {
                hexString.add(String.format("0x%x", dataToRecord[i]));
               // decimalString.add(String.format("%d", dataToRecord[i]));
            }

            Log.d(UsbProber.myLogId, identifier + ": " + hexString.stream().collect(Collectors.joining(",")));
            //Log.d(UsbProber.myLogId, identifier + ": " + decimalString.stream().collect(Collectors.joining(",")));
        }
    }
}

