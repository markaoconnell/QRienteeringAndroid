package com.moconnell.qrienteering.url;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

public class UrlCaller {

    private static final int DEFAULT_SITE_TIMEOUT = 10;  // seconds
    private String siteUrl;
    private String siteAccessKey;
    private int siteTimeout = DEFAULT_SITE_TIMEOUT;

    private UrlCallResults callResults;

    public UrlCaller(String siteUrl, String siteAccessKey, int siteTimeout) {
        this.siteUrl = siteUrl;
        this.siteAccessKey = siteAccessKey;
        this.siteTimeout = siteTimeout;
        callResults = new UrlConnectionFailureResults(new IOException("No call made"));
    }

    public UrlCaller(String siteUrl, String siteAccessKey) {
      this(siteUrl, siteAccessKey, DEFAULT_SITE_TIMEOUT);
    }

    public String makeUrlToCall(String formatString) {
        String urlString = String.format(formatString, siteUrl, siteAccessKey);
        return (urlString);
    }

    public String makeUrlToCall(String formatString, String extraParams) {
        String urlString = String.format(formatString, siteUrl, siteAccessKey) + "&" + extraParams;
        return (urlString);
    }


    public void makeUrlCall(String urlToCall) {
        StringBuilder resultHTML = new StringBuilder();

        try {
            URL url = new URL(urlToCall);
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(siteTimeout * 1000);
            connection.setReadTimeout(siteTimeout * 1000);

            BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream(), "UTF-8"));

            String line;
            while ((line = br.readLine()) != null) {
                resultHTML.append(line + "\n");
            }
            br.close();

            callResults = new UrlCallResults(resultHTML.toString());
        } catch (MalformedURLException murle) {
            callResults = new UrlFailureResults(murle);
        } catch (IOException ioe) {
            callResults = new UrlConnectionFailureResults(ioe);
        }
    }

    public UrlCallResults getResults() {
        return (callResults);
    }
}
