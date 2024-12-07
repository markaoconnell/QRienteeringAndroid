package com.moconnell.qrienteering;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import com.moconnell.qrienteering.sireader.SiReaderThread;
import com.moconnell.qrienteering.util.LogUtil;
import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import androidx.core.os.HandlerCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.moconnell.qrienteering.databinding.ActivityMainBinding;

import android.view.Menu;
import android.view.MenuItem;

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

    private static SiReaderThread siReaderThread;
    private static Fragment owningFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        String myAppVersion = "no version found";
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            myAppVersion = pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            // Nothing to do really, just don't print a version number
            // e.printStackTrace();
        }

        binding.versionField.setText("Version: " + myAppVersion);


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

            if (currentFragId == R.id.EventChooserFragment) {
                navController.navigate(R.id.action_EventChooserFragment_to_settingsFragment);
            }
            else if (currentFragId == R.id.ResultsFragment) {
                navController.navigate(R.id.action_ResultsFragment_to_settingsFragment);
            }
            else if (currentFragId == R.id.massStartFragment) {
                navController.navigate(R.id.action_massStartFragment_to_settingsFragment);
            }
            else if (currentFragId == R.id.offlineFragment) {
                navController.navigate(R.id.action_OfflineFragment_to_settingsFragment);
            }
            return true;
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

    public void setVersion(String version) { binding.versionField.setText(version); }

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

    public SiReaderThread getSiReaderThread(Fragment owningFragment) {
        Log.d(LogUtil.myLogId, "SIReaderThread: owningFragment was " + this.owningFragment + ", now is " + owningFragment + " current thread: " + siReaderThread);
        this.owningFragment = owningFragment;
        // See if the old thread, if any, has exited
        // If the siReaderIsStopping() flag is set, then the thread is on the way to exiting,
        // even if still alive - but if still alive, return null to force a callback later to retry
        if (siReaderThread != null) {
            siReaderThread.clearExitIfIdle();
            if (siReaderThread.siReaderIsStopping() || !siReaderThread.isAlive()) {
                if (siReaderThread.isAlive()) {
                    Log.d(LogUtil.myLogId, "Old thread " + siReaderThread + " still alive, return failure for fragment: " + owningFragment);
                    return null;
                }
                siReaderThread = null;
            }
        }

        if (siReaderThread == null) {
            // Need to start a new thread
            siReaderThread = new SiReaderThread(this);
            Log.d(LogUtil.myLogId, "New thread started (" + siReaderThread + ") for owningFragment " + owningFragment);
        }
        else {
            Log.d(LogUtil.myLogId, "Reusing SI reader thread (" + siReaderThread + ") for owningFragment " + owningFragment);
        }

        return (siReaderThread);
    }

    public void releaseSIReaderThread(Fragment releasingFragment) {
        Log.d(LogUtil.myLogId, "Releasing thread (" + siReaderThread + ") for fragment " + releasingFragment + ", owningFragment is " + this.owningFragment);

        if (owningFragment == releasingFragment) {
            if (siReaderThread != null) {
                siReaderThread.setStatusUpdateCallback(null);
                siReaderThread.setSiResultHandler(null);
                siReaderThread.exitIfIdle();
            }
            owningFragment = null;
        }
    }
}