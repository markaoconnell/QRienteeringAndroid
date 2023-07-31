package com.example.playgroundtwo;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.preference.PreferenceManager;

import com.example.playgroundtwo.databinding.EventChooserFragmentBinding;
import com.example.playgroundtwo.QRienteeringCalls.GetEventList;
import com.example.playgroundtwo.resultlogging.LogFileRetriever;
import com.example.playgroundtwo.url.UrlCallResults;
import com.example.playgroundtwo.url.UrlCaller;

import java.util.List;


public class EventChooserFragment extends Fragment {

    private EventChooserFragmentBinding binding;
    private UrlCaller urlCaller;
    private String xlatedKey;

    private LogFileRetriever logRetriever;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = EventChooserFragmentBinding.inflate(inflater, container, false);

        SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(this.getActivity() /* Activity context */);
        String defaultInvalidURL = getResources().getString(R.string.default_invalid_url);
        String defaultInvalidKey = getResources().getString(R.string.default_invalid_key);
        String settingsUrl = sharedPreferences.getString(getResources().getString(R.string.settings_url), defaultInvalidURL);
        String settingsKey = sharedPreferences.getString(getResources().getString(R.string.settings_key), defaultInvalidKey);
        String siteTimeoutString = sharedPreferences.getString(getResources().getString(R.string.settings_site_timeout), "10");

        int siteTimeout = 10;
        try {
            siteTimeout = Integer.parseInt(siteTimeoutString);
        }
        catch (Exception e) {
            siteTimeout = 10;
        }

        logRetriever = new LogFileRetriever(getContext());

        if (settingsUrl.equals(defaultInvalidURL) || settingsKey.equals(defaultInvalidKey)) {
            binding.buttonFirst.setEnabled(false);
            binding.eventChooserStatusField.setText(getResources().getText(R.string.set_url_and_key_error));
            binding.eventChooserStatusField.setTextColor(getResources().getColor(com.google.android.material.R.color.design_default_color_error));
        } else {
            binding.buttonFirst.setEnabled(false);
            binding.eventChooserStatusField.setText("Getting available events");
            binding.eventChooserStatusField.setTextColor(getResources().getColor(com.google.android.material.R.color.design_default_color_primary));

            urlCaller = new UrlCaller(settingsUrl, settingsKey, siteTimeout);
            GetEventList eventGetter = new GetEventList(urlCaller);
            eventGetter.setHandler(MainActivity.getUIHandler());
            eventGetter.setCallback(t -> {
                UrlCallResults results = eventGetter.getUrlCallResults();
                if (results.isSuccess()) {
                    xlatedKey = eventGetter.getXlatedKey();
                    chooseEvent(eventGetter);
                } else if (results.isConnectivityFailure()) {
                    binding.buttonFirst.setEnabled(false);
                    binding.eventChooserStatusField.setText(String.format("Cannot contact site (%s), please check connectivity - message %s",
                            settingsUrl,
                            results.getFailureException().getMessage()));
                    binding.eventChooserStatusField.setTextColor(getResources().getColor(com.google.android.material.R.color.design_default_color_error));
                } else {
                    binding.buttonFirst.setEnabled(false);
                    binding.eventChooserStatusField.setText(String.format("Poorly formatted site URL (%s), please re-enter", settingsUrl));
                    binding.eventChooserStatusField.setTextColor(getResources().getColor(com.google.android.material.R.color.design_default_color_error));
                }
            });

            MainActivity.submitBackgroundTask(eventGetter);
        }

        binding.showLogButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                List<Pair<String, String>> logList = logRetriever.getLogFilenames();
                if (logList.size() > 0) {
                    RadioGroup rg = new RadioGroup(getActivity());
                    for (int i = 0; i < logList.size(); i++) {
                        Pair<String, String> logFile = logList.get(i);
                        RadioButton rb = new RadioButton(getActivity());
                        rb.setText(logFile.second);
                        rb.setId(i);
                        rg.addView(rb);
                    }
                    binding.logFileChooserLayout.addView(rg);

                    binding.emailLogButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            int selected = rg.getCheckedRadioButtonId();
                            if (selected != -1) {
                                sendLogFileInEmail(logList.get(selected).first);
                            }
                        }
                    });
                    binding.showLogButton.setVisibility(View.INVISIBLE);
                    binding.emailLogButton.setVisibility(View.VISIBLE);
                }
                else {
                    binding.showLogButton.setText("No available logs");
                    binding.showLogButton.setEnabled(false);
                }
            }
        });


        return binding.getRoot();
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.buttonFirst.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                NavHostFragment.findNavController(EventChooserFragment.this)
                        .navigate(R.id.action_EventChooserFragment_to_ResultsFragment);
            }
        });

    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void chooseEvent(GetEventList eventGetter) {
        List<Pair<String, String>> eventList = eventGetter.getEventListResult();

        if (eventList.size() == 0) {
            binding.buttonFirst.setEnabled(false);
            binding.eventChooserStatusField.setText("No currently open events");
            binding.eventChooserStatusField.setTextColor(getResources().getColor(com.google.android.material.R.color.design_default_color_error));
        }
        else if (eventList.size() == 1) {
            useSelectedCourse(eventList.get(0), eventGetter.eventSupportsPreregistration(eventList.get(0).first));
        }
        else {
            RadioGroup rg = new RadioGroup(getActivity());
            for (int i = 0; i < eventList.size(); i++) {
                Pair<String, String> course = eventList.get(i);
                RadioButton rb = new RadioButton(getActivity());
                rb.setText(course.second);
                rb.setId(i);
                rg.addView(rb);
                if (i == 0) {
                    rb.setChecked(true);
                }
            }

            binding.eventChooserLayout.addView(rg);

            binding.buttonFirst.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    int selection = rg.getCheckedRadioButtonId();
                    Pair<String, String> chosenEvent = eventList.get(selection);
                    useSelectedCourse(chosenEvent, eventGetter.eventSupportsPreregistration(chosenEvent.first));
                }
            });
            binding.buttonFirst.setEnabled(true);
        }
    }

    private void useSelectedCourse(Pair<String, String> chosenEvent, boolean eventAllowsPreregistration) {
        ((MainActivity) getActivity()).setEventName(chosenEvent.second);
        ((MainActivity) getActivity()).setEventAndKey(chosenEvent.first, xlatedKey, eventAllowsPreregistration);
        NavHostFragment.findNavController(EventChooserFragment.this)
                .navigate(R.id.action_EventChooserFragment_to_ResultsFragment);
    }

    private void sendLogFileInEmail(String logFilename) {
        String logContents = logRetriever.getLogContents(logFilename);
        if (logContents != null) {
            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_TEXT, logContents);
            sendIntent.setType("text/plain");
            String extraInfo = "Orienteering SI reader logs for " + logFilename;
            sendIntent.putExtra(Intent.EXTRA_TITLE, extraInfo);
            sendIntent.putExtra(Intent.EXTRA_SUBJECT, extraInfo);

            Intent shareIntent = Intent.createChooser(sendIntent, null);
            startActivity(shareIntent);
        }
    }
}