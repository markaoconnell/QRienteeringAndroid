package com.example.playgroundtwo.sireader;

import android.util.Pair;

public class SiStickResult {
    private int stickNumber;
    private int startTime;
    private int finishTime;
    private Pair<Integer, Integer>[] punches;

    public SiStickResult(int stick, int start, int finish, Pair<Integer,Integer>[] punchesArray) {
        stickNumber = stick;
        startTime = start;
        finishTime = finish;
        punches = punchesArray;
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

    public Pair<Integer, Integer>[] getPunches() {
        return punches;
    }
}
