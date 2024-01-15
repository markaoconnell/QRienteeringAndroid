package com.moconnell.qrienteering.sireader;

import com.moconnell.qrienteering.SI.CardReader;

public interface FoundCardCallback {
    public void foundCard(CardReader.CardEntry cardRead);
}
