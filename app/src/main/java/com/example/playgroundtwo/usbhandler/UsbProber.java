package com.example.playgroundtwo.usbhandler;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.util.Log;

import com.example.playgroundtwo.MainActivity;
import com.example.playgroundtwo.SI.CardReader;
import com.example.playgroundtwo.SI.SIReader;
import com.hoho.android.usbserial.driver.Cp21xxSerialDriver;
import com.hoho.android.usbserial.driver.ProbeTable;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class UsbProber extends Thread {

    private static boolean usbPermissionGranted = false;

    private Handler handler;
    private UsbProberCallback callback;

    private MainActivity mainActivity;

    private boolean stopRunning = false;

    public UsbProber(MainActivity activity) {
        mainActivity = activity;
    }

    public void setHandler(Handler completionHandler) {
        this.handler = completionHandler;
    }

    public void setCallback(UsbProberCallback c) {
        this.callback = c;
    }

    protected void notifyListeners(String notificationString) {
        if (callback != null) {
            if (handler != null) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.OnInfoFound(notificationString);
                    }
                });
            } else {
                callback.OnInfoFound(notificationString);
            }
        }
    }

    public void updateStatus(String message) {
        Log.i(myLogId, message);
        notifyListeners(message);
    }

    public void run() {
       // probeForUSB();
        probeForUSBNewPackage();
    }

    public void stopRunning() {
        this.stopRunning = true;
    }



    private static final String ACTION_USB_PERMISSION =
            "com.android.example.USB_PERMISSION";

    private final static String myLogId = "MOC_QR_UsbProber";

    /*
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            probeDevice(device, true);
                        }
                    }
                    else {
                        probeDevice(device, false);
                    }
                }
            }
        }
    };
    */


    private void probeForUSBNewPackage() {
        UsbManager manager = (UsbManager) mainActivity.getSystemService(Context.USB_SERVICE);

        // Probe for the SI reader device, which use the VendorID and ProductID below.
        ProbeTable customTable = new ProbeTable();
        customTable.addProduct(4292, 32778, Cp21xxSerialDriver.class);
        //customTable.addProduct(0x1234, 0x0002, FtdiSerialDriver.class);

        UsbSerialProber prober = new UsbSerialProber(customTable);
        List<UsbSerialDriver> drivers = prober.findAllDrivers(manager);

        if (drivers.size() == 0) {
            updateStatus("Did not find any compatible drivers");
            return;
        }
        else {
            //updateStatus(String.format("Found %d compatible drivers (I hope), trying one", drivers.size()));
        }

        // Open a connection to the first available driver.
        UsbSerialDriver driver = drivers.get(0);
        updateStatus(String.format("Found %s, opening it", driver.getDevice().getProductName()));
        UsbDeviceConnection connection = manager.openDevice(driver.getDevice());
        if (connection == null) {
            updateStatus("probeForUSBNewPackage - could not get device connection");
            return;
        }

        UsbSerialPort port = driver.getPorts().get(0); // Most devices have just one port (port 0)
        if (port == null) {
            updateStatus("driver.getPorts().get(0) returned null");
            return;
        }


        try {
            port.open(connection);
        }
        catch (IOException ioe) {
            updateStatus("Failed to open port - " + ioe.getMessage());
            return;
        }

        SIReader reader = new SIReader(port);
        if (reader.probeDevice(this)) {
            updateStatus("Waiting for card insert");
            CardReader cardReader = new CardReader(reader, this);
            CardReader.CardEntry siCard = null;
//            SIReader.SiCardInfo siCard = new SIReader.SiCardInfo();
            while (!this.stopRunning) {
                siCard = cardReader.readCardOnce();
                if (siCard != null) {
                    String punchString = siCard.punches.stream().map(punch -> (punch.getCode() + ":" + punch.getTime())).collect(Collectors.joining(","));
                    updateStatus(String.format("Read card %d, start %d, finish %d, punches: %s", siCard.cardId, siCard.startTime, siCard.finishTime, punchString));
                }
            }
        }
        else {
            updateStatus("Device probe failed, exiting");
        }
        reader.close();
        return;
/*
        try {
            updateStatus("Attempting to open port and set baud rate");
            port.open(connection);
            port.setParameters(38400, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            updateStatus("Port has been opened and baudrate set to 38400, time to try the first write (Coming)");
        }
        catch (IOException ioe) {
            updateStatus("IOException thrown - " + ioe.getMessage());
        }
        finally {
            try {
                port.close();
            }
            catch (IOException ioe2) {
                updateStatus("IOException when closing the port: " + ioe2.getMessage());
                // do nothing
            }
        }
 */
    }



    private void probeForUSB() {
        UsbManager manager = (UsbManager) mainActivity.getSystemService(Context.USB_SERVICE);

        /*
        PendingIntent permissionIntent = PendingIntent.getBroadcast(mainActivity, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        mainActivity.registerReceiver(usbReceiver, filter);
        */

        HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
        // UsbDevice device = deviceList.get("deviceName");
        // manager.requestPermission(device, permissionIntent);

        Log.i(myLogId, String.format("Found %d USB devices.", deviceList.size()));
        notifyListeners(String.format("Found %d USB devices.", deviceList.size()));
        for (Map.Entry<String, UsbDevice> thisDevice : deviceList.entrySet()) {
            Log.i(myLogId, String.format("Device: %s, VendorId: %d , ProdId: %d ", thisDevice.getKey(), thisDevice.getValue().getVendorId(), thisDevice.getValue().getProductId()));
            notifyListeners(String.format("Device: %s, VendorId: %d , ProdId: %d ", thisDevice.getKey(), thisDevice.getValue().getVendorId(), thisDevice.getValue().getProductId()));
            notifyListeners(String.format("Class: %d, subclass: %d, Protocol: %d", thisDevice.getValue().getDeviceClass(), thisDevice.getValue().getDeviceSubclass(), thisDevice.getValue().getDeviceProtocol()));
            notifyListeners(String.format("Now probing %s more deeply, asking permission", thisDevice.getValue().getProductName()));

            UsbDevice dev = thisDevice.getValue();
            if (manager.hasPermission(dev)) {
                probeDevice(dev, true);
            }
            // manager.requestPermission(dev, permissionIntent);
        }
    }

    private void probeDevice(UsbDevice usbDevice, boolean permissionGranted) {

        if (!permissionGranted) {
            if (usbDevice == null) {
                Log.i(myLogId, String.format("How is the device null here, permissionGranted is %s", (permissionGranted ? "true" : "false")));
                notifyListeners(String.format("Device is null, permissionGranted is %s", (permissionGranted ? "true" : "false")));
            }
            else {
                Log.i(myLogId, String.format("Permission to use device %s not granted", usbDevice.getDeviceName()));
                notifyListeners(String.format("Permission to use device %s not granted", usbDevice.getDeviceName()));
            }

            return;
        }

        if (usbDevice == null) {
            Log.i(myLogId, String.format("How is the device null here, permissionGranted is %s", (permissionGranted ? "true" : "false")));
            notifyListeners(String.format("Device is null, permissionGranted is %s", (permissionGranted ? "true" : "false")));
        }

        String deviceName = usbDevice.getProductName();
        if (deviceName == null) {
            deviceName = "NULL device name";
        }

        UsbEndpoint readEndpoint = null;
        UsbEndpoint writeEndpoint = null;

        Log.i(myLogId, String.format("Device: %s, num interfaces: %d", deviceName, usbDevice.getInterfaceCount()));
//        notifyListeners(String.format("Device: %s, num interfaces: %d", deviceName, usbDevice.getInterfaceCount()));
        for (int i = 0; i < usbDevice.getInterfaceCount(); i++) {
            UsbInterface iface = usbDevice.getInterface(i);
            Log.i(myLogId, String.format("Device %s, interface %d, has %d endpoints", deviceName, i, iface.getEndpointCount()));
//            notifyListeners(String.format("Device %s, interface %d, has %d endpoints", deviceName, i, iface.getEndpointCount()));
            for (int j = 0; j < iface.getEndpointCount(); j++) {
                UsbEndpoint endp = iface.getEndpoint(j);
                int endpointDir = endp.getDirection();
                String dirString = "unknown";
                if (endpointDir == UsbConstants.USB_DIR_OUT) {
                    writeEndpoint = endp;
                    dirString = "OUT";
                }
                else if (endpointDir == UsbConstants.USB_DIR_IN) {
                    readEndpoint = endp;
                    dirString = "IN";
                }

                Log.i(myLogId, String.format("Device %s, interface %d, endpoint %d, dir: %s,%d", deviceName, i, j, dirString, endpointDir));
//                notifyListeners(String.format("Device %s, interface %d, endpoint %d, dir: %s,%d", deviceName, i, j, dirString, endpointDir));
            }
        }

        if ((readEndpoint != null) && (writeEndpoint != null)) {
            Log.i(myLogId, String.format("Using device %s for reading SI", deviceName));
            notifyListeners(String.format("Using device %s for reading SI", deviceName));

/*
            SIReader reader = new SIReader(usbDevice, readEndpoint, writeEndpoint);
            reader.probeDevice(mainActivity, this);
            reader.close();
 */
        }
        else {
            Log.i(myLogId, String.format("Device %s not appropriate for SI reading - lacking read or write endpoint", deviceName));
            notifyListeners(String.format("Device %s not appropriate for SI reading - lacking read or write endpoint", deviceName));
        }
    }
}
