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

    public void setEventAndKey(String event, String key) {
        eventId = event;
        xlatedKey = key;
    }

    public String getEventId() {
        return (eventId);
    }

    public String getKeyForEvent() {
        return(xlatedKey);
    }


    private static final String ACTION_USB_PERMISSION =
            "com.android.example.USB_PERMISSION";

    private static boolean usbPermissionGranted = false;

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if(device != null){
                            usbPermissionGranted = true;
                            //call method to set up device communication
                        }
                    }
                    else {
                        // Log.d(TAG, "permission denied for device " + device);
                    }
                }
            }
        }
    };

    private void probeForUSB() {
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);

        PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(usbReceiver, filter);

        HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
        // UsbDevice device = deviceList.get("deviceName");
        // manager.requestPermission(device, permissionIntent);

        Log.i(myLogId, String.format("Found %d USB devices.", deviceList.size()));
        for (Map.Entry<String, UsbDevice> thisDevice : deviceList.entrySet()) {
            Log.i(myLogId, String.format("Device: %s, VendorId: %d , ProdId: %d ", thisDevice.getKey(), thisDevice.getValue().getVendorId(), thisDevice.getValue().getProductId()));

            UsbDevice dev = thisDevice.getValue();
            Log.i(myLogId, String.format("Device: %s, num interfaces: %d", dev.getDeviceName(), dev.getInterfaceCount()));
            for (int i = 0; i < dev.getInterfaceCount(); i++) {
                UsbInterface iface = dev.getInterface(i);
                Log.i(myLogId, String.format("Device %s, interface %d, has %d endpoints", dev.getDeviceName(), i, iface.getEndpointCount()));
                for (int j = 0; j < iface.getEndpointCount(); j++) {
                    UsbEndpoint endp = iface.getEndpoint(j);
                    int endpointDir = endp.getDirection();
                    String dirString = "unknown";
                    if (endpointDir == UsbConstants.USB_DIR_OUT) {
                        dirString = "OUT";
                    }
                    else if (endpointDir == UsbConstants.USB_DIR_IN) {
                        dirString = "IN";
                    }

                    Log.i(myLogId, String.format("Device %s, interface %d, endpoint %d, dir: %s,%d", dev.getDeviceName(), i, j, dirString, endpointDir));
                }
            }
        }
    }
}