package com.example.playgroundtwo.usbhandler;

public interface UsbProberCallback {

    public void OnInfoFound(String infoString);
    public void OnErrorEncountered(String errorString);
}
