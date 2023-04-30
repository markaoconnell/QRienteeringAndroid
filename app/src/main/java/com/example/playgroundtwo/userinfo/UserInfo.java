package com.example.playgroundtwo.userinfo;

import com.example.playgroundtwo.databinding.StickEntryBinding;
import com.example.playgroundtwo.sireader.SiStickResult;

public class UserInfo {
    private SiStickResult readResults;
    private String memberName;
    private String course;
    private StickEntryBinding statusWidget;

    public UserInfo(SiStickResult stickResults) {
        readResults = stickResults;
    }

    public void setRetrievedInfo(String memberName, String courseRun) {
        this.memberName = memberName;
        course = courseRun;
    }

    public String getMemberName() {
        return memberName;
    }

    public String getCourse() {
        return course;
    }

    public SiStickResult getReadResults() {
        return readResults;
    }

    public void setStatusWidget(StickEntryBinding widget) {
        statusWidget = widget;
    }

    public StickEntryBinding getStatusWidget() {
        return(statusWidget);
    }
}
