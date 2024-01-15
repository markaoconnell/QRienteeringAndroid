package com.moconnell.qrienteering.url;

public class UrlCallResults {

    private Exception urlCallingException = null;
    private boolean connectivityException = false;
    private boolean otherException = false;
    private String siteResultHTML;

    public UrlCallResults(String results) {
        siteResultHTML = results;
    }

    protected UrlCallResults(boolean connectivityException, boolean otherException, Exception e) {
        this.connectivityException = connectivityException;
        this.otherException = otherException;
        urlCallingException = e;
    }

    public boolean isConnectivityFailure() {
        return connectivityException;
    }

    public boolean isFailure() {
        return (connectivityException || otherException);
    }

    public boolean isSuccess() {
        return (!connectivityException && !otherException);
    }

    public Exception getFailureException() {
        return(urlCallingException);
    }

    public String getResult() throws UrlCallerException {
        if (siteResultHTML != null) {
            return (siteResultHTML);
        }
        else {
            throw new UrlCallerException(this);
        }
    }
}
