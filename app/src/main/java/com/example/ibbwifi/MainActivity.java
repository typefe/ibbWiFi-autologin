package com.example.ibbwifi;

import android.Manifest;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 123;
    private WifiReceiver wifiReceiver;
    private TextView statusTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusTextView = findViewById(R.id.statusTextView);
        Button checkButton = findViewById(R.id.checkButton);

        checkButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkWifiStatus();
            }
        });

        // Request permissions
        requestPermissions();

        // Register the receiver
        wifiReceiver = new WifiReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        registerReceiver(wifiReceiver, intentFilter);

        statusTextView.setText("IBB WiFi Auto Login is running\nWaiting for WiFi connection...");
    }

    private void requestPermissions() {
        String[] permissions = {
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_FINE_LOCATION
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
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
                Toast.makeText(this, "App requires permissions to function properly", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void checkWifiStatus() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        if (!wifiManager.isWifiEnabled()) {
            statusTextView.setText("WiFi is disabled. Please enable WiFi.");
            return;
        }

        String ssid = wifiManager.getConnectionInfo().getSSID();
        if (ssid != null && (ssid.equals("\"ibbWiFi\"") || ssid.equals("ibbWiFi"))) {
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
                                        .setText("Connected to ibbWiFi\nNo internet connection\nAttempting login...");
                                // Manually trigger the login process
                                Intent intent = new Intent(WifiManager.NETWORK_STATE_CHANGED_ACTION);
                                sendBroadcast(intent);
                            }
                        }
                    });
                }
            }).start();
        } else {
            statusTextView.setText("Not connected to ibbWiFi\nCurrent network: " + ssid);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (wifiReceiver != null) {
            unregisterReceiver(wifiReceiver);
        }
    }
}
