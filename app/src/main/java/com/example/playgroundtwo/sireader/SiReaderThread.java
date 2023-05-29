package com.example.playgroundtwo.sireader;

import android.content.Context;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.util.Pair;

import com.example.playgroundtwo.MainActivity;
import com.example.playgroundtwo.SI.CardReader;
import com.example.playgroundtwo.SI.SIReader;
import com.example.playgroundtwo.SI.SiStationDisconnectedException;
import com.hoho.android.usbserial.driver.Cp21xxSerialDriver;
import com.hoho.android.usbserial.driver.ProbeTable;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class SiReaderThread extends Thread {

    private Handler handler;
    private SiResultHandler resultHandler;
    private volatile boolean stopRunning;
    private MainActivity mainActivity;
    private boolean runSimulationIfNoSiReader;
    private boolean reportVerboseSiResults;

    private StatusUpdateCallback statusUpdateCallback;

    public void setHandler(Handler completionHandler) {
        this.handler = completionHandler;
    }

    public void setStatusUpdateCallback(StatusUpdateCallback c) {
        this.statusUpdateCallback = c;
    }

    protected void notifyStatusUpdate(String notificationString, boolean isError) {
        if (statusUpdateCallback != null) {
            if (handler != null) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!isError) {
                            statusUpdateCallback.OnInfoFound(notificationString);
                        }
                        else {
                            statusUpdateCallback.OnErrorEncountered(notificationString);
                        }
                    }
                });
            } else {
                if (!isError) {
                    statusUpdateCallback.OnInfoFound(notificationString);
                } else {
                    statusUpdateCallback.OnErrorEncountered(notificationString);
                }
            }
        }
    }

    public SiReaderThread(MainActivity mainActivity) {
        stopRunning = false;
        this.mainActivity = mainActivity;
        runSimulationIfNoSiReader = false;
        reportVerboseSiResults = false;
    }

    public void setSiResultHandler(SiResultHandler resultHandler) {
        this.resultHandler = resultHandler;
    }

    public void useSimulationMode(boolean allowSimulationMode) {
        runSimulationIfNoSiReader = allowSimulationMode;
    }

    public void printVerboseSiResults(boolean verboseResults) {
        reportVerboseSiResults = verboseResults;
    }

    @Override
    public void run() {
        // The CardReader returns the timestamps in milliseconds, whereas QRienteering expects
        // times in seconds - convert here
/*        siReader.setCardFoundCallback(c -> {
            List<Pair<Integer, Integer>> myPunches;
            myPunches = c.punches.stream().map(p -> new Pair<Integer, Integer> (p.getCode(), (int) p.getTime() / 1000)).collect(Collectors.toList());
            SiStickResult result = new SiStickResult((int) c.cardId, (int) c.startTime / 1000, (int) c.finishTime / 1000, myPunches);
            processReadStick(result);
        });*/

        readSiCards();
        if (!stopRunning && runSimulationIfNoSiReader) {
            simulationRun();
        }
    }

    private static final String ACTION_USB_PERMISSION =
            "com.android.example.USB_PERMISSION";


    private void readSiCards() {
        UsbManager manager = (UsbManager) mainActivity.getSystemService(Context.USB_SERVICE);

        // Probe for the SI reader device, which use the VendorID and ProductID below.
        ProbeTable customTable = new ProbeTable();
        customTable.addProduct(4292, 32778, Cp21xxSerialDriver.class);
        //customTable.addProduct(0x1234, 0x0002, FtdiSerialDriver.class);

        UsbSerialProber prober = new UsbSerialProber(customTable);
        List<UsbSerialDriver> drivers = prober.findAllDrivers(manager);

        if (drivers.size() == 0) {
            notifyStatusUpdate("No SI reader found, is one connected?", true);
            return;
        }
        else {
            //updateStatus(String.format("Found %d compatible drivers (I hope), trying one", drivers.size()));
        }

        // Open a connection to the first available driver.
        UsbSerialDriver driver = drivers.get(0);
        //notifyStatusUpdate(String.format("Found %s, opening it", driver.getDevice().getProductName()), false);
        UsbDeviceConnection connection = manager.openDevice(driver.getDevice());
        if (connection == null) {
            notifyStatusUpdate("Found SI reader but could not access it for reading - open device error", true);
            return;
        }

        UsbSerialPort port = driver.getPorts().get(0); // Most devices have just one port (port 0)
        if (port == null) {
            notifyStatusUpdate("Found SI reader but could not access it for reading - port error", true);
            return;
        }


        try {
            port.open(connection);
        }
        catch (IOException ioe) {
            notifyStatusUpdate("Found SI reader but failed to open port - " + ioe.getMessage(), true);
            return;
        }

        SIReader reader = new SIReader(port);
        try {
            if (reader.probeDevice((m, e) -> notifyStatusUpdate(m, e))) {
                notifyStatusUpdate("Waiting for card insert", false);
                CardReader cardReader = new CardReader(reader, (m, e) -> notifyStatusUpdate(m, e));
                CardReader.CardEntry siCard = null;
//            SIReader.SiCardInfo siCard = new SIReader.SiCardInfo();
                while (!this.stopRunning) {
                    siCard = cardReader.readCardOnce();
                    if (siCard != null) {
                        if (siCard.cardId == 0) {
                            // The card must have been removed from the reader while trying to read the data
                            // Reset the station
                            notifyStatusUpdate(String.format("Card %d removed while reading, please reinsert", siCard.initialCardId), true);
                            reader.sendAck();
                        } else {
                            List<Pair<Integer, Integer>> myPunches;
                            myPunches = siCard.punches.stream().map(p -> new Pair<Integer, Integer> (p.getCode(), (int) p.getTime() / 1000)).collect(Collectors.toList());
                            SiStickResult result = new SiStickResult((int) siCard.cardId, (int) siCard.startTime / 1000, (int) siCard.finishTime / 1000, myPunches);
                            processReadStick(result);
                            notifyStatusUpdate("Read card: " + siCard.cardId, false);
                            // String punchString = siCard.punches.stream().map(punch -> (punch.getCode() + ":" + punch.getTime())).collect(Collectors.joining(","));
                            // updateStatus(String.format("Read card %d, start %d, finish %d, punches: %s", siCard.cardId, siCard.startTime, siCard.finishTime, punchString));
                        }
                    }
                }
            } else {
                notifyStatusUpdate("SI reader found but cannot communicate, exiting", true);
            }
        }
        catch (SiStationDisconnectedException sde) {
            notifyStatusUpdate("Possible SI reader disconnection?  No longer reading SI cards.", true);
        }

        reader.close();
        return;
    }

    private void simulationRun() {
        Random r = new Random();
        int nextResultReport = r.nextInt(10) + 5;
        while (true) {
            if (stopRunning) {
                break;
            }

            try {
                Thread.sleep(2000);
            }
            catch (Exception e) {
                break;
            }

            if (stopRunning) {
                break;
            }

            nextResultReport--;
            if (nextResultReport <= 0) {
                nextResultReport = r.nextInt(20) + 10;
                int stick = 2108368 + r.nextInt(5);
                int startTime = r.nextInt(10800) + 36000;
                int finishTime = startTime + 3000 + r.nextInt(7200);
                int totalTime = finishTime - startTime;
                int numPunches = r.nextInt(10) + 1;
                List<Pair<Integer, Integer>> fakePunches = new ArrayList<Pair<Integer, Integer>>();

                if (r.nextInt(3) < 2) {
                    for (int i = 0; i < numPunches; i++) {
                        int controlNum = r.nextInt(25) + 100;
                        int timestamp = startTime + ((totalTime / (numPunches + 2)) * (i + 1));
                        fakePunches.add(new Pair<Integer, Integer>(controlNum, timestamp));
                    }
                }
                else {
                    // Do a registration with a cleared stick
                    // Suppose I didn't really need to figure out the start time, the finish time,
                    // and how many punches there would be if I'm not going to use it, but, then again,
                    // this is a bit of throwaway test code and efficiency doesn't really matter here
                    startTime = 0;
                    finishTime = 0;
                    if ((numPunches % 2) == 0) {
                        stick = 2108369;
                    }
                }
                SiStickResult result = new SiStickResult(stick, startTime, finishTime, fakePunches);
                processReadStick(result);
                notifyStatusUpdate("Read card: " + stick, false);
            }
        }
    }

    protected void processReadStick(SiStickResult results) {
        if (resultHandler != null) {
            if (handler != null) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        resultHandler.processResult(results);
                    }
                });
            } else {
                resultHandler.processResult(results);
            }
        }
    }

    public void stopThread() {
        stopRunning = true;
    }
}
