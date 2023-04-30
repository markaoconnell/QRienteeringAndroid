package com.example.playgroundtwo.SI;

import java.io.IOException;

public class SiStationDisconnectedException extends IOException {
    public SiStationDisconnectedException(String msg) {
        super(msg);
    }

    public SiStationDisconnectedException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
