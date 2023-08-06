package com.example.playgroundtwo;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;

import com.example.playgroundtwo.QRienteeringCalls.GetCourseList;
import com.example.playgroundtwo.QRienteeringCalls.MassStartCourses;
import com.example.playgroundtwo.databinding.MassStartFragmentBinding;
import com.example.playgroundtwo.sireader.SiReaderThread;
import com.example.playgroundtwo.sireader.SiResultHandler;
import com.example.playgroundtwo.sireader.SiStickResult;
import com.example.playgroundtwo.sireader.StatusUpdateCallback;
import com.example.playgroundtwo.url.UrlCallResults;
import com.example.playgroundtwo.url.UrlCaller;
import com.example.playgroundtwo.util.LogUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 *
 */
public class MassStartFragment extends Fragment {

    private MassStartFragmentBinding binding;
    private String eventId;
    private String accessKey;
    private int siteTimeout;
    private String settingsUrl;

    private SiReaderThread siReaderThread;

    private List<Pair<String, String>> courseList = new ArrayList<>();
    SharedPreferences sharedPreferences;

    private boolean simulationModeEnabled;
    private boolean verboseSIUnitResults;

    private boolean awaitingStartTime;

    private int startTimeInSeconds = 0;
    private static final int SI_READER_START_RETRY_DELAY_MILLIS = 30 * 1000;  // 30 seconds


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = MassStartFragmentBinding.inflate(inflater, container, false);

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
            } else if (results.isConnectivityFailure()) {
                binding.massStartErrorText.setText("Cannot retrieve course list - connectivity error with web site");
                binding.massStartErrorText.setError("Check connectivity and retry");
            } else { // other error
                binding.massStartErrorText.setText("Cannot retrieve course list - unknown error");
                binding.massStartErrorText.setError("Not sure what is happening here");
            }
        });

        MainActivity.submitBackgroundTask(courseListGetter);



        awaitingStartTime = true;
        siReaderThread = ((MainActivity) this.getActivity()).getSiReaderThread(this);
        if (siReaderThread != null) {
            setupSiReaderThread(siReaderThread);
        }
        else {
            // Getting the reader failed - try again in a few seconds to give the old reader thread time to die
            binding.massStartStatusText.setText("Failed to start SIReader, retrying shortly");
            MainActivity.getUIHandler().postDelayed(() -> {
                siReaderThread = ((MainActivity) this.getActivity()).getSiReaderThread(this);
                if (siReaderThread != null) {
                    setupSiReaderThread(siReaderThread);
                }
                else {
                    Log.d(LogUtil.myLogId, "Failed to get SI Reader for MassStartFragment " + this);
                    binding.massStartErrorText.setText("Cannot start SIReader, try exiting app and restarting");
                    binding.massStartErrorText.setError("This is an error");
                }
            }, SI_READER_START_RETRY_DELAY_MILLIS);
        }


        return (binding.getRoot());
    }

    private void getTimeForMassStart() {
        awaitingStartTime = true;
        startTimeInSeconds = 0;
    }
    private void chooseCourses() {
        ArrayList<Pair<CheckBox, Pair<String, String>>> courseChoiceCheckboxes = new ArrayList<>();
        binding.massStartCoursesCheckboxes.removeAllViews();

        for (Pair<String, String> thisCourse : courseList) {
            CheckBox courseCheckBox = new CheckBox(this.getContext());
            courseCheckBox.setText(thisCourse.second);
            binding.massStartCoursesCheckboxes.addView(courseCheckBox);
            courseChoiceCheckboxes.add(new Pair<>(courseCheckBox, thisCourse));
        }

        binding.massStartGoBackToGetTimeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                binding.massStartCoursesCheckboxes.removeAllViews();
                binding.massStartTimeChosenText.setText("");
                binding.massStartChooseCoursesLayout.setVisibility(View.GONE);
                binding.massStartGetTimeLayout.setVisibility(View.VISIBLE);
                getTimeForMassStart();
            }
        });

        binding.massStartCourseChoiceOkButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Make sure that at least one course was checked
                ArrayList<String> chosenCourses = new ArrayList<>();
                for (Pair<CheckBox, Pair<String, String>> courseChoiceBox : courseChoiceCheckboxes) {
                    if (courseChoiceBox.first.isChecked()) {
                        chosenCourses.add(courseChoiceBox.second.first);  // Add the unique name of the course
                    }
                }

                if (chosenCourses.size() > 0) {
                    binding.massStartChooseCoursesLayout.setVisibility(View.GONE);
                    binding.massStartResultsText.setVisibility(View.VISIBLE);
                    runMassStartCommand(chosenCourses);
                }
                else {
                    binding.massStartErrorText.setText("Must choose at least one course for the mass start");
                }
            }
        });

        binding.massStartChooseCoursesLayout.setVisibility(View.VISIBLE);
        binding.massStartGetTimeLayout.setVisibility(View.GONE);
    }

    private void runMassStartCommand(List<String> chosenCourses) {
        UrlCaller massStartCaller = new UrlCaller(settingsUrl, accessKey, siteTimeout);
        MassStartCourses massStarter = new MassStartCourses(massStartCaller, eventId, startTimeInSeconds, chosenCourses);
        massStarter.setHandler(MainActivity.getUIHandler());
        massStarter.setCallback(t -> {
            UrlCallResults results = massStarter.getUrlCallResults();
            if (results.isSuccess()) {
                Map<String, Integer> massStartResults = massStarter.getStartsByCourse();
                String resultString = chosenCourses.stream()
                        .map(i -> i.replaceAll("^[0-9]+-", "") + ": " + (massStartResults.containsKey(i) ? massStartResults.get(i) : 0))
                        .collect(Collectors.joining(", "));
                if (!massStarter.hasErrors()) {
                    binding.massStartResultsText.setText("Competitors started per course: \n" + resultString);
                }
                else {
                    binding.massStartResultsText.setText("Errors starting courses:\n" + massStarter.getErrors() + "\n\n" + resultString);
                    binding.massStartResultsText.setError("Call failed");
                }
            } else if (results.isConnectivityFailure()) {
                binding.massStartResultsText.setText("Cannot mass start courses - connectivity error with web site");
                binding.massStartResultsText.setError("Check connectivity and retry");
            } else { // other error
                binding.massStartResultsText.setText("Cannot mass start courses - unknown error");
                binding.massStartResultsText.setError("Not sure what is happening here");
            }
        });

        MainActivity.submitBackgroundTask(massStarter);
    }

    private void setupSiReaderThread(SiReaderThread thread) {
        Log.d(LogUtil.myLogId, "Setting up SI Reader Thread " + thread + " for MassStartFragment " + this);
        thread.setHandler(MainActivity.getUIHandler());
        thread.setSiResultHandler(new SiResultHandler() {
            @Override
            public void processResult(SiStickResult result) {
                if (awaitingStartTime) {
                    if (result.getStartTime() != 0) {
                        awaitingStartTime = false;

                        startTimeInSeconds = result.getStartTime();
                        int hours = (startTimeInSeconds / 3600);
                        int minutes = (startTimeInSeconds % 3600) / 60;
                        int seconds = startTimeInSeconds % 60;
                        binding.massStartTimeChosenText.setText(String.format("Using start time of %02dh:%02dm:%02ds", hours, minutes, seconds));

                        chooseCourses();
                    }
                    else {
                        binding.massStartErrorText.setText(String.format("SI Unit (%d) has start time of zero, was it cleared?", result.getStickNumber()));
                    }
                }
                else {
                    // do nothing, more sticks should not be inserted
                    binding.massStartErrorText.setText(String.format("Ignoring SI Unit insertion (%d), mass start time already established.", result.getStickNumber()));
                }
            }
        });

        thread.setStatusUpdateCallback(new StatusUpdateCallback() {
            @Override
            public void OnInfoFound(String infoString) {
                binding.massStartStatusText.setText(infoString);
            }

            @Override
            public void OnErrorEncountered(String errorString) {
                binding.massStartErrorText.setText(errorString);
                binding.massStartErrorText.setError(errorString);
            }
        });

        thread.useSimulationMode(simulationModeEnabled);
        thread.printVerboseSiResults(verboseSIUnitResults);

        // This is hacky, reconsider where the thread should be started from
        if (!thread.isAlive()) {
            Log.d(LogUtil.myLogId, "Starting SI Reader Thread " + thread + " for MassStartFragment " + this);
            thread.start();
        }
    }

    @Override
    public void onDestroyView() {
        if (siReaderThread != null) {
            ((MainActivity) this.getActivity()).releaseSIReaderThread(this);
            siReaderThread = null;
        }

        super.onDestroyView();
        binding = null;
    }
}