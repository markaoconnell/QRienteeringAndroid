package com.example.playgroundtwo;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.preference.PreferenceManager;

import com.example.playgroundtwo.background.BaseBackgroundTask;
import com.example.playgroundtwo.background.BaseBackgroundTaskCallback;
import com.example.playgroundtwo.databinding.FragmentFirstBinding;
import com.example.playgroundtwo.url.GetEventList;
import com.example.playgroundtwo.url.UrlCallResults;
import com.example.playgroundtwo.url.UrlCaller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class FirstFragment extends Fragment {

    private FragmentFirstBinding binding;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = FragmentFirstBinding.inflate(inflater, container, false);

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

        if (settingsUrl.equals(defaultInvalidURL) || settingsKey.equals(defaultInvalidKey)) {
            binding.buttonFirst.setEnabled(false);
            binding.eventChooserStatusField.setText(getResources().getText(R.string.set_url_and_key_error));
            binding.eventChooserStatusField.setTextColor(getResources().getColor(com.google.android.material.R.color.design_default_color_error));
        } else {
            binding.buttonFirst.setEnabled(false);
            binding.eventChooserStatusField.setText("Getting available events");
            binding.eventChooserStatusField.setTextColor(getResources().getColor(com.google.android.material.R.color.design_default_color_primary));

            UrlCaller urlCaller = new UrlCaller(settingsUrl, settingsKey, siteTimeout);
            GetEventList eventGetter = new GetEventList(urlCaller);
            eventGetter.setHandler(MainActivity.getUIHandler());
            eventGetter.setCallback(t -> {
                UrlCallResults results = eventGetter.getUrlCallResults();
                if (results.isSuccess()) {
                    List<Pair<String, String>> eventList = eventGetter.getEventListResult();
                    String[] eventNames = eventList.stream().map((item) -> (item.second)).toArray(String[]::new);
                    binding.eventChooserStatusField.setText(String.format("Found %d events: %s", eventList.size(), String.join(" -- ", eventNames)));
                    binding.eventChooserStatusField.setTextColor(getResources().getColor(com.google.android.material.R.color.design_default_color_primary));
                    binding.buttonFirst.setEnabled(true);
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


        return binding.getRoot();
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.buttonFirst.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                NavHostFragment.findNavController(FirstFragment.this)
                        .navigate(R.id.action_FirstFragment_to_SecondFragment);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

}