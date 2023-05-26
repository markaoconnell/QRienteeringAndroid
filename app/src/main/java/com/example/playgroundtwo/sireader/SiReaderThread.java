package com.example.playgroundtwo.sireader;

import android.os.Handler;
import android.util.Pair;

import com.example.playgroundtwo.usbhandler.UsbProber;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class SiReaderThread extends Thread {

    private Handler handler;
    private SiResultHandler resultHandler;
    private volatile boolean stopRunning;
    private UsbProber siReader;
    private boolean runSimulationIfNoSiReader;
    private boolean reportVerboseSiResults;

    public SiReaderThread(UsbProber readerObj) {
        stopRunning = false;
        siReader = readerObj;
        runSimulationIfNoSiReader = false;
        reportVerboseSiResults = false;
    }

    public void setHandler(Handler completionHandler) {
        this.handler = completionHandler;
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
        siReader.setCardFoundCallback(c -> {
            List<Pair<Integer, Integer>> myPunches;
            myPunches = c.punches.stream().map(p -> new Pair<Integer, Integer> (p.getCode(), (int) p.getTime() / 1000)).collect(Collectors.toList());
            SiStickResult result = new SiStickResult((int) c.cardId, (int) c.startTime / 1000, (int) c.finishTime / 1000, myPunches);
            processReadStick(result);
        });

        siReader.run();
        if (!stopRunning && runSimulationIfNoSiReader) {
            simulationRun();
        }
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

                if (r.nextInt(3) == 0) {
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
        siReader.stopRunning();
    }
}
