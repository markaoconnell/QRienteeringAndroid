package com.example.playgroundtwo.sireader;

import android.os.Handler;
import android.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SiReaderThread extends Thread {

    private Handler handler;
    private SiResultHandler resultHandler;
    private volatile boolean stopRunning;

    public SiReaderThread() {
        stopRunning = false;
    }

    public void setHandler(Handler completionHandler) {
        this.handler = completionHandler;
    }

    public void setSiResultHandler(SiResultHandler resultHandler) {
        this.resultHandler = resultHandler;
    }

    @Override
    public void run() {
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

                for (int i = 0; i < numPunches; i++) {
                    int controlNum = r.nextInt(25) + 100;
                    int timestamp = startTime + ((totalTime / (numPunches + 2)) * (i + 1));
                    fakePunches.add(new Pair<Integer, Integer>(controlNum, timestamp));
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
    }
}
