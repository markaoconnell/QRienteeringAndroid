package com.example.playgroundtwo.userinfo;

import com.example.playgroundtwo.sireader.SiStickResult;

public class UserInfo {
    private SiStickResult readResults;
    private String memberName;
    private String course;

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
}
