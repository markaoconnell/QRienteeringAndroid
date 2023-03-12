package com.example.playgroundtwo.url;

public class UrlFailureResults extends UrlCallResults {
    public UrlFailureResults(Exception e) {
        super(false, true, e);
    }
}
