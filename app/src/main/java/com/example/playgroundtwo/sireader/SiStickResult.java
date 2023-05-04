package com.example.playgroundtwo.sireader;

import android.util.Log;
import android.util.Pair;

import com.example.playgroundtwo.usbhandler.UsbProber;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class SiStickResult {
    private int stickNumber;
    private int startTime;
    private int finishTime;
    private List<Pair<Integer, Integer>> punches;

    private String stickSummaryString = null;

    public SiStickResult(int stick, int start, int finish, List<Pair<Integer,Integer>> punchList) {
        stickNumber = stick;
        startTime = start;
        finishTime = finish;
        punches = punchList;
    }

    public int getStickNumber() {
        return stickNumber;
    }

    public int getStartTime() {
        return startTime;
    }

    public int getFinishTime() {
        return finishTime;
    }

    public List<Pair<Integer, Integer>> getPunches() {
        return punches;
    }

    public String getStickSummaryString() {
        if (stickSummaryString != null) {
            return(stickSummaryString);
        }

        String punchesString = punches.stream().map(punch -> (punch.first + ":" + punch.second)).collect(Collectors.joining(","));

        // If there is no finish punch, then make one up (10 minute split), as the QRienteering software really likes a finish punch
        int finishTimeForSummary = finishTime;
        if (finishTime == 0) {
            finishTimeForSummary = ((punches.size() == 0) ? startTime : punches.stream().flatMapToInt(p -> IntStream.of(p.second)).max().getAsInt()) + 600;
        }
        stickSummaryString = String.format("%d;%d,start:%d,finish:%d", stickNumber, startTime, startTime, finishTimeForSummary) + ((punchesString == "") ? "" : ("," + punchesString));
        Log.i(UsbProber.myLogId, stickSummaryString);
        return (stickSummaryString);
    }

    public boolean isClearedStick() {
        return ((startTime == 0) && (finishTime == 0) && ((punches == null) || (punches.size() == 0)));
    }
}
