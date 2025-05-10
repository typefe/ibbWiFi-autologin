package com.example.ibbwifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WifiReceiver extends BroadcastReceiver {
    private static final String TAG = "WifiReceiver";
    private static final String TARGET_SSID = "ibbWiFi";
    private static final String PREFS_NAME = "IBBWifiPrefs";
    private static final String PREF_PHONE_NUMBER = "phone_number";
    private static final String PREF_PASSWORD = "password";
    private static final String PREF_MAC_ADDRESS = "mac_address";
    private static final String COUNTRY_CODE = "+90";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null)
            return;

        if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
            NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
            if (info != null && info.isConnected()) {
                WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                        .getSystemService(Context.WIFI_SERVICE);
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                String ssid = wifiInfo.getSSID();

                if (ssid != null && (ssid.equals("\"" + TARGET_SSID + "\"") || ssid.equals(TARGET_SSID))) {
                    Log.d(TAG, "Connected to ibbWiFi");
                    showToast(context, "Connected to ibbWiFi, checking internet...");

                    new Thread(() -> {
                        if (!hasInternet()) {
                            Log.d(TAG, "No internet, running login");
                            showToast(context, "No internet, attempting login...");

                            // Start the login service instead of performing login directly
                            Intent serviceIntent = new Intent(context, WifiLoginService.class);
                            context.startService(serviceIntent);
                        } else {
                            Log.d(TAG, "Already has internet connection");
                            showToast(context, "Already connected to internet");
                        }
                    }).start();
                }
            }
        }
    }

    private boolean hasInternet() {
        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress("8.8.8.8", 53), 1500);
            socket.close();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Internet check failed", e);
            return false;
        }
    }

    private void showToast(Context context, String message) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
    }
}