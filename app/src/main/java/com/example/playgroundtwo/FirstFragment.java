package com.example.playgroundtwo;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.preference.PreferenceManager;

import com.example.playgroundtwo.databinding.FragmentFirstBinding;
import com.example.playgroundtwo.databinding.StickEntryBinding;

public class FirstFragment extends Fragment {

    private FragmentFirstBinding binding;

    private static int numberResults = 2;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = FragmentFirstBinding.inflate(inflater, container, false);

        SharedPreferences sharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(this.getActivity() /* Activity context */);
        for (int thisResult = 0; thisResult < numberResults; thisResult++) {
            StickEntryBinding stickEntryBinding = StickEntryBinding.inflate(inflater, binding.stickInfoLayout, true);
            stickEntryBinding.stickNumber.setText(String.valueOf(thisResult));

            String settingsUrlTextField = sharedPreferences.getString(getResources().getString(R.string.settings_url), "no url");
            stickEntryBinding.stickMemberName.setText(settingsUrlTextField);

            String settingsTimeout = sharedPreferences.getString(getResources().getString(R.string.settings_site_timeout), "no timeout");
            stickEntryBinding.timeTakenField.setText(settingsTimeout + ":" + thisResult);

            String settingsKey = sharedPreferences.getString(getResources().getString(R.string.settings_key), "no key");
            stickEntryBinding.courseField.setText(settingsKey);

            String[] courses = {"White", "Yellow", "Orange", "Tan", "Brown", "Green", "Red"};
            ArrayAdapter<String> courseChoices = new ArrayAdapter<>(this.getContext(), androidx.appcompat.R.layout.support_simple_spinner_dropdown_item, courses);

            stickEntryBinding.courseChoiceSpinner.setAdapter(courseChoices);
            stickEntryBinding.registerLayout.setVisibility(View.GONE);

            stickEntryBinding.registerButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
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
                }
            });

            stickEntryBinding.registrationOkButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    stickEntryBinding.stickMemberName.setText(stickEntryBinding.registerNameField.getText());
                    stickEntryBinding.courseField.setText(courses[stickEntryBinding.courseChoiceSpinner.getSelectedItemPosition()]);
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

        numberResults++;

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