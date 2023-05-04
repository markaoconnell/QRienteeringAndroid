package com.example.playgroundtwo;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

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

    private static int numberResults = 1;
    private static List<UserInfo> actualResults = new ArrayList<>();

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
        usbProber.setCallback(infoString -> infoTextWidget.setText(infoTextWidget.getText() + "\n" + infoString));

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

        siReaderThread.start();


        /*
        for (int thisResult = 0; thisResult < numberResults; thisResult++) {
            SiStickResult fakeResult = new SiStickResult(thisResult, 0, 0, null);
            UserInfo userInfo = new UserInfo(fakeResult);

            addResultEntry(inflater, userInfo);
        }
         */

        //numberResults++;

        return binding.getRoot();

    }

    private void addNewResultEntry(LayoutInflater inflater, UserInfo userInfo) {
        addResultEntry(inflater, userInfo);

        // Now check to see what actions to kick off - registration or download
        if (userInfo.getStickInfo().getStartTime() != 0) {
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
                    if (resultDetails.registrationName != null) {
                        userInfo.setMemberName(resultDetails.registrationName);
                        if (!resultDetails.hasNreClass()) {
                            userInfo.getStatusWidget().stickMemberName.setText(userInfo.getMemberName());
                        } else {
                            userInfo.getStatusWidget().stickMemberName.setText(userInfo.getMemberName() + " (" + resultDetails.nreClass + ")");
                        }
                        userInfo.getStatusWidget().timeTakenField.setText(resultDetails.timeTaken);
                        userInfo.getStatusWidget().courseField.setText(resultDetails.courseRun);
                    }
                    else {
                        performStickLookup = true;
                    }

                    if (resultDetails.hasErrors()) {
                        userInfo.getStatusWidget().statusField.setText(resultDetails.errors);
                    }
                    else {
                        userInfo.getStatusWidget().statusField.setText(resultDetails.courseStatus);
                    }
                } else if (results.isConnectivityFailure()) {
                    userInfo.getStatusWidget().stickMemberName.setText("Connectivity failure - retry later");
                } else { // other error
                    userInfo.getStatusWidget().stickMemberName.setText("Unknown failure - retry later");
                }

                if (performStickLookup) {
                    UrlCaller lookupCaller = new UrlCaller(settingsUrl, accessKey, siteTimeout);
                    LookupSiUnit lookupHandler = new LookupSiUnit(lookupCaller, eventId, ((MainActivity) getActivity()).checkPreregistrationList(), userInfo);
                    lookupHandler.setHandler(MainActivity.getUIHandler());
                    lookupHandler.setCallback(l -> {
                        UrlCallResults lookupResults = lookupHandler.getUrlCallResults();
                        if (lookupResults.isSuccess()) {
                            // The call may have returned successfully, but it may not have found a member for this stick
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
                            }
                        } else if (lookupResults.isConnectivityFailure()) {
                            userInfo.getStatusWidget().stickMemberName.setText("Connectivity failure - retry later");
                        } else { // other error
                            userInfo.getStatusWidget().stickMemberName.setText("Unknown failure - retry later");
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
                    // The call may have returned successfully, but it may not have found a member for this stick
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
                    }
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

        if (userInfo.getMemberName() != null) {
            stickEntryBinding.stickMemberName.setText(userInfo.getMemberName());
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

                stickEntryBinding.registerNameField.setText(stickEntryBinding.stickMemberName.getText());
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
                        if (regResults.success) {
                            if (regResults.hasNreClass()) {
                                stickEntryBinding.statusField.setText("Registered successfully - " + regResults.getNreClass());
                            }
                            else {
                                stickEntryBinding.statusField.setText("Registered successfully");
                            }
                            userInfo.setDownloadResults(null);  // Clear this, as it is now obsolete with the new registration
                            stickEntryBinding.courseField.setText(courseToRun.replaceFirst("^[0-9]+-", ""));
                        }
                        else {
                            stickEntryBinding.statusField.setText(regResults.getErrorDescription());
                        }
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

        stickEntryBinding.downloadButton.setEnabled(!userInfo.getStickInfo().isClearedStick());
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
                    if (userInfo.getMemberName() != null) {
                        if (!resultDetails.hasNreClass()) {
                            userInfo.getStatusWidget().stickMemberName.setText(userInfo.getMemberName());
                        } else {
                            userInfo.getStatusWidget().stickMemberName.setText(userInfo.getMemberName() + " (" + resultDetails.nreClass + ")");
                        }
                        userInfo.getStatusWidget().timeTakenField.setText(resultDetails.timeTaken);
                        userInfo.getStatusWidget().courseField.setText(resultDetails.courseRun);
                    }

                    if (resultDetails.hasErrors()) {
                        userInfo.getStatusWidget().statusField.setText(resultDetails.errors);
                    }
                    else {
                        userInfo.getStatusWidget().statusField.setText(resultDetails.courseStatus);
                    }
                } else if (results.isConnectivityFailure()) {
                    userInfo.getStatusWidget().stickMemberName.setText("Connectivity failure - retry later");
                } else { // other error
                    userInfo.getStatusWidget().stickMemberName.setText("Unknown failure - retry later");
                }
            });

            MainActivity.submitBackgroundTask(resultUploader);

        });

        userInfo.setStatusWidget(stickEntryBinding);

        // By default, the registration view is hidden - this may be overriden later
        stickEntryBinding.registerLayout.setVisibility(View.GONE);
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