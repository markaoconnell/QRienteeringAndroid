package com.moconnell.qrienteering.sireader;

public interface StatusUpdateCallback {
    public void OnInfoFound(String infoString);
    public void OnErrorEncountered(String errorString);
}
