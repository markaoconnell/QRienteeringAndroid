package com.moconnell.qrienteering.resultlogging;

import android.content.Context;
import android.icu.text.SimpleDateFormat;
import android.util.Log;

import com.moconnell.qrienteering.util.LogUtil;

import java.io.File;
import java.util.Date;


public class LogCleanupThread extends Thread {
    private Context appContext;
    private volatile boolean stopRunning = false;

    static final long OLD_FILE_CUTOFF_TIME = ((long) 30 * 86400 * 1000);  // 30 days in milliseconds

    public LogCleanupThread(Context appContext) {
        this.appContext = appContext;
    }

    @Override
    public void run() {
        String[] logFileList = appContext.fileList();
        long oldFileCutoffTime = System.currentTimeMillis() - OLD_FILE_CUTOFF_TIME;

        SimpleDateFormat sdf = new SimpleDateFormat("LL/dd-HH:mm:ss");
        Date cutoffDate = new Date(oldFileCutoffTime);
        String formattedCutoffDate = sdf.format(cutoffDate);

        for (String fileToCheck : logFileList) {
            if (stopRunning) {
                return;
            }

            // Make sure that it is a log file that we are dealing with - looks like there are
            // some other files there too
            if (fileToCheck.startsWith("event-")) {
                File logFileEntry = new File(appContext.getFilesDir(), fileToCheck);
                long fileMtime = logFileEntry.lastModified();
                Date fileMtimeDate = new Date(fileMtime);
                long diffInDays = (oldFileCutoffTime - fileMtime) / (86400 * 1000);

                String diffMsg;
                if (diffInDays < 0) {
                    diffMsg = "will delete in ";
                    diffInDays = -diffInDays;
                } else {
                    diffMsg = "exceeds threshold by ";
                }

                Log.d(LogUtil.myLogId, String.format("Found file %s with mtime %s vs cutoff of %s, %s %d days", fileToCheck, sdf.format(fileMtimeDate), formattedCutoffDate, diffMsg, diffInDays));
                if (fileMtime < oldFileCutoffTime) {
                    logFileEntry.delete();
                }
            }
        }
    }

    public void stopThread() {
        stopRunning = true;
    }
}
