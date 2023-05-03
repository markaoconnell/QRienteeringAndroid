package com.example.playgroundtwo.usbhandler;

import com.example.playgroundtwo.SI.CardReader;
import com.example.playgroundtwo.SI.SIReader;

public interface FoundCardCallback {
    public void foundCard(CardReader.CardEntry cardRead);
}
