package com.example.playgroundtwo.url;

public class UrlCallerException extends Exception {
    UrlCallResults results;

    public UrlCallerException(UrlCallResults results) {
        this.results = results;
    }

    public UrlCallResults getResults() {
        return (results);
    }
}
