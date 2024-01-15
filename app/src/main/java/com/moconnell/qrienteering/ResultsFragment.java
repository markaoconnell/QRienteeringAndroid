package com.moconnell.qrienteering;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.preference.PreferenceManager;

import com.moconnell.qrienteering.QRienteeringCalls.GetCourseList;
import com.moconnell.qrienteering.QRienteeringCalls.LookupSiUnit;
import com.moconnell.qrienteering.QRienteeringCalls.RegisterForCourse;
import com.moconnell.qrienteering.QRienteeringCalls.UploadResults;
import com.moconnell.qrienteering.databinding.ResultsFragmentBinding;
import com.moconnell.qrienteering.databinding.StickEntryBinding;
import com.moconnell.qrienteering.resultlogging.EventResultLogger;
import com.moconnell.qrienteering.sireader.SiReaderThread;
import com.moconnell.qrienteering.sireader.SiResultHandler;
import com.moconnell.qrienteering.sireader.SiStickResult;
import com.moconnell.qrienteering.url.UrlCallResults;
import com.moconnell.qrienteering.url.UrlCaller;
import com.moconnell.qrienteering.sireader.StatusUpdateCallback;
import com.moconnell.qrienteering.userinfo.DownloadResults;
import com.moconnell.qrienteering.userinfo.RegistrationResults;
import com.moconnell.qrienteering.userinfo.UserInfo;
import com.moconnell.qrienteering.util.LogUtil;

import java.util.ArrayList;
import java.util.List;

public class ResultsFragment extends Fragment {

    private ResultsFragmentBinding binding;
    private String eventId;
    private String accessKey;
    private String settingsUrl;
    private int siteTimeout;
    private SiReaderThread siReaderThread;
    //private UsbProber usbProber;

    private List<Pair<String, String>> courseList = new ArrayList<>();
    private String [] courseNames = new String[0];
    SharedPreferences sharedPreferences;

    private static List<UserInfo> actualResults = new ArrayList<>();
    private boolean simulationModeEnabled;
    private boolean verboseSIUnitResults;

    private EventResultLogger resultLogger;

    private static final int SI_READER_START_RETRY_DELAY_MILLIS = 30 * 1000;  // 30 seconds

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = ResultsFragmentBinding.inflate(inflater, container, false);

        sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(this.getActivity() /* Activity context */);
        String defaultInvalidURL = getResources().getString(R.string.default_invalid_url);
        settingsUrl = sharedPreferences.getString(getResources().getString(R.string.settings_url), defaultInvalidURL);
        String siteTimeoutString = sharedPreferences.getString(getResources().getString(R.string.settings_site_timeout), "10");
        simulationModeEnabled = sharedPreferences.getBoolean(getResources().getString(R.string.enable_simulation_mode), false);
        verboseSIUnitResults = sharedPreferences.getBoolean(getResources().getString(R.string.enable_verbose_si_readout), false);

        try {
            siteTimeout = Integer.parseInt(siteTimeoutString);
        }
        catch (Exception e) {
            siteTimeout = 10;
        }

        TextView infoTextWidget = binding.textviewFirst;



        eventId = ((MainActivity) getActivity()).getEventId();
        accessKey = ((MainActivity) getActivity()).getKeyForEvent();

        UrlCaller courseListCaller = new UrlCaller(settingsUrl, accessKey, siteTimeout);
        GetCourseList courseListGetter = new GetCourseList(courseListCaller, eventId);
        courseListGetter.setHandler(MainActivity.getUIHandler());
        courseListGetter.setCallback(t -> {
            UrlCallResults results = courseListGetter.getUrlCallResults();
            if (results.isSuccess()) {
                courseList = courseListGetter.getCourseListResult();
                courseNames = courseList.stream().map((item) -> (item.second)).toArray(String[]::new);
            } else if (results.isConnectivityFailure()) {
                infoTextWidget.setText("Cannot retrieve course list - connectivity error with web site");
                infoTextWidget.setError("Check connectivity and retry");
            } else { // other error
                infoTextWidget.setText("Cannot retrieve course list - unknown error");
                infoTextWidget.setError("Not sure what is happening here");
            }
        });

        MainActivity.submitBackgroundTask(courseListGetter);

        for (UserInfo thisUser : actualResults) {
            addResultEntry(inflater, thisUser);
        }


        // Try and open the interface to log the results - if this errors, just keep going
        resultLogger = new EventResultLogger(this.getContext(), eventId);
        try {
            resultLogger.openLogger();
        }
        catch (Exception e) {
            Log.e(LogUtil.myLogId, String.format("Unable to open result log file for: %s", eventId));
            resultLogger = null;
        }

        siReaderThread = ((MainActivity) this.getActivity()).getSiReaderThread(this);
        if (siReaderThread != null) {
            setupSiReader(siReaderThread, inflater);
        }
        else {
            // Getting the reader failed - try again in a few seconds to give the old reader thread time to die
            infoTextWidget.setText("Failed to start SIReader, retrying shortly");
            MainActivity.getUIHandler().postDelayed(() -> {
                siReaderThread = ((MainActivity) this.getActivity()).getSiReaderThread(this);
                if (siReaderThread != null) {
                    setupSiReader(siReaderThread, inflater);
                }
                else {
                    Log.d(LogUtil.myLogId, "Failed to get SIReader for ResultsFragment " + this);
                    infoTextWidget.setText("Cannot start SIReader, try exiting app and restarting");
                    infoTextWidget.setError("This is an error");
                }
            }, SI_READER_START_RETRY_DELAY_MILLIS);
        }


        return binding.getRoot();

    }

    private void addNewResultEntry(LayoutInflater inflater, UserInfo userInfo) {
        addResultEntry(inflater, userInfo);

        // Now check to see what actions to kick off - registration or download
        if (userInfo.getStickInfo().isClearedStick()) {
            lookupMember(userInfo);
        }
        else {
            // Download
            downloadSiResults(userInfo,true);
        }
    }
    private void addResultEntry(LayoutInflater inflater, UserInfo userInfo) {
        StickEntryBinding stickEntryBinding = StickEntryBinding.inflate(inflater, binding.stickInfoLayout, true);
        stickEntryBinding.stickNumber.setText(String.valueOf(userInfo.getStickInfo().getStickNumber()));

        userInfo.setStatusWidget(stickEntryBinding);

        if (verboseSIUnitResults) {
            userInfo.getStatusWidget().siStickDebugTextArea.setText(userInfo.getStickInfo().getVerboseStickSummaryString());
        }

        if (userInfo.getMemberName() != null) {
            userInfo.getStatusWidget().stickMemberName.setText(userInfo.getMemberName());
            userInfo.getStatusWidget().registerNameField.setText(userInfo.getMemberName());
            if (!userInfo.getCellPhone().equals("")) {
                userInfo.getStatusWidget().emergencyContact.setText(userInfo.getCellPhone());
            }
        }

        if (userInfo.getDownloadResults() != null) {
            displayDownloadResults(userInfo);
        }

        if (userInfo.getRegistrationResults() != null) {
            displayRegistrationResults(userInfo);
        }

        if (courseList.size() > 0) {
            ArrayAdapter<String> courseChoices = new ArrayAdapter<>(ResultsFragment.this.getContext(), androidx.appcompat.R.layout.support_simple_spinner_dropdown_item, courseNames);
            stickEntryBinding.courseChoiceSpinner.setAdapter(courseChoices);
        }

        stickEntryBinding.registerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if ((stickEntryBinding.courseChoiceSpinner.getCount() == 0) && (courseList.size() > 0)) {
                    ArrayAdapter<String> courseChoices = new ArrayAdapter<>(ResultsFragment.this.getContext(), androidx.appcompat.R.layout.support_simple_spinner_dropdown_item, courseNames);
                    stickEntryBinding.courseChoiceSpinner.setAdapter(courseChoices);
                }
                if (userInfo.getPreregisteredCourse() != null) {
                    int preregisteredCourseIndex = -1;
                    for (Pair<String, String> thisCourse : courseList) {
                        preregisteredCourseIndex++;
                        if (thisCourse.first.equals(userInfo.getPreregisteredCourse())) {
                            stickEntryBinding.courseChoiceSpinner.setSelection(preregisteredCourseIndex);
                            break;
                        }
                    }
                }

                stickEntryBinding.registerLayout.setVisibility(View.VISIBLE);
                stickEntryBinding.stickNavigationLayout.setVisibility(View.GONE);
            }
        });

        stickEntryBinding.cancelRegistrationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stickEntryBinding.stickNavigationLayout.setVisibility(View.VISIBLE);
                stickEntryBinding.registerLayout.setVisibility(View.GONE);
            }
        });

        stickEntryBinding.closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                binding.stickInfoLayout.removeView(stickEntryBinding.getRoot());
                actualResults.remove(userInfo);
            }
        });

        stickEntryBinding.registrationOkButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String courseToRun = courseList.get(stickEntryBinding.courseChoiceSpinner.getSelectedItemPosition()).first;
                stickEntryBinding.stickMemberName.setText(stickEntryBinding.registerNameField.getText().toString());
                userInfo.setMemberName(stickEntryBinding.registerNameField.getText().toString());
                userInfo.setCellPhone(stickEntryBinding.emergencyContact.getText().toString());

                registerForCourse(userInfo, courseToRun);

                stickEntryBinding.stickNavigationLayout.setVisibility(View.VISIBLE);
                stickEntryBinding.registerLayout.setVisibility(View.GONE);
            }
        });

        if (userInfo.getStickInfo().isClearedStick()) {
            stickEntryBinding.downloadButton.setEnabled(false);
            stickEntryBinding.downloadButton.setBackgroundColor(Color.GRAY);
            stickEntryBinding.downloadButton.setVisibility(View.GONE);
        }
        stickEntryBinding.downloadButton.setOnClickListener(v -> {
                downloadSiResults(userInfo, false);
        });


        // Look at the current state to decide what should be visible
        if ((userInfo.getMemberName() != null) && userInfo.getStickInfo().isClearedStick()) {
            stickEntryBinding.stickNavigationLayout.setVisibility(View.GONE);
            stickEntryBinding.registerLayout.setVisibility(View.VISIBLE);
        }
        else {
            stickEntryBinding.stickNavigationLayout.setVisibility(View.VISIBLE);
            stickEntryBinding.registerLayout.setVisibility(View.GONE);
        }
    }

    private void displayDownloadResults(UserInfo resultInfo) {
        DownloadResults resultDetails = resultInfo.getDownloadResults();

        if (resultDetails.registrationName != null) {
            resultInfo.setMemberName(resultDetails.registrationName);
            resultInfo.getStatusWidget().registerNameField.setText(resultInfo.getMemberName());
            if (!resultDetails.hasNreClass()) {
                resultInfo.getStatusWidget().stickMemberName.setText(resultInfo.getMemberName());
            } else {
                resultInfo.getStatusWidget().stickMemberName.setText(resultInfo.getMemberName() + " (" + resultDetails.nreClass + ")");
            }
            resultInfo.getStatusWidget().timeTakenField.setText(resultDetails.timeTaken);
            resultInfo.getStatusWidget().courseField.setText(resultDetails.courseRun);
        }

        if (resultDetails.hasErrors()) {
            showTextWithError(resultInfo.getStatusWidget().statusField, resultDetails.errors);
        }
        else {
            showTextClearError(resultInfo.getStatusWidget().statusField, resultDetails.courseStatus);
        }
    }

    private void displayRegistrationResults(UserInfo resultInfo) {
        RegistrationResults regResults = resultInfo.getRegistrationResults();
        if (regResults.success) {
            if (regResults.hasNreClass()) {
                showTextClearError(resultInfo.getStatusWidget().statusField, "Registered successfully - " + regResults.getNreClass());
            }
            else {
                showTextClearError(resultInfo.getStatusWidget().statusField, "Registered successfully");
            }
            resultInfo.getStatusWidget().courseField.setText(regResults.getCourse().replaceFirst("^[0-9]+-", ""));
        }
        else {
            showTextWithError(resultInfo.getStatusWidget().statusField, regResults.getErrorDescription());
        }
    }

    private void lookupMember(UserInfo userInfo) {
        UrlCaller lookupCaller = new UrlCaller(settingsUrl, accessKey, siteTimeout);
        LookupSiUnit lookupHandler = new LookupSiUnit(lookupCaller, eventId, ((MainActivity) getActivity()).checkPreregistrationList(), userInfo);
        lookupHandler.setHandler(MainActivity.getUIHandler());
        lookupHandler.setCallback(t -> {
            UrlCallResults results = lookupHandler.getUrlCallResults();
            if (results.isSuccess()) {
                handleLookupResults(userInfo);
            } else if (results.isConnectivityFailure()) {
                showTextWithError(userInfo.getStatusWidget().stickMemberName, "Connectivity failure - retry later");
            } else { // other error
                showTextWithError(userInfo.getStatusWidget().stickMemberName, "Unknown failure - retry later");
            }
        });

        MainActivity.submitBackgroundTask(lookupHandler);
    }

    private void downloadSiResults(UserInfo userInfo, boolean lookForMemberIfNoCompetitorFound) {
        UrlCaller uploadCaller = new UrlCaller(settingsUrl, accessKey, siteTimeout);
        UploadResults resultUploader = new UploadResults(uploadCaller, eventId, userInfo);
        resultUploader.setHandler(MainActivity.getUIHandler());
        resultUploader.setCallback(t -> {
            UrlCallResults results = resultUploader.getUrlCallResults();
            if (results.isSuccess()) {
                DownloadResults resultDetails = resultUploader.getResultDetails();
                userInfo.setDownloadResults(resultDetails);
                userInfo.setRegistrationResults(null);
                displayDownloadResults(userInfo);
            } else if (results.isConnectivityFailure()) {
                showTextWithError(userInfo.getStatusWidget().stickMemberName, "Connectivity failure - retry later");
            } else { // other error
                showTextWithError(userInfo.getStatusWidget().stickMemberName, "Unknown failure - retry later");
            }

            // If the download didn't find a registered person, try looking them up as a member
            if ((userInfo.getMemberName() == null) && lookForMemberIfNoCompetitorFound) {
                lookupMember(userInfo);
            }
        });

        MainActivity.submitBackgroundTask(resultUploader);
    }

    private void registerForCourse(UserInfo userInfo, String courseToRun) {
        UrlCaller registerCaller = new UrlCaller(settingsUrl, accessKey, siteTimeout);
        RegisterForCourse registrationHandler = new RegisterForCourse(registerCaller, eventId, courseToRun, userInfo);
        registrationHandler.setHandler(MainActivity.getUIHandler());

        registrationHandler.setCallback(t -> {
            UrlCallResults results = registrationHandler.getUrlCallResults();
            if (results.isSuccess()) {
                RegistrationResults regResults = registrationHandler.getRegistrationResults();
                userInfo.setRegistrationResults(regResults);
                userInfo.setDownloadResults(null);
                displayRegistrationResults(userInfo);
            } else if (results.isConnectivityFailure()) {
                showTextWithError(userInfo.getStatusWidget().stickMemberName, "Connectivity failure - retry later");
            } else { // other error
                showTextWithError(userInfo.getStatusWidget().stickMemberName, "Unknown failure - retry later");
            }
        });

        MainActivity.submitBackgroundTask(registrationHandler);
    }

    private void handleLookupResults(UserInfo userInfo) {
        if (userInfo.getMemberName() != null) {
            userInfo.getStatusWidget().stickMemberName.setText(userInfo.getMemberName());
            userInfo.getStatusWidget().registerNameField.setText(userInfo.getMemberName());
            if (!userInfo.getCellPhone().equals("")) {
                userInfo.getStatusWidget().emergencyContact.setText(userInfo.getCellPhone());
            }

            userInfo.getStatusWidget().registerLayout.setVisibility(View.VISIBLE);
            userInfo.getStatusWidget().stickNavigationLayout.setVisibility(View.GONE);
        }
        else {
            showTextWithError(userInfo.getStatusWidget().stickMemberName, "No member found");
        }
    }

    private void showTextClearError(TextView field, String textToSet) {
        field.setText(textToSet);
        field.setError(null);
    }

    private void showTextWithError(TextView field, String textToSet) {
        field.setText(textToSet);
        field.setError(textToSet);
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.resultsMassStartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                NavHostFragment.findNavController(ResultsFragment.this)
                        .navigate(R.id.action_ResultsFragment_to_massStartFragment);
            }
        });

    }

    private void setupSiReader(SiReaderThread thread, LayoutInflater inflater) {
        Log.d(LogUtil.myLogId, "Setting up SIReaderThread " + thread + " for ResultsFragment " + this);
        TextView infoTextWidget = binding.textviewFirst;

        thread.setHandler(MainActivity.getUIHandler());
        thread.setSiResultHandler(new SiResultHandler() {
            @Override
            public void processResult(SiStickResult result) {
                UserInfo userInfo = new UserInfo(result);
                if (resultLogger != null) {
                    resultLogger.logResult(result.getStickSummaryString());
                }
                actualResults.add(userInfo);
                addNewResultEntry(inflater, userInfo);
            }
        });

        thread.setStatusUpdateCallback(new StatusUpdateCallback() {
            @Override
            public void OnInfoFound(String infoString) {
                infoTextWidget.setText(infoString);
                infoTextWidget.setError(null);
            }

            @Override
            public void OnErrorEncountered(String errorString) {
                infoTextWidget.setText(errorString);
                infoTextWidget.setError(errorString);
            }
        });

        thread.useSimulationMode(simulationModeEnabled);
        thread.printVerboseSiResults(verboseSIUnitResults);

        // This is hacky, reconsider where the thread should be started from
        if (!thread.isAlive()) {
            Log.d(LogUtil.myLogId, "Starting SIReaderThread " + thread + " for ResultsFragment " + this);
            thread.start();
        }
    }

    @Override
    public void onDestroyView() {
        if (siReaderThread != null) {
            ((MainActivity) this.getActivity()).releaseSIReaderThread(this);
            siReaderThread = null;
        }

        if (resultLogger != null) {
            resultLogger.closeLogger();
            resultLogger = null;
        }

        super.onDestroyView();
        binding = null;
    }

}