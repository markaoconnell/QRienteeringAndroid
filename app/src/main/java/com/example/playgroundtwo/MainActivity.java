package com.example.playgroundtwo;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;

import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import androidx.core.os.HandlerCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.playgroundtwo.databinding.ActivityMainBinding;

import android.view.Menu;
import android.view.MenuItem;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;

    private static ExecutorService executorService = Executors.newFixedThreadPool(15);;
    private static Handler mainLoopHandler = HandlerCompat.createAsync(Looper.getMainLooper());

    private String eventId;
    private String xlatedKey;

    private boolean eventAllowsPreregistration = false;

    public static String myLogId = "MOC_QRienteering_Android";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        binding.fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        //probeForUSB();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
            int currentFragId = navController.getCurrentDestination().getId();

            if (currentFragId == R.id.FirstFragment) {
                navController.navigate(R.id.action_FirstFragment_to_settingsFragment);
            }
            else if (currentFragId == R.id.SecondFragment) {
                navController.navigate(R.id.action_SecondFragment_to_settingsFragment);
            }
            return true;
        }
        else if (id == R.id.action_enter_register_mode) {
            binding.textModeArea.setText(R.string.action_register_mode);
        }
        else if (id == R.id.action_enter_download_mode) {
            binding.textModeArea.setText(R.string.action_download_mode);
        }
        else if (id == R.id.action_enter_mass_start_mode) {
            binding.textModeArea.setText(R.string.action_mass_start_mode);
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }

    public static void submitBackgroundTask(Runnable r) {
        executorService.submit(r);
    }

    public static Handler getUIHandler() {
        return (mainLoopHandler);
    }

    public void setEventName(String eventName) {
        binding.textEventNameArea.setText(eventName);
    }

    public void setEventAndKey(String event, String key, boolean eventAllowsPreregistration) {
        eventId = event;
        xlatedKey = key;
        this.eventAllowsPreregistration = eventAllowsPreregistration;
    }

    public String getEventId() {
        return (eventId);
    }
    public boolean checkPreregistrationList() {
        return (eventAllowsPreregistration);
    }

    public String getKeyForEvent() {
        return(xlatedKey);
    }

}