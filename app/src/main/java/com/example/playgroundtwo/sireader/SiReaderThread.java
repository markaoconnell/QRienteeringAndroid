package com.example.playgroundtwo.sireader;

import android.os.Handler;

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
                nextResultReport = r.nextInt(20) + 30;
                int stick = 2108369 + r.nextInt(5);
                SiStickResult result = new SiStickResult(stick, r.nextInt(10800) + 36000, 0, null);
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
