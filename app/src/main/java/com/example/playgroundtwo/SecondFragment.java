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
import androidx.navigation.fragment.NavHostFragment;
import androidx.preference.PreferenceManager;

import com.example.playgroundtwo.QRienteeringCalls.GetCourseList;
import com.example.playgroundtwo.QRienteeringCalls.GetEventList;
import com.example.playgroundtwo.databinding.FragmentSecondBinding;
import com.example.playgroundtwo.databinding.StickEntryBinding;
import com.example.playgroundtwo.sireader.SiReaderThread;
import com.example.playgroundtwo.sireader.SiResultHandler;
import com.example.playgroundtwo.sireader.SiStickResult;
import com.example.playgroundtwo.url.UrlCallResults;
import com.example.playgroundtwo.url.UrlCaller;
import com.example.playgroundtwo.usbhandler.UsbProber;
import com.example.playgroundtwo.usbhandler.UsbProberCallback;
import com.example.playgroundtwo.userinfo.UserInfo;

import java.util.ArrayList;
import java.util.List;

public class SecondFragment extends Fragment {

    private FragmentSecondBinding binding;
    private UrlCaller urlCaller;
    private String eventId;
    private String accessKey;
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
        String settingsUrl = sharedPreferences.getString(getResources().getString(R.string.settings_url), defaultInvalidURL);
        String siteTimeoutString = sharedPreferences.getString(getResources().getString(R.string.settings_site_timeout), "10");

        int siteTimeout = 10;
        try {
            siteTimeout = Integer.parseInt(siteTimeoutString);
        }
        catch (Exception e) {
            siteTimeout = 10;
        }

        eventId = ((MainActivity) getActivity()).getEventId();
        accessKey = ((MainActivity) getActivity()).getKeyForEvent();

        urlCaller = new UrlCaller(settingsUrl, accessKey, siteTimeout);
        GetCourseList courseListGetter = new GetCourseList(urlCaller, eventId);
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

        siReaderThread = new SiReaderThread();
        siReaderThread.setHandler(MainActivity.getUIHandler());
        siReaderThread.setSiResultHandler(new SiResultHandler() {
            @Override
            public void processResult(SiStickResult result) {
                UserInfo userInfo = new UserInfo(result);
                actualResults.add(userInfo);
                addResultEntry(inflater, userInfo);
            }
        });

        siReaderThread.start();

        usbProber = new UsbProber((MainActivity) this.getActivity());
        usbProber.setHandler(MainActivity.getUIHandler());
        TextView infoTextWidget = binding.textviewFirst;
        usbProber.setCallback(infoString -> infoTextWidget.setText(infoTextWidget.getText() + "\n" + infoString));
        usbProber.start();

        for (int thisResult = 0; thisResult < numberResults; thisResult++) {
            SiStickResult fakeResult = new SiStickResult(thisResult, 0, 0, null);
            UserInfo userInfo = new UserInfo(fakeResult);

            addResultEntry(inflater, userInfo);
        }

        //numberResults++;

        return binding.getRoot();

    }

    private void addResultEntry(LayoutInflater inflater, UserInfo userInfo) {
        StickEntryBinding stickEntryBinding = StickEntryBinding.inflate(inflater, binding.stickInfoLayout, true);
        stickEntryBinding.stickNumber.setText(String.valueOf(userInfo.getReadResults().getStickNumber()));

        String settingsUrlTextField = sharedPreferences.getString(getResources().getString(R.string.settings_url), getResources().getString(R.string.default_invalid_url));
        stickEntryBinding.stickMemberName.setText(settingsUrlTextField);

        String settingsTimeout = sharedPreferences.getString(getResources().getString(R.string.settings_site_timeout), "10");
        stickEntryBinding.timeTakenField.setText(settingsTimeout + ":" + userInfo.getReadResults().getStartTime());

        String settingsKey = sharedPreferences.getString(getResources().getString(R.string.settings_key), getResources().getString(R.string.default_invalid_key));
        stickEntryBinding.courseField.setText(settingsKey);
        stickEntryBinding.registerLayout.setVisibility(View.GONE);

        stickEntryBinding.registerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ArrayAdapter<String> courseChoices = new ArrayAdapter<>(SecondFragment.this.getContext(), androidx.appcompat.R.layout.support_simple_spinner_dropdown_item, courseNames);
                stickEntryBinding.courseChoiceSpinner.setAdapter(courseChoices);

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
                stickEntryBinding.stickMemberName.setText(stickEntryBinding.registerNameField.getText());
                stickEntryBinding.courseField.setText(courseNames[stickEntryBinding.courseChoiceSpinner.getSelectedItemPosition()]);
                stickEntryBinding.statusField.setText("Registered");
                stickEntryBinding.stickNumber.setText(stickEntryBinding.emergencyContact.getText());

                if (stickEntryBinding.emergencyContact.getText().length() < 7) {
                    stickEntryBinding.statusField.setText("Invalid contact number");
                }

                stickEntryBinding.stickNavigationLayout.setVisibility(View.VISIBLE);
                stickEntryBinding.registerLayout.setVisibility(View.GONE);
            }
        });
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