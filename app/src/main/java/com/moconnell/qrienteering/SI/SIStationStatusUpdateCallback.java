package com.moconnell.qrienteering.SI;

public interface SIStationStatusUpdateCallback {
    public void notifyStatusUpdate(String notificationString, boolean isError);
}
