package com.example.ibbwifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
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
    private static final String PHONE_NUMBER = "(546) 814 02 73";
    private static final String PASSWORD = "9353";
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
                            performLogin(context);
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

    private void performLogin(Context context) {
        try {
            // Get MAC address
            WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            String macAddress = wifiManager.getConnectionInfo().getMacAddress();
            if (macAddress == null || macAddress.equals("02:00:00:00:00:00")) {
                // Android 6.0+ doesn't allow getting MAC address directly
                // This is a fallback that might not work in all cases
                macAddress = "02:00:00:00:00:00";
            }
            String encodedMac = URLEncoder.encode(macAddress, "UTF-8");

            // Setup cookies and headers
            Map<String, String> cookies = new HashMap<>();
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent",
                    "Mozilla/5.0 (Platform; Security; OS-or-CPU; Localization; rv:1.4) Gecko/20030624 Netscape/7.1 (ax)");

            // First request to get session and initial form values
            String initialUrl = "http://captive.ibbwifi.istanbul/?mac=" + encodedMac;
            String initialResponse = makeGetRequest(initialUrl, headers);
            Log.d(TAG, "Initial response received");

            // Extract session ID from response
            String sessionId = extractSessionId(initialResponse);
            if (sessionId != null) {
                cookies.put("ASP.NET_SessionId", sessionId);
                cookies.put("cerezdosyam", "TelefonNumarasi=" + URLEncoder.encode(PHONE_NUMBER, "UTF-8") + "&UlkeKodu="
                        + URLEncoder.encode(COUNTRY_CODE, "UTF-8"));
            }

            // Extract form values
            String[] formValues = extractFormValues(initialResponse);
            if (formValues == null) {
                Log.e(TAG, "Failed to extract form values");
                showToast(context, "Login failed: Could not extract form values");
                return;
            }

            // Prepare first POST data
            Map<String, String> firstPostData = new HashMap<>();
            firstPostData.put("ScriptManager1", "UpdatePanel1|BtnGiris");
            firstPostData.put("__EVENTTARGET", "BtnGiris");
            firstPostData.put("__EVENTARGUMENT", "");
            firstPostData.put("__LASTFOCUS", "");
            firstPostData.put("CmbLang", "tr");
            firstPostData.put("HdnGecici", "");
            firstPostData.put("HdnWifiPoint", "İBB Ümraniye Erkek Öğrenci Yurdu");
            firstPostData.put("HdnLat", "40,999480");
            firstPostData.put("HdnLong", "29,146470");
            firstPostData.put("TxtPhoneNumber", PHONE_NUMBER);
            firstPostData.put("CmbConutry", COUNTRY_CODE);
            firstPostData.put("ChkBeniHatirlaYerli", "on");
            firstPostData.put("__ASYNCPOST", "true");
            firstPostData.put("__VIEWSTATE", formValues[0]);
            firstPostData.put("__VIEWSTATEGENERATOR", formValues[1]);
            firstPostData.put("__EVENTVALIDATION", formValues[2]);

            // Make first POST request
            String firstPostResponse = makePostRequest(initialUrl, headers, firstPostData, cookies);
            Log.d(TAG, "First post response received");

            // Extract form values for second POST
            String viewState = extractHiddenField(firstPostResponse, "__VIEWSTATE");
            String viewStateGenerator = extractHiddenField(firstPostResponse, "__VIEWSTATEGENERATOR");
            String eventValidation = extractHiddenField(firstPostResponse, "__EVENTVALIDATION");

            if (viewState == null || viewStateGenerator == null || eventValidation == null) {
                Log.e(TAG, "Failed to extract second form values");
                showToast(context, "Login failed: Could not extract second form values");
                return;
            }

            // Prepare second POST data
            Map<String, String> secondPostData = new HashMap<>();
            secondPostData.put("ScriptManager1", "UpdatePanel1|BtnBaglan");
            secondPostData.put("CmbLang", "tr");
            secondPostData.put("HdnGecici", "");
            secondPostData.put("HdnWifiPoint", "İBB Ümraniye Erkek Öğrenci Yurdu");
            secondPostData.put("HdnLat", "40,999480");
            secondPostData.put("HdnLong", "29,146470");
            secondPostData.put("TxtPassWord", PASSWORD);
            secondPostData.put("__LASTFOCUS", "");
            secondPostData.put("__EVENTTARGET", "BtnBaglan");
            secondPostData.put("__EVENTARGUMENT", "");
            secondPostData.put("__ASYNCPOST", "true");
            secondPostData.put("__VIEWSTATE", viewState);
            secondPostData.put("__VIEWSTATEGENERATOR", viewStateGenerator);
            secondPostData.put("__EVENTVALIDATION", eventValidation);

            // Make second POST request
            String secondPostResponse = makePostRequest(initialUrl, headers, secondPostData, cookies);
            Log.d(TAG, "Second post response received");

            // Check if login was successful
            if (secondPostResponse.contains("success") || secondPostResponse.contains("başarı")) {
                Log.d(TAG, "Login successful");
                showToast(context, "Login successful!");
            } else {
                Log.d(TAG, "Login may have failed");
                showToast(context, "Login attempt complete, please check your connection");
            }

            // Wait and check internet again
            Thread.sleep(3000);
            if (hasInternet()) {
                Log.d(TAG, "Internet connection verified after login");
                showToast(context, "Internet connection verified!");
            } else {
                Log.d(TAG, "Still no internet after login");
                showToast(context, "Still no internet after login");
            }

        } catch (Exception e) {
            Log.e(TAG, "Login process failed", e);
            showToast(context, "Login failed: " + e.getMessage());
        }
    }

    private String makeGetRequest(String urlString, Map<String, String> headers) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        // Set headers
        for (Map.Entry<String, String> header : headers.entrySet()) {
            connection.setRequestProperty(header.getKey(), header.getValue());
        }

        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);

        StringBuilder response = new StringBuilder();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
                response.append('\n');
            }
        }

        connection.disconnect();
        return response.toString();
    }

    private String makePostRequest(String urlString, Map<String, String> headers,
            Map<String, String> postData, Map<String, String> cookies) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);

        // Set headers
        for (Map.Entry<String, String> header : headers.entrySet()) {
            connection.setRequestProperty(header.getKey(), header.getValue());
        }

        // Set cookies
        if (cookies != null && !cookies.isEmpty()) {
            StringBuilder cookieString = new StringBuilder();
            for (Map.Entry<String, String> cookie : cookies.entrySet()) {
                if (cookieString.length() > 0) {
                    cookieString.append("; ");
                }
                cookieString.append(cookie.getKey()).append("=").append(cookie.getValue());
            }
            connection.setRequestProperty("Cookie", cookieString.toString());
        }

        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);

        // Write post data
        StringBuilder postDataString = new StringBuilder();
        for (Map.Entry<String, String> param : postData.entrySet()) {
            if (postDataString.length() > 0) {
                postDataString.append("&");
            }
            postDataString.append(URLEncoder.encode(param.getKey(), "UTF-8"));
            postDataString.append("=");
            postDataString.append(URLEncoder.encode(param.getValue(), "UTF-8"));
        }

        try (java.io.OutputStream os = connection.getOutputStream()) {
            byte[] input = postDataString.toString().getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        StringBuilder response = new StringBuilder();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
                response.append('\n');
            }
        }

        connection.disconnect();
        return response.toString();
    }

    private String extractSessionId(String html) {
        Pattern pattern = Pattern.compile("ASP\\.NET_SessionId=([^;]+)");
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String[] extractFormValues(String html) {
        try {
            Document doc = Jsoup.parse(html);
            String viewState = doc.select("input#__VIEWSTATE").attr("value");
            String viewStateGenerator = doc.select("input#__VIEWSTATEGENERATOR").attr("value");
            String eventValidation = doc.select("input#__EVENTVALIDATION").attr("value");

            if (viewState.isEmpty() || viewStateGenerator.isEmpty() || eventValidation.isEmpty()) {
                return null;
            }

            return new String[] { viewState, viewStateGenerator, eventValidation };
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse HTML", e);
            return null;
        }
    }

    private String extractHiddenField(String html, String fieldName) {
        for (String part : html.split("\\|hiddenField\\|")) {
            if (part.startsWith(fieldName)) {
                String[] parts = part.split("\\|");
                if (parts.length > 1) {
                    return parts[1];
                }
            }
        }
        return null;
    }
}