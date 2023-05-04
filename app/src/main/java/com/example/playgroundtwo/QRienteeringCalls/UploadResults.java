package com.example.playgroundtwo.QRienteeringCalls;

import android.os.Build;
import android.util.Base64;

import androidx.annotation.RequiresApi;

import com.example.playgroundtwo.background.BaseBackgroundTask;
import com.example.playgroundtwo.url.UrlCallResults;
import com.example.playgroundtwo.url.UrlCaller;
import com.example.playgroundtwo.url.UrlCallerException;
import com.example.playgroundtwo.userinfo.DownloadResults;
import com.example.playgroundtwo.userinfo.UserInfo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UploadResults extends BaseBackgroundTask {

    private UrlCaller urlCaller;
    private String stickUploadResult;
    private UrlCallResults callResults;
    private UserInfo stickUser;
    private String eventId;

    private DownloadResults foundResults = new DownloadResults();

    public UploadResults(UrlCaller caller, String eventId, UserInfo stickUser) {
        this.urlCaller = caller;
        this.stickUser = stickUser;
        this.eventId = eventId;
    }


    @Override
    public void run() {
        uploadResults();
    }


    public void uploadResults() {
        String stickSummaryString = stickUser.getStickInfo().getStickSummaryString();
        String encodedStickSummary = Base64.encodeToString(stickSummaryString.getBytes(), Base64.DEFAULT);
        String safeStickSummary = encodedStickSummary.replaceAll("=", "%3D").replaceAll("\n", "");

        String urlString = urlCaller.makeUrlToCall("%s/OMeet/finish_course.php?key=%s", String.format("event=%s&si_stick_finish=%s", eventId, safeStickSummary));
        urlCaller.makeUrlCall(urlString);
        callResults = urlCaller.getResults();


        StringBuilder tmpUploadResults = new StringBuilder();
        try {
            String uploadResultsHTML = callResults.getResult();
            Pattern pattern = Pattern.compile("####,RESULT,.*");
            Matcher matcher = pattern.matcher(uploadResultsHTML);

            if (matcher.find()) {
                String[] pieces = matcher.group().split(",");
                if (pieces.length >= 5) {  // should be ####,RESULT,b64 name,course,time
                    String competitorName = new String(Base64.decode(pieces[2].getBytes(), Base64.DEFAULT));
                    String course = pieces[3];
                    int timeTaken = Integer.parseInt(pieces[4]);

                    tmpUploadResults.append(String.format("%s finished course %s in %s - ", competitorName, course, formatTimeTaken(timeTaken)));
                    foundResults.timeTaken = formatTimeTaken(timeTaken);
                    foundResults.courseRun = course;
                    foundResults.registrationName = competitorName;
                }

                Pattern classPattern = Pattern.compile("####,CLASS,.*");
                Matcher classMatcher = classPattern.matcher(uploadResultsHTML);
                if (classMatcher.find()) {
                    String[] classPieces = classMatcher.group().split(",");
                    if (classPieces.length >= 3) {
                        String nreClass = classPieces[2];
                        tmpUploadResults.append(nreClass + " - ");
                        foundResults.nreClass = nreClass;
                    }
                }
            }

            Pattern errorPattern = Pattern.compile("####,ERROR,.*");
            Matcher errorMatcher = errorPattern.matcher(uploadResultsHTML);
            if (errorMatcher.find()) {
                StringBuilder errorInfo = new StringBuilder();
                do {
                    String[] errorPieces = errorMatcher.group().split(",");
                    if (errorPieces.length >= 3) {
                        errorInfo.append(errorPieces[2]);
                    }
                } while (errorMatcher.find());
                foundResults.errors = errorInfo.toString();
                tmpUploadResults.append(errorInfo);
            }
            else {
                tmpUploadResults.append("OK");
                foundResults.courseStatus = "OK";
            }

            stickUploadResult = tmpUploadResults.toString();
        }
        catch (UrlCallerException e) {
            // do nothing, the caller will pick this up in the callback
        }

        notifyListeners();
    }


    public String getUploadResultString() {
        return(stickUploadResult);
    }

    public UrlCallResults getUrlCallResults() {
        return(callResults);
    }

    public DownloadResults getResultDetails() {
        return(foundResults);
    }

    private String formatTimeTaken(int timeTaken) {
        int hours = timeTaken / 3600;
        int minutes = (timeTaken % 3600) / 60;
        int seconds = timeTaken % 60;

        if (hours == 0) {
            return(String.format("%02dm:%02ds", minutes, seconds));
        }
        else {
            return(String.format("%02dh:%02dm:%02ds", hours, minutes, seconds));
        }
    }

}
