package com.moconnell.qrienteering.QRienteeringCalls;

import android.util.Pair;

import com.moconnell.qrienteering.background.BaseBackgroundTask;
import com.moconnell.qrienteering.url.UrlCallResults;
import com.moconnell.qrienteering.url.UrlCaller;
import com.moconnell.qrienteering.url.UrlCallerException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GetCourseList extends BaseBackgroundTask {

    private UrlCaller urlCaller;
    private String eventId;

    private List<Pair<String, String>> courseList;
    private UrlCallResults callResults;

    public GetCourseList(UrlCaller caller, String eventId) {
        this.urlCaller = caller;
        this.eventId = eventId;
    }


    @Override
    public void run() {
        getCourseList();
    }

    public void getCourseList() {
        String urlString = urlCaller.makeUrlToCall("%s/OMeet/view_results.php?key=%s", String.format("event=%s", eventId));
        urlCaller.makeUrlCall(urlString);
        callResults = urlCaller.getResults();

        courseList = new ArrayList<>();
        try {
            String courseListHTML = callResults.getResult();
            Pattern pattern = Pattern.compile("####,CourseList,.*");
            Matcher matcher = pattern.matcher(courseListHTML);

            if (matcher.find()) {
                String[] pieces = matcher.group().split(",");
                if (pieces.length > 2) {
                    for (int i = 2; i < pieces.length; i++) {
                        courseList.add(new Pair<>(pieces[i], pieces[i].replaceAll("^[0-9]+-", "")));
                    }
                }
            }
        }
        catch (UrlCallerException e) {
            // do nothing, the caller will pick this up in the callback
        }

        notifyListeners();
    }

    public List<Pair<String, String>> getCourseListResult() {
        return(courseList);
    }

    public UrlCallResults getUrlCallResults() {
        return(callResults);
    }
}
