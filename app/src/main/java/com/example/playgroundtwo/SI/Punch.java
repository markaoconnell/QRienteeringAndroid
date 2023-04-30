package com.example.playgroundtwo.SI;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.Serializable;

public class Punch {

    public int code; //The SI station code
    public long time; //The time of the punch

    public Punch() {
    }

    public Punch(int code, long time) {
        this.code = code;
        this.time = time;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

  }

