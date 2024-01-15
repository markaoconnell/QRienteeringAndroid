package com.moconnell.qrienteering;

import android.content.SharedPreferences;
import android.icu.text.SimpleDateFormat;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.moconnell.qrienteering.databinding.OfflineFragmentBinding;
import com.moconnell.qrienteering.databinding.StickEntryBinding;
import com.moconnell.qrienteering.resultlogging.EventResultLogger;
import com.moconnell.qrienteering.sireader.SiReaderThread;
import com.moconnell.qrienteering.sireader.SiResultHandler;
import com.moconnell.qrienteering.sireader.SiStickResult;
import com.moconnell.qrienteering.sireader.StatusUpdateCallback;
import com.moconnell.qrienteering.userinfo.UserInfo;
import com.moconnell.qrienteering.util.LogUtil;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class OfflineFragment extends Fragment {

    private OfflineFragmentBinding binding;

    private SiReaderThread siReaderThread;

    SharedPreferences sharedPreferences;

    private static List<UserInfo> actualResults = new ArrayList<>();
    private boolean simulationModeEnabled;
    private boolean verboseSIUnitResults;

    private EventResultLogger resultLogger;

    private static final int SI_READER_START_RETRY_DELAY_MILLIS = 30 * 1000;  // 30 seconds


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = OfflineFragmentBinding.inflate(inflater, container, false);

        sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(this.getActivity() /* Activity context */);
        simulationModeEnabled = sharedPreferences.getBoolean(getResources().getString(R.string.enable_simulation_mode), false);
        verboseSIUnitResults = sharedPreferences.getBoolean(getResources().getString(R.string.enable_verbose_si_readout), false);


        for (UserInfo thisUser : actualResults) {
            addResultEntry(inflater, thisUser);
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String fakeOfflineEventId = String.format("event-offline-%s", sdf.format(new Date()));


        // Try and open the interface to log the results - if this errors, just keep going
        resultLogger = new EventResultLogger(this.getContext(), fakeOfflineEventId);
        try {
            resultLogger.openLogger();
        } catch (Exception e) {
            Log.e(LogUtil.myLogId, String.format("Unable to open result log file for: %s", fakeOfflineEventId));
            resultLogger = null;
        }

        siReaderThread = ((MainActivity) this.getActivity()).getSiReaderThread(this);
        if (siReaderThread != null) {
            setupSiReader(siReaderThread, inflater);
        } else {
            // Getting the reader failed - try again in a few seconds to give the old reader thread time to die
            binding.offlineStatusText.setText("Failed to start SIReader, retrying shortly");
            MainActivity.getUIHandler().postDelayed(() -> {
                siReaderThread = ((MainActivity) this.getActivity()).getSiReaderThread(this);
                if (siReaderThread != null) {
                    setupSiReader(siReaderThread, inflater);
                } else {
                    Log.d(LogUtil.myLogId, "Failed to get SIReader for ResultsFragment " + this);
                    binding.offlineStatusText.setText("Cannot start SIReader, try exiting app and restarting");
                    binding.offlineStatusText.setError("This is an error");
                }
            }, SI_READER_START_RETRY_DELAY_MILLIS);
        }


        return binding.getRoot();

    }

    private void addResultEntry(LayoutInflater inflater, UserInfo userInfo) {
        StickEntryBinding stickEntryBinding = StickEntryBinding.inflate(inflater, binding.offlineResultsLayout, true);
        stickEntryBinding.stickNumber.setText(String.valueOf(userInfo.getStickInfo().getStickNumber()));

        userInfo.setStatusWidget(stickEntryBinding);

        int timeTaken = userInfo.getStickInfo().getFinishTime() - userInfo.getStickInfo().getStartTime();
        stickEntryBinding.timeTakenField.setText(formatTimeTaken(timeTaken));

        if (verboseSIUnitResults) {
            userInfo.getStatusWidget().siStickDebugTextArea.setText(userInfo.getStickInfo().getVerboseStickSummaryString());
        }

        stickEntryBinding.closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                binding.offlineResultsLayout.removeView(stickEntryBinding.getRoot());
                actualResults.remove(userInfo);
            }
        });

        stickEntryBinding.registerButton.setVisibility(View.INVISIBLE);
        stickEntryBinding.downloadButton.setVisibility(View.INVISIBLE);

        stickEntryBinding.stickNavigationLayout.setVisibility(View.VISIBLE);
        stickEntryBinding.registerLayout.setVisibility(View.GONE);
    }

    // This is duplicated with the UploadResults, should really move this to a util class
    private String formatTimeTaken(int timeTaken) {
        int hours = timeTaken / 3600;
        int minutes = (timeTaken % 3600) / 60;
        int seconds = timeTaken % 60;

        if (hours == 0) {
            return (String.format("%02dm:%02ds", minutes, seconds));
        } else {
            return (String.format("%02dh:%02dm:%02ds", hours, minutes, seconds));
        }
    }


    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
/*
            binding.resultsMassStartButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    NavHostFragment.findNavController(com.example.playgroundtwo.ResultsFragment.this)
                            .navigate(R.id.action_ResultsFragment_to_massStartFragment);
                }
            });
*/

    }

    private void setupSiReader(SiReaderThread thread, LayoutInflater inflater) {
        Log.d(LogUtil.myLogId, "Setting up SIReaderThread " + thread + " for OfflineFragment " + this);
        TextView infoTextWidget = binding.offlineStatusText;

        thread.setHandler(MainActivity.getUIHandler());
        thread.setSiResultHandler(new SiResultHandler() {
            @Override
            public void processResult(SiStickResult result) {
                UserInfo userInfo = new UserInfo(result);
                if (resultLogger != null) {
                    resultLogger.logResult(result.getStickSummaryString());
                }
                actualResults.add(userInfo);
                addResultEntry(inflater, userInfo);
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
            Log.d(LogUtil.myLogId, "Starting SIReaderThread " + thread + " for OfflineFragment " + this);
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