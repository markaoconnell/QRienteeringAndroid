package com.example.playgroundtwo.url;

public class UrlConnectionFailureResults extends UrlCallResults {
    public UrlConnectionFailureResults(Exception e) {
        super(true, false, e);
    }
}
