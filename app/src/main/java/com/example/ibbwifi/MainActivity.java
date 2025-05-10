package com.example.ibbwifi;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 123;
    private static final String PREFS_NAME = "IBBWifiPrefs";
    private static final String PREF_PHONE_NUMBER = "phone_number";
    private static final String PREF_PASSWORD = "password";
    private static final String PREF_MAC_ADDRESS = "mac_address";

    private WifiReceiver wifiReceiver;
    private TextView statusTextView;
    private TextInputEditText phoneNumberEditText;
    private TextInputEditText passwordEditText;
    private TextInputEditText macAddressEditText;
    private TextInputLayout macAddressLayout;
    private FloatingActionButton helpButton;
    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI components
        statusTextView = findViewById(R.id.statusTextView);
        phoneNumberEditText = findViewById(R.id.phoneNumberEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        macAddressEditText = findViewById(R.id.macAddressEditText);
        macAddressLayout = findViewById(R.id.macAddressLayout);
        helpButton = findViewById(R.id.helpButton);
        Button saveButton = findViewById(R.id.saveButton);
        Button checkButton = findViewById(R.id.checkButton);

        // Initialize Preferences
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        loadSavedPreferences();

        // Set up MAC address helper text
        macAddressLayout.setHelperText("Format: XX:XX:XX:XX:XX:XX");
        macAddressLayout.setHint("MAC Address (required)");

        // Set up help button
        helpButton.setOnClickListener(v -> showMacAddressHelpDialog());

        // Set up button listeners
        saveButton.setOnClickListener(v -> savePreferences());
        checkButton.setOnClickListener(v -> checkWifiConnection());

        // Request necessary permissions
        requestPermissions();

        // Register WiFi state change receiver
        wifiReceiver = new WifiReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        registerReceiver(wifiReceiver, intentFilter);

        statusTextView.setText("IBB WiFi Auto Login is running\nWaiting for WiFi connection...");
    }

    private void showMacAddressHelpDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("How to Find Your MAC Address");

        String message = "<b>On Android 10+:</b><br>" +
                "1. Go to Settings > About Phone > Status<br>" +
                "2. Find 'Wi-Fi MAC address'<br><br>" +
                "<b>On Android 9 and below:</b><br>" +
                "1. Go to Settings > Wi-Fi<br>" +
                "2. Tap the menu (three dots)<br>" +
                "3. Select Advanced<br>" +
                "4. Find 'MAC address'<br><br>" +
                "<b>Note:</b> Android 6.0+ restricts apps from accessing MAC address for privacy reasons, so you must enter it manually.";

        // Use the appropriate method based on Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.setMessage(Html.fromHtml(message, Html.FROM_HTML_MODE_COMPACT));
        } else {
            builder.setMessage(Html.fromHtml(message));
        }

        builder.setPositiveButton("OK", null);
        builder.show();
    }

    private void loadSavedPreferences() {
        String phoneNumber = preferences.getString(PREF_PHONE_NUMBER, "");
        String password = preferences.getString(PREF_PASSWORD, "");
        String macAddress = preferences.getString(PREF_MAC_ADDRESS, "");

        phoneNumberEditText.setText(phoneNumber);
        passwordEditText.setText(password);
        macAddressEditText.setText(macAddress);
    }

    private void savePreferences() {
        String phoneNumber = phoneNumberEditText.getText() != null ? phoneNumberEditText.getText().toString() : "";
        String password = passwordEditText.getText() != null ? passwordEditText.getText().toString() : "";
        String macAddress = macAddressEditText.getText() != null ? macAddressEditText.getText().toString() : "";

        if (phoneNumber.isEmpty()) {
            Toast.makeText(this, "Phone number cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.isEmpty()) {
            Toast.makeText(this, "Password cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        if (macAddress.isEmpty()) {
            Toast.makeText(this, "MAC address cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate MAC address format
        if (!isValidMacAddress(macAddress)) {
            Toast.makeText(this, "Invalid MAC address format. Use XX:XX:XX:XX:XX:XX", Toast.LENGTH_LONG).show();
            return;
        }

        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(PREF_PHONE_NUMBER, phoneNumber);
        editor.putString(PREF_PASSWORD, password);
        editor.putString(PREF_MAC_ADDRESS, macAddress);
        editor.apply();

        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
    }

    private boolean isValidMacAddress(String macAddress) {
        // Check if the MAC address matches the format XX:XX:XX:XX:XX:XX
        return macAddress.matches("([0-9A-Fa-f]{2}[:]){5}([0-9A-Fa-f]{2})");
    }

    private void checkWifiConnection() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wifiManager != null) {
            if (!wifiManager.isWifiEnabled()) {
                statusTextView.setText("WiFi is disabled. Please enable WiFi.");
                return;
            }

            String ssid = wifiManager.getConnectionInfo().getSSID();
            if (ssid.contains("ibbWiFi") || ssid.contains("IBB")) {
                statusTextView.setText("Connected to ibbWiFi\nChecking internet connection...");
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final boolean hasInternet = checkInternetConnection();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (hasInternet) {
                                    statusTextView.setText("Connected to ibbWiFi\nInternet connection is available");
                                } else {
                                    statusTextView
                                            .setText(
                                                    "Connected to ibbWiFi\nNo internet connection\nAttempting login...");
                                    // Manually trigger the login process
                                    Intent intent = new Intent(WifiManager.NETWORK_STATE_CHANGED_ACTION);
                                    sendBroadcast(intent);
                                }
                            }
                        });
                    }
                }).start();
            } else {
                statusTextView.setText("Not connected to ibbWiFi. Current network: " + ssid);
            }
        }
    }

    private boolean checkInternetConnection() {
        try {
            java.net.Socket socket = new java.net.Socket();
            socket.connect(new java.net.InetSocketAddress("8.8.8.8", 53), 1500);
            socket.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void requestPermissions() {
        String[] permissions = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.INTERNET,
                Manifest.permission.CHANGE_WIFI_STATE
        };

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
                break;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (!allGranted) {
                Toast.makeText(this, "All permissions are required for this app to function properly",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (wifiReceiver != null) {
            unregisterReceiver(wifiReceiver);
        }
    }
}
