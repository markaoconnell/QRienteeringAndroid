package com.example.playgroundtwo.background;

import android.os.Handler;

public abstract class BaseBackgroundTask implements Runnable {
    private Handler handler;
    private BaseBackgroundTaskCallback callback;

    public void setHandler(Handler completionHandler) {
        this.handler = completionHandler;
    }

    public void setCallback(BaseBackgroundTaskCallback c) {
        this.callback = c;
    }

    protected void notifyListeners() {
        if (callback != null) {
            if (handler != null) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.OnComplete(BaseBackgroundTask.this);
                    }
                });
            } else {
                callback.OnComplete(this);
            }
        }
    }
}
