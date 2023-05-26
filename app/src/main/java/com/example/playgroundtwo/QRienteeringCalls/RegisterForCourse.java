package com.example.playgroundtwo.QRienteeringCalls;

import android.util.Base64;

import com.example.playgroundtwo.background.BaseBackgroundTask;
import com.example.playgroundtwo.url.UrlCallResults;
import com.example.playgroundtwo.url.UrlCaller;
import com.example.playgroundtwo.url.UrlCallerException;
import com.example.playgroundtwo.userinfo.DownloadResults;
import com.example.playgroundtwo.userinfo.RegistrationResults;
import com.example.playgroundtwo.userinfo.UserInfo;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class RegisterForCourse extends BaseBackgroundTask {

    public class RegistrationResultsSuccess extends RegistrationResults {

        private String webResultString;

        public RegistrationResultsSuccess (String course) {
            registeredCourse = course;
            success = true;
        }

        public RegistrationResultsSuccess(String course, String nreClassFound) {
            success = true;
            registeredCourse = course;
            nreClass = nreClassFound;
        }

        public void addWebResultString(String webResult) {
            webResultString = webResult;
        }
    }

    public class RegistrationResultsError extends RegistrationResults {
        public RegistrationResultsError(String errorDescription) {
            success = false;
            this.errorDescription = errorDescription;
        }
    }

    private UrlCaller urlCaller;
    private String eventId;
    private String course;
    private UserInfo userToRegister;

    private RegistrationResults registrationResults;

    private UrlCallResults callResults;

    public RegisterForCourse(UrlCaller caller, String eventId, String course, UserInfo stickUser) {
        this.urlCaller = caller;
        this.userToRegister = stickUser;
        this.eventId = eventId;
        this.course = course;
    }


    @Override
    public void run() {
        registerForCourse();
    }


    public void registerForCourse() {

        if (userToRegister.getMemberName() == null) {
            return;
        }

        ArrayList<String> registrationParams = new ArrayList<>();
        registrationParams.add("first_name");
        registrationParams.add(Base64.encodeToString(userToRegister.getMemberName().getBytes(), Base64.DEFAULT));
        registrationParams.add("last_name");
        registrationParams.add(Base64.encodeToString("".getBytes(), Base64.DEFAULT));
        registrationParams.add("club_name");
        registrationParams.add(Base64.encodeToString(userToRegister.getClub().getBytes(), Base64.DEFAULT));
        registrationParams.add("si_stick");
        registrationParams.add(Base64.encodeToString(String.valueOf(userToRegister.getStickInfo().getStickNumber()).getBytes(), Base64.DEFAULT));
        registrationParams.add("email_address");
        registrationParams.add(Base64.encodeToString(userToRegister.getEmailAddress().getBytes(), Base64.DEFAULT));
        registrationParams.add("registration");
        registrationParams.add(Base64.encodeToString("SIUnit - Android".getBytes(), Base64.DEFAULT));
        registrationParams.add("member_id");
        registrationParams.add(Base64.encodeToString(userToRegister.getMemberId().getBytes(), Base64.DEFAULT));
        registrationParams.add("cell_phone");
        registrationParams.add(Base64.encodeToString(userToRegister.getCellPhone().getBytes(), Base64.DEFAULT));
        registrationParams.add("is_member");
        registrationParams.add(Base64.encodeToString("yes".getBytes(), Base64.DEFAULT));
        if (userToRegister.getNreClassificationInfo() != null) {
            registrationParams.add("classification_info");
            registrationParams.add(Base64.encodeToString(userToRegister.getNreClassificationInfo().getBytes(), Base64.DEFAULT));
        }

        String quotedName;
        String quotedCourse;
        try {
            quotedName = URLEncoder.encode(userToRegister.getMemberName(), "UTF-8");
            quotedCourse = URLEncoder.encode(course, "UTF-8");
        }
        catch (UnsupportedEncodingException uee) {
            // TODO return a better error here
            return;
        }

        String namedParamsToPass = registrationParams.stream().collect(Collectors.joining(","));
        String paramsToPass = String.format("event=%s&course=%s&competitor_name=%s&registration_info=%s", eventId, quotedCourse, quotedName, namedParamsToPass);
        paramsToPass = paramsToPass.replaceAll("\n", "");

        String urlString = urlCaller.makeUrlToCall("%s/OMeetRegistration/register_competitor.php?key=%s", paramsToPass);
        urlCaller.makeUrlCall(urlString);
        callResults = urlCaller.getResults();

        try {
            String registerResultsHTML = callResults.getResult();
            Pattern pattern = Pattern.compile("####,RESULT,.*");
            Matcher matcher = pattern.matcher(registerResultsHTML);

            if (matcher.find()) {
                String webResult = "";
                RegistrationResultsSuccess successfulResult;

                String[] pieces = matcher.group().split(",");
                if (pieces.length >= 3) {  // should be ####,RESULT,Registered XXX on COURSE
                    webResult = pieces[2];
                }

                Pattern classPattern = Pattern.compile("####,CLASS,.*");
                Matcher classMatcher = classPattern.matcher(registerResultsHTML);
                if (classMatcher.find()) {
                    String[] classPieces = classMatcher.group().split(",");
                    if (classPieces.length >= 3) {
                        String nreClass = classPieces[2];
                        successfulResult = new RegistrationResultsSuccess(course, nreClass);
                    }
                    else {
                        // This should really never happen, but this makes sure that
                        // successfulResult has always been initialized
                        successfulResult = new RegistrationResultsSuccess(course);
                    }
                }
                else {
                    successfulResult = new RegistrationResultsSuccess(course);
                }

                successfulResult.addWebResultString(webResult);
                registrationResults = successfulResult;
            }
            else {
                Pattern errorPattern = Pattern.compile("####,ERROR,.*");
                Matcher errorMatcher = errorPattern.matcher(registerResultsHTML);
                if (errorMatcher.find()) {
                    String[] errorPieces = errorMatcher.group().split(",");
                    if (errorPieces.length >= 3) {
                        registrationResults = new RegistrationResultsError(errorPieces[2]);
                    }
                    else {
                        registrationResults = new RegistrationResultsError("Unknown error encountered");
                    }
                }
                else {
                    registrationResults = new RegistrationResultsError("Unknown error encountered");
                }
            }
        }
        catch (UrlCallerException e) {
            // do nothing, the caller will pick this up in the callback
        }

        notifyListeners();
    }


    public UrlCallResults getUrlCallResults() {
        return(callResults);
    }

    public RegistrationResults getRegistrationResults() {
        return(registrationResults);
    }
}
