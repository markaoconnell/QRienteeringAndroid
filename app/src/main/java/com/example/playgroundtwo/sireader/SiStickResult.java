package com.example.playgroundtwo.sireader;

import android.util.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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

        stickSummaryString = String.format("%d;%d,start:%d,finish:%d", stickNumber, startTime, startTime, finishTime) + ((punchesString == "") ? "" : ("," + punchesString));
        return (stickSummaryString);
    }
}
