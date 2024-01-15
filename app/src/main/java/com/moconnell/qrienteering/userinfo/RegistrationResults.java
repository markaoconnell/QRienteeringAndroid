package com.moconnell.qrienteering.userinfo;

public class RegistrationResults {
    public boolean success;
    protected String nreClass;
    protected String registeredCourse;
    protected String errorDescription;

    // Only allow this to be created as a success or an error
    // from within the RegisterForCourse code
    protected RegistrationResults() {}

    public boolean hasNreClass() {
        return (nreClass != null);
    }

    public String getNreClass() {
        return (nreClass);
    }
    public String getCourse() {
        return (registeredCourse);
    }

    public boolean hasErrors() {
        return(!success);
    }

    public String getErrorDescription() {
        if (hasErrors()) {
            return (errorDescription);
        }
        else {
            return ("");
        }
    }
}
