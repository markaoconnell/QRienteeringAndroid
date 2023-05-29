package com.example.playgroundtwo.resultlogging;

import android.content.Context;
import android.util.Log;

import com.example.playgroundtwo.util.LogUtil;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

public class EventResultLogger {
    private String eventId;
    private Context appContext;

    private Writer logOutputWriter;

    private LogCleanupThread logCleanupThread;
    private boolean logCleanupThreadStarted = false;

    public EventResultLogger(Context context, String eventId) {
        this.eventId = eventId;
        appContext = context;
        logCleanupThread = new LogCleanupThread(context);
    }

    public void openLogger() throws Exception {
        if (logOutputWriter == null) {
            FileOutputStream logOutputStream = appContext.openFileOutput(eventId, Context.MODE_PRIVATE | Context.MODE_APPEND);
            logOutputWriter = new BufferedWriter(new OutputStreamWriter(logOutputStream));
        }
        else {
            // Do something?  Exception?  Or ignore as it is already open?
        }

        if (!logCleanupThreadStarted) {
            logCleanupThread.start();
        }
    }

    public void closeLogger() {
        if (logOutputWriter == null) {
            return;
        }

        try {
            Writer localWriterCopy = logOutputWriter;
            logOutputWriter = null;
            localWriterCopy.close();
        }
        catch (Exception e) {
            Log.i(LogUtil.myLogId, "Error closing log file: " + eventId + ", ignoring it");
        }

        // On the off chance that this is still running, close it
        logCleanupThread.stopThread();
    }

    public void logResult(String resultToLog) {
        try {
            logOutputWriter.write(resultToLog + "\n");
        }
        catch (Exception e) {
            Log.i(LogUtil.myLogId, String.format("Error writing %s to log file %s, exception is %s.", resultToLog, eventId, e.getMessage()));
        }
    }

}
