package com.moconnell.qrienteering.QRienteeringCalls;

import android.util.Base64;

import com.moconnell.qrienteering.background.BaseBackgroundTask;
import com.moconnell.qrienteering.url.UrlCallResults;
import com.moconnell.qrienteering.url.UrlCaller;
import com.moconnell.qrienteering.url.UrlCallerException;
import com.moconnell.qrienteering.userinfo.UserInfo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LookupSiUnit extends BaseBackgroundTask {

    private UrlCaller urlCaller;
    private UrlCallResults callResults;
    private UserInfo stickUser;
    private String eventId;
    private boolean checkPreregistrationList = false;

    private static Pattern memberPattern = Pattern.compile("####,MEMBER_ENTRY,.*");
    private static Pattern registeredPattern = Pattern.compile("####,REGISTERED,.*");
    private static Pattern classPattern = Pattern.compile("####,CLASSIFICATION_INFO,.*");





    public LookupSiUnit(UrlCaller caller, String eventId, boolean checkPreregistrationList, UserInfo stickUser) {
        this.urlCaller = caller;
        this.stickUser = stickUser;
        this.eventId = eventId;
        this.checkPreregistrationList = checkPreregistrationList;
    }


    @Override
    public void run() {
        if (checkPreregistrationList) {
            lookupMemberByStick(true);
        }

        // Two ways to get here - the user has not preregistered but may be a member
        // or the event does not allow preregistration and we are only checking for members
        // (which is the far more common case)
        if (stickUser.getMemberName() == null) {
            lookupMemberByStick(false);
        }
    }


    public void lookupMemberByStick(boolean checkForPreregisteredStick) {

        String lookupParameters = String.format("event=%s&si_stick=%d", eventId, stickUser.getStickInfo().getStickNumber());
        if (checkForPreregisteredStick) {
            lookupParameters += "&checkin=true";
        }

        String urlString = urlCaller.makeUrlToCall("%s/OMeetWithMemberList/stick_lookup.php?key=%s", lookupParameters);
        urlCaller.makeUrlCall(urlString);
        callResults = urlCaller.getResults();

        try {
            String uploadResultsHTML = callResults.getResult();
            Matcher matcher = memberPattern.matcher(uploadResultsHTML);


            Matcher registeredMatcher = registeredPattern.matcher(uploadResultsHTML);
            if (registeredMatcher.find()) {
                String[] registeredPieces = registeredMatcher.group().split(",");
                stickUser.setRegisteredStickMsg(registeredPieces[2]);
            }

            if (matcher.find()) {
                String[] pieces = matcher.group().split(",");
                if (pieces.length >= 7) {  // should be ####,MEMBER_ENTRY,b64 name,memberid,email,phone,club,course(if preregistered)
                    String competitorName = new String(Base64.decode(pieces[2].getBytes(), Base64.DEFAULT));
                    stickUser.setMemberName(competitorName);
                    stickUser.setMemberId(pieces[3]);
                    stickUser.setEmailAddress(pieces[4]);
                    stickUser.setCellPhone(pieces[5]);
                    stickUser.setClub(pieces[6]);
                    if (pieces.length >= 8) {
                        stickUser.setPreregisteredCourse(pieces[7]);
                    }
                }

                Matcher classMatcher = classPattern.matcher(uploadResultsHTML);
                if (classMatcher.find()) {
                    String[] classPieces = classMatcher.group().split(",");
                    if (classPieces.length >= 3) {
                        stickUser.setNreClassificationInfo(classPieces[2]);
                    }
                }
            }

        } catch (UrlCallerException e) {
            // do nothing, the caller will pick this up in the callback
        }

        notifyListeners();
    }


    public UrlCallResults getUrlCallResults() {
        return (callResults);
    }

}
