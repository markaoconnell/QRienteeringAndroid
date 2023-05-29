package com.example.playgroundtwo.resultlogging;

import android.content.Context;
import android.icu.text.SimpleDateFormat;
import android.util.Log;
import android.util.Pair;

import com.example.playgroundtwo.util.LogUtil;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class LogFileRetriever {
    private Context appContext;

    public LogFileRetriever(Context appContext) {
        this.appContext = appContext;
    }

    public List<Pair<String, String>> getLogFilenames() {
        String[] logFileList = appContext.fileList();
        long oldFileCutoffTime = System.currentTimeMillis() - LogCleanupThread.OLD_FILE_CUTOFF_TIME;
        SimpleDateFormat sdf = new SimpleDateFormat("LL/dd-HH:mm:ss");

        ArrayList<Pair<String, String>> logFilenames = new ArrayList<>();
        for (String fileToCheck : logFileList) {
            // Looks like there might be a few other files in this directory
            // Look for the ones that are how the log files are named.
            if (fileToCheck.startsWith("event-")) {
                File logFileEntry = new File(appContext.getFilesDir(), fileToCheck);
                long fileMtime = logFileEntry.lastModified();
                Date fileMtimeDate = new Date(fileMtime);

                if (fileMtime < oldFileCutoffTime) {
                    logFilenames.add(new Pair<>(fileToCheck, String.format("%s (last used %s - to be deleted)", fileToCheck, sdf.format(fileMtimeDate))));
                } else {
                    logFilenames.add(new Pair<>(fileToCheck, String.format("%s (last used %s)", fileToCheck, sdf.format(fileMtimeDate))));
                }
            }
        }
        return (logFilenames);
    }

    public String getLogContents(String logFilename) {
        FileInputStream logInputStream;
        InputStreamReader ir;
        try {
            logInputStream = appContext.openFileInput(logFilename);
            ir = new InputStreamReader(logInputStream);
        }
        catch (Exception e) {
            Log.d(LogUtil.myLogId, String.format("ERROR: Log file %s not found in LogFileRetriever", logFilename));
            return (null);
        }

        StringBuilder stringBuilder = new StringBuilder();
        try (BufferedReader logInputReader = new BufferedReader(ir)) {
            String line = logInputReader.readLine();
            while (line != null) {
                stringBuilder.append(line).append('\n');
                line = logInputReader.readLine();
            }
        } catch (IOException e) {
            // Error occurred when opening raw file for reading.
        }

        return(stringBuilder.toString());
    }
}
