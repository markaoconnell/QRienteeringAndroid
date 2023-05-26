package com.example.playgroundtwo;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.example.playgroundtwo.QRienteeringCalls.GetCourseList;
import com.example.playgroundtwo.QRienteeringCalls.LookupSiUnit;
import com.example.playgroundtwo.QRienteeringCalls.RegisterForCourse;
import com.example.playgroundtwo.QRienteeringCalls.UploadResults;
import com.example.playgroundtwo.databinding.FragmentSecondBinding;
import com.example.playgroundtwo.databinding.StickEntryBinding;
import com.example.playgroundtwo.sireader.SiReaderThread;
import com.example.playgroundtwo.sireader.SiResultHandler;
import com.example.playgroundtwo.sireader.SiStickResult;
import com.example.playgroundtwo.url.UrlCallResults;
import com.example.playgroundtwo.url.UrlCaller;
import com.example.playgroundtwo.usbhandler.UsbProber;
import com.example.playgroundtwo.usbhandler.UsbProberCallback;
import com.example.playgroundtwo.userinfo.DownloadResults;
import com.example.playgroundtwo.userinfo.RegistrationResults;
import com.example.playgroundtwo.userinfo.UserInfo;

import java.util.ArrayList;
import java.util.List;

public class SecondFragment extends Fragment {

    private FragmentSecondBinding binding;
    private String eventId;
    private String accessKey;
    private String settingsUrl;
    private int siteTimeout;
    private SiReaderThread siReaderThread;
    private UsbProber usbProber;

    private List<Pair<String, String>> courseList = new ArrayList<>();
    private String [] courseNames = new String[0];
    SharedPreferences sharedPreferences;

    private static List<UserInfo> actualResults = new ArrayList<>();
    private boolean simulationModeEnabled;
    private boolean verboseSIUnitResults;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = FragmentSecondBinding.inflate(inflater, container, false);

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
            } else { // other error
            }
        });

        MainActivity.submitBackgroundTask(courseListGetter);

        for (UserInfo thisUser : actualResults) {
            addResultEntry(inflater, thisUser);
        }

        usbProber = new UsbProber((MainActivity) this.getActivity());
        usbProber.setHandler(MainActivity.getUIHandler());
        TextView infoTextWidget = binding.textviewFirst;
        usbProber.setCallback(new UsbProberCallback() {
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

        siReaderThread = new SiReaderThread(usbProber);
        siReaderThread.setHandler(MainActivity.getUIHandler());
        siReaderThread.setSiResultHandler(new SiResultHandler() {
            @Override
            public void processResult(SiStickResult result) {
                UserInfo userInfo = new UserInfo(result);
                actualResults.add(userInfo);
                addNewResultEntry(inflater, userInfo);
            }
        });

        siReaderThread.useSimulationMode(simulationModeEnabled);
        siReaderThread.printVerboseSiResults(verboseSIUnitResults);

        siReaderThread.start();

        return binding.getRoot();

    }

    private void addNewResultEntry(LayoutInflater inflater, UserInfo userInfo) {
        addResultEntry(inflater, userInfo);

        // Now check to see what actions to kick off - registration or download
        if (!userInfo.getStickInfo().isClearedStick()) {
            // Download
            UrlCaller uploadCaller = new UrlCaller(settingsUrl, accessKey, siteTimeout);
            UploadResults resultUploader = new UploadResults(uploadCaller, eventId, userInfo);
            resultUploader.setHandler(MainActivity.getUIHandler());
            resultUploader.setCallback(t -> {
                boolean performStickLookup = false;
                UrlCallResults results = resultUploader.getUrlCallResults();
                if (results.isSuccess()) {
                    DownloadResults resultDetails = resultUploader.getResultDetails();
                    userInfo.setDownloadResults(resultDetails);
                    userInfo.setRegistrationResults(null);
                    displayDownloadResults(userInfo);
                } else if (results.isConnectivityFailure()) {
                    userInfo.getStatusWidget().stickMemberName.setText("Connectivity failure - retry later");
                    userInfo.getStatusWidget().stickMemberName.setError("No response from web site, retry later");
                } else { // other error
                    userInfo.getStatusWidget().stickMemberName.setText("Unknown failure - retry later");
                }

                // If the download didn't find a registered person, try looking them up as a member
                if (userInfo.getMemberName() == null) {
                    UrlCaller lookupCaller = new UrlCaller(settingsUrl, accessKey, siteTimeout);
                    LookupSiUnit lookupHandler = new LookupSiUnit(lookupCaller, eventId, ((MainActivity) getActivity()).checkPreregistrationList(), userInfo);
                    lookupHandler.setHandler(MainActivity.getUIHandler());
                    lookupHandler.setCallback(l -> {
                        UrlCallResults lookupResults = lookupHandler.getUrlCallResults();
                        if (lookupResults.isSuccess()) {
                            handleLookupResults(userInfo);
                        } else if (lookupResults.isConnectivityFailure()) {
                            userInfo.getStatusWidget().stickMemberName.setText("Connectivity failure - retry later");
                            userInfo.getStatusWidget().stickMemberName.setError("No response from web site, retry later");
                        } else { // other error
                            userInfo.getStatusWidget().stickMemberName.setText("Unknown failure - retry later");
                            userInfo.getStatusWidget().stickMemberName.setError("Unknown failure - retry later");
                        }
                    });

                    MainActivity.submitBackgroundTask(lookupHandler);
                }
            });

            MainActivity.submitBackgroundTask(resultUploader);
        }
        else {
            UrlCaller lookupCaller = new UrlCaller(settingsUrl, accessKey, siteTimeout);
            LookupSiUnit lookupHandler = new LookupSiUnit(lookupCaller, eventId, ((MainActivity) getActivity()).checkPreregistrationList(), userInfo);
            lookupHandler.setHandler(MainActivity.getUIHandler());
            lookupHandler.setCallback(t -> {
                UrlCallResults results = lookupHandler.getUrlCallResults();
                if (results.isSuccess()) {
                    handleLookupResults(userInfo);
                } else if (results.isConnectivityFailure()) {
                    userInfo.getStatusWidget().stickMemberName.setText("Connectivity failure - retry later");
                } else { // other error
                    userInfo.getStatusWidget().stickMemberName.setText("Unknown failure - retry later");
                }
            });

            MainActivity.submitBackgroundTask(lookupHandler);
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
            ArrayAdapter<String> courseChoices = new ArrayAdapter<>(SecondFragment.this.getContext(), androidx.appcompat.R.layout.support_simple_spinner_dropdown_item, courseNames);
            stickEntryBinding.courseChoiceSpinner.setAdapter(courseChoices);
        }

        stickEntryBinding.registerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if ((stickEntryBinding.courseChoiceSpinner.getCount() == 0) && (courseList.size() > 0)) {
                    ArrayAdapter<String> courseChoices = new ArrayAdapter<>(SecondFragment.this.getContext(), androidx.appcompat.R.layout.support_simple_spinner_dropdown_item, courseNames);
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
                        userInfo.getStatusWidget().stickMemberName.setText("Connectivity failure - retry later");
                    } else { // other error
                        userInfo.getStatusWidget().stickMemberName.setText("Unknown failure - retry later");
                    }
                });

                MainActivity.submitBackgroundTask(registrationHandler);

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
            UrlCaller uploadCaller = new UrlCaller(settingsUrl, accessKey, siteTimeout);
            UploadResults resultUploader = new UploadResults(uploadCaller, eventId, userInfo);
            resultUploader.setHandler(MainActivity.getUIHandler());
            resultUploader.setCallback(t -> {
                UrlCallResults results = resultUploader.getUrlCallResults();
                if (results.isSuccess()) {
                    DownloadResults resultDetails = resultUploader.getResultDetails();
                    userInfo.setRegistrationResults(null);  // this is obsolete now that we have a successful download
                    userInfo.setDownloadResults(resultDetails);
                    displayDownloadResults(userInfo);
                } else if (results.isConnectivityFailure()) {
                    userInfo.getStatusWidget().stickMemberName.setText("Connectivity failure - retry later");
                } else { // other error
                    userInfo.getStatusWidget().stickMemberName.setText("Unknown failure - retry later");
                }
            });

            MainActivity.submitBackgroundTask(resultUploader);

        });

        userInfo.setStatusWidget(stickEntryBinding);

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
            userInfo.getStatusWidget().stickMemberName.setText("No member found");
            userInfo.getStatusWidget().stickMemberName.setError("No member found");
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
        /*
        binding.buttonSecond.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                NavHostFragment.findNavController(SecondFragment.this)
                        .navigate(R.id.action_SecondFragment_to_FirstFragment);
            }
        }); */
    }

    @Override
    public void onDestroyView() {
        siReaderThread.stopThread();
        usbProber.stopRunning();
        super.onDestroyView();
        binding = null;
    }

}