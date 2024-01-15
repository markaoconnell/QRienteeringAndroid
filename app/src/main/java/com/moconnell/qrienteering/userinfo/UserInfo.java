package com.moconnell.qrienteering.userinfo;

import com.moconnell.qrienteering.databinding.StickEntryBinding;
import com.moconnell.qrienteering.sireader.SiStickResult;

public class UserInfo {
    private SiStickResult stickInfo;

    private DownloadResults userResults;

    private RegistrationResults registrationResults;
    private String memberName;
    private String preregisteredCourse;
    private String club = "No club";
    private String memberId = "NotAMember";
    private String emailAddress = "";
    private String cellPhone = "";
    private String nreClassificationInfo;
    private StickEntryBinding statusWidget;

    public UserInfo(SiStickResult stickResults) {
        stickInfo = stickResults;
    }


    public DownloadResults getDownloadResults() {
        return userResults;
    }

    public void setDownloadResults(DownloadResults userResults) {
        this.userResults = userResults;
    }

    public RegistrationResults getRegistrationResults() {
        return registrationResults;
    }

    public void setRegistrationResults(RegistrationResults registrationResults) {
        this.registrationResults = registrationResults;
    }

    public String getMemberName() {
        return memberName;
    }
    public void setMemberName(String name) { memberName = name; }

    public String getPreregisteredCourse() {
        return preregisteredCourse;
    }
    public void setPreregisteredCourse(String preregisteredCourse) {
        this.preregisteredCourse = preregisteredCourse;
    }

    public SiStickResult getStickInfo() {
        return stickInfo;
    }

    public void setStatusWidget(StickEntryBinding widget) {
        statusWidget = widget;
    }

    public StickEntryBinding getStatusWidget() {
        return(statusWidget);
    }

    public String getClub() {
        return club;
    }

    public void setClub(String club) {
        this.club = club;
    }

    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public void setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
    }

    public String getCellPhone() {
        return cellPhone;
    }

    public void setCellPhone(String cellPhone) {
        this.cellPhone = cellPhone;
    }

    public String getNreClassificationInfo() {
        return nreClassificationInfo;
    }

    public void setNreClassificationInfo(String nreClassificationInfo) {
        this.nreClassificationInfo = nreClassificationInfo;
    }
}
