package com.moconnell.qrienteering.QRienteeringCalls;

import com.moconnell.qrienteering.background.BaseBackgroundTask;
import com.moconnell.qrienteering.url.UrlCallResults;
import com.moconnell.qrienteering.url.UrlCaller;
import com.moconnell.qrienteering.url.UrlCallerException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MassStartCourses extends BaseBackgroundTask {

    private UrlCaller urlCaller;
    private String eventId;

    private List<String> coursesForMassStart;
    private int startTimeInSeconds;
    private UrlCallResults callResults;

    private HashMap<String, Integer> startsByCourse;
    private HashMap<String, List<String>> startersByCourse;

    private String errorsFound = null;

    public MassStartCourses(UrlCaller caller, String eventId, int startTimeInSeconds, List<String> coursesForMassStart) {
        this.urlCaller = caller;
        this.eventId = eventId;
        this.coursesForMassStart = coursesForMassStart;
        this.startTimeInSeconds = startTimeInSeconds;
        startsByCourse = new HashMap<>();
        startersByCourse = new HashMap<>();
    }


    @Override
    public void run() {
        massStartCourses();
    }

    public void massStartCourses() {
        String coursesForUrlCall = coursesForMassStart.stream().collect(Collectors.joining(","));
        String paramsToPass = String.format("event=%s&si_stick_time=%d&courses_to_start=%s", eventId, startTimeInSeconds, coursesForUrlCall);

        String urlString = urlCaller.makeUrlToCall("%s/OMeetMgmt/mass_start_courses.php?key=%s", paramsToPass);
        urlCaller.makeUrlCall(urlString);
        callResults = urlCaller.getResults();

        try {
            String courseListHTML = callResults.getResult();
            Pattern pattern = Pattern.compile("####,STARTED,.*");
            Matcher matcher = pattern.matcher(courseListHTML);

            while (matcher.find()) {
                String[] pieces = matcher.group().split(",");
                if (pieces.length >= 4) {
                    String courseStartedForCompetitor = pieces[3];
                    String competitorName = pieces[2];

                    startsByCourse.computeIfAbsent(courseStartedForCompetitor, k -> 0);
                    startsByCourse.compute(courseStartedForCompetitor, (k, v) -> v+1);

                    startersByCourse.computeIfAbsent(courseStartedForCompetitor, k -> new ArrayList<String>());
                    startersByCourse.get(courseStartedForCompetitor).add(competitorName);
                }
            }

            Pattern errorPattern = Pattern.compile("####,ERROR,.*");
            Matcher errorMatcher = errorPattern.matcher(courseListHTML);
            StringBuilder fullErrorString = new StringBuilder();
            boolean foundError = false;
            while (errorMatcher.find()) {
                String errorString = matcher.group().split(",")[2];
                if (!foundError) {
                    fullErrorString.append(errorString);
                    foundError = true;
                }
                else {
                    fullErrorString.append("\n").append(errorString);
                }
            }
            if (foundError) {
                errorsFound = fullErrorString.toString();
            }

        }
        catch (UrlCallerException e) {
            // do nothing, the caller will pick this up in the callback
        }

        notifyListeners();
    }

    public Map<String, Integer> getStartsByCourse() {
        return(startsByCourse);
    }

    public Map<String, List<String>> getStartersByCourse() {
        return(startersByCourse);
    }

    public boolean hasErrors() { return (errorsFound != null); }
    public String getErrors() { return (errorsFound); }

    public UrlCallResults getUrlCallResults() {
        return(callResults);
    }
}
