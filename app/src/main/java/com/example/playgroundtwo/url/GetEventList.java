package com.example.playgroundtwo.url;

import android.util.Base64;
import android.util.Pair;

import com.example.playgroundtwo.background.BaseBackgroundTask;

import java.util.List;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GetEventList extends BaseBackgroundTask {

    private UrlCaller urlCaller;
    private String xlatedKey;
    private List<Pair<String, String>> eventList;
    private UrlCallResults callResults;

    public GetEventList(UrlCaller caller) {
        this.urlCaller = caller;
    }


    @Override
    public void run() {
        getEvents();
    }

    public void getEvents() {
        String urlString = urlCaller.makeUrlToCall("%s/OMeetMgmt/manage_events.php?key=%s&recent_event_timeout=12h");
        urlCaller.makeUrlCall(urlString);
        callResults = urlCaller.getResults();

        eventList = new ArrayList<>();
        try {
            String eventListHTML = callResults.getResult();
            Pattern pattern = Pattern.compile("####,[A-Z]*_EVENT,.*");
            Matcher matcher = pattern.matcher(eventListHTML);

            while (matcher.find()) {
                String[] pieces = matcher.group().split(",");
                byte[] eventDescriptionBytes = Base64.decode(pieces[3], Base64.DEFAULT);
                eventList.add(new Pair<>(pieces[2], new String(eventDescriptionBytes)));
            }

            Pattern pattern2 = Pattern.compile("####,XLATED_KEY,.*");
            Matcher matcher2 = pattern2.matcher(eventListHTML);
            if (matcher2.find()) {
                String[] pieces = matcher2.group().split(",");
                xlatedKey = pieces[2];
            }
        }
        catch (UrlCallerException e) {
            // do nothing, the caller will pick this up in the callback
        }

        notifyListeners();
    }

    public List<Pair<String, String>> getEventListResult() {
        return(eventList);
    }

    public String getXlatedKey() {
        return(xlatedKey);
    }

    public UrlCallResults getUrlCallResults() {
        return(callResults);
    }

}
