package com.example.playgroundtwo.userinfo;

public class DownloadResults {
    public String timeTaken = "No time";
    public String courseRun = "No course";
    public String nreClass;
    public String courseStatus = "No status";
    public String errors = null;

    public boolean hasNreClass() {
        return (nreClass != null);
    }

    public boolean hasErrors() {
        return (errors != null);
    }
}
