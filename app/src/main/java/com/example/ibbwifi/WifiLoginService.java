package com.example.ibbwifi;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.Nullable;

public class WifiLoginService extends Service {
    private static final String TAG = "WifiLoginService";
    private static final String TARGET_SSID = "ibbWiFi";
    private static final String PREFS_NAME = "IBBWifiPrefs";
    private static final String PREF_PHONE_NUMBER = "phone_number";
    private static final String PREF_PASSWORD = "password";
    private static final String PREF_MAC_ADDRESS = "mac_address";
    private static final String COUNTRY_CODE = "+90";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        new Thread(() -> {
            try {
                if (!hasInternet()) {
                    Log.d(TAG, "No internet, attempting login");
                    showToast("No internet, attempting login...");
                    performLogin();
                } else {
                    Log.d(TAG, "Already has internet connection");
                    showToast("Already connected to internet");
                }
            } finally {
                stopSelf();
            }
        }).start();

        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
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

    private void showToast(String message) {
        new Handler(Looper.getMainLooper())
                .post(() -> Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show());
    }

    private void performLogin() {
        try {
            // Get login credentials from SharedPreferences
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String phoneNumber = prefs.getString(PREF_PHONE_NUMBER, "");
            String password = prefs.getString(PREF_PASSWORD, "");
            String macAddress = prefs.getString(PREF_MAC_ADDRESS, "");

            // Check if credentials are available
            if (phoneNumber.isEmpty() || password.isEmpty() || macAddress.isEmpty()) {
                Log.e(TAG, "Missing credentials");
                showToast("Missing credentials. Please set them in the app.");

                // Open the main activity to set credentials
                Intent mainIntent = new Intent(this, MainActivity.class);
                mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(mainIntent);
                return;
            }

            // Use the provided MAC address
            Log.d(TAG, "Using MAC address: " + macAddress);
            String encodedMac = URLEncoder.encode(macAddress, "UTF-8");

            // Setup headers - exactly as in Python code
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent",
                    "Mozilla/5.0 (Platform; Security; OS-or-CPU; Localization; rv:1.4) Gecko/20030624 Netscape/7.1 (ax)");

            // Create a session with cookies
            Map<String, String> cookies = new HashMap<>();

            // First request to get the login page and initial form values
            String initialUrl = "http://captive.ibbwifi.istanbul/?mac=" + encodedMac;
            Log.d(TAG, "Making initial request to: " + initialUrl);
            HttpURLConnection initialConnection = makeInitialConnection(initialUrl, headers);

            // Get ASP.NET_SessionId cookie
            String setCookieHeader = initialConnection.getHeaderField("Set-Cookie");
            if (setCookieHeader != null) {
                Pattern pattern = Pattern.compile("ASP\\.NET_SessionId=([^;]+)");
                Matcher matcher = pattern.matcher(setCookieHeader);
                if (matcher.find()) {
                    cookies.put("ASP.NET_SessionId", matcher.group(1));
                    Log.d(TAG, "Session cookie obtained: " + matcher.group(1));
                }
            }

            String initialResponse = readResponseFromConnection(initialConnection);
            Log.d(TAG, "Initial response received, length: " + initialResponse.length());

            // Extract form values using JSoup
            Map<String, String> formValues = getFormValues(initialResponse);
            if (formValues.isEmpty()) {
                Log.e(TAG, "Failed to extract form values");
                showToast("Login failed: Could not extract form values");
                return;
            }

            Log.d(TAG, "Form values extracted successfully");

            // First post request with phone number - match Python code exactly
            Map<String, String> phonePostData = new HashMap<>();
            phonePostData.put("ScriptManager1", "UpdatePanel1|BtnGiris");
            phonePostData.put("CmbLang", "tr");
            phonePostData.put("CmbConutry", "+90"); // Hardcoded country code as in Python
            phonePostData.put("HdnGecici", formValues.getOrDefault("HdnGecici", ""));
            phonePostData.put("HdnWifiPoint", formValues.getOrDefault("HdnWifiPoint", ""));
            phonePostData.put("HdnLat", formValues.getOrDefault("HdnLat", ""));
            phonePostData.put("HdnLong", formValues.getOrDefault("HdnLong", ""));
            phonePostData.put("TxtPhoneNumber", phoneNumber);
            phonePostData.put("__LASTFOCUS", "");
            phonePostData.put("__EVENTTARGET", "BtnGiris");
            phonePostData.put("__EVENTARGUMENT", "");
            phonePostData.put("__VIEWSTATE", formValues.get("__VIEWSTATE"));
            phonePostData.put("__VIEWSTATEGENERATOR", formValues.get("__VIEWSTATEGENERATOR"));
            phonePostData.put("__EVENTVALIDATION", formValues.get("__EVENTVALIDATION"));
            phonePostData.put("__ASYNCPOST", "true");
            phonePostData.put("", "");

            // Make phone number post request
            Log.d(TAG, "Making phone number post request");
            String phoneResponse = makePostRequest(initialUrl, headers, phonePostData, cookies);
            Log.d(TAG, "Phone response received, length: " + phoneResponse.length());

            // Extract hidden fields from the phone response
            Map<String, String> hiddenFields = parseHiddenFields(phoneResponse);
            if (hiddenFields.isEmpty()) {
                Log.e(TAG, "Failed to extract hidden fields from phone response");
                showToast("Login failed: Could not extract hidden fields");
                return;
            }

            // Second post request with password - match Python code exactly
            Map<String, String> passwordPostData = new HashMap<>();
            passwordPostData.put("ScriptManager1", "UpdatePanel1|BtnBaglan");
            passwordPostData.put("__EVENTTARGET", "BtnBaglan");
            passwordPostData.put("TxtPassWord", password);
            passwordPostData.put("__VIEWSTATE",
                    hiddenFields.getOrDefault("__VIEWSTATE", formValues.get("__VIEWSTATE")));
            passwordPostData.put("__VIEWSTATEGENERATOR",
                    hiddenFields.getOrDefault("__VIEWSTATEGENERATOR", formValues.get("__VIEWSTATEGENERATOR")));
            passwordPostData.put("__EVENTVALIDATION",
                    hiddenFields.getOrDefault("__EVENTVALIDATION", formValues.get("__EVENTVALIDATION")));
            passwordPostData.put("__ASYNCPOST", "true");
            passwordPostData.put("", "");

            // Make password post request
            Log.d(TAG, "Making password post request");
            String loginResponse = makePostRequest(initialUrl, headers, passwordPostData, cookies);
            Log.d(TAG, "Login response received, length: " + loginResponse.length());

            // Log a preview of the response
            Log.d(TAG, "Login response preview: " +
                    (loginResponse.length() > 200 ? loginResponse.substring(0, 200) : loginResponse));

            // Check if login was successful
            if (loginResponse.contains("success") || loginResponse.contains("başarı") ||
                    loginResponse.contains("Giriş başarılı")) {
                Log.d(TAG, "Login successful");
                showToast("Login successful!");
            } else {
                Log.d(TAG, "Login may have failed, trying alternative method");

                // Try alternative approach - just open the captive portal page in a WebView
                Intent browserIntent = new Intent(this, WebViewActivity.class);
                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                browserIntent.putExtra("URL", initialUrl);
                startActivity(browserIntent);

                showToast("Opening captive portal in browser");
                return;
            }

            // Wait and check internet again
            Thread.sleep(3000);
            if (hasInternet()) {
                Log.d(TAG, "Internet connection verified after login");
                showToast("Internet connection verified!");
            } else {
                Log.d(TAG, "Still no internet after login");
                showToast("Still no internet after login");
            }

        } catch (Exception e) {
            Log.e(TAG, "Login process failed", e);
            showToast("Login failed: " + e.getMessage());
        }
    }

    private HttpURLConnection makeInitialConnection(String urlString, Map<String, String> headers) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        // Set headers
        for (Map.Entry<String, String> header : headers.entrySet()) {
            connection.setRequestProperty(header.getKey(), header.getValue());
            Log.d(TAG, "Setting header: " + header.getKey() + " = " + header.getValue());
        }

        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);

        // Just establish the connection
        Log.d(TAG, "Connecting to: " + urlString);
        connection.connect();

        // Log response code and headers
        int responseCode = connection.getResponseCode();
        Log.d(TAG, "Initial connection response code: " + responseCode);

        Log.d(TAG, "Initial connection response headers:");
        for (Map.Entry<String, List<String>> header : connection.getHeaderFields().entrySet()) {
            if (header.getKey() != null) {
                Log.d(TAG, header.getKey() + ": " + header.getValue());
            }
        }

        return connection;
    }

    private String readResponseFromConnection(HttpURLConnection connection) throws IOException {
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
                response.append('\n');
            }
        }
        return response.toString();
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
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream()))) {
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
            Log.d(TAG, "Setting cookies: " + cookieString.toString());
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

        // Log post data (truncated if too long)
        String postDataLog = postDataString.toString();
        Log.d(TAG,
                "POST data: " + (postDataLog.length() > 500
                        ? postDataLog.substring(0, 500) + "... (total length: " + postDataLog.length() + ")"
                        : postDataLog));

        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = postDataString.toString().getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        int responseCode = connection.getResponseCode();
        Log.d(TAG, "HTTP Response Code: " + responseCode);

        // Log response headers
        Log.d(TAG, "Response Headers:");
        for (Map.Entry<String, List<String>> header : connection.getHeaderFields().entrySet()) {
            if (header.getKey() != null) {
                Log.d(TAG, header.getKey() + ": " + header.getValue());
            }
        }

        StringBuilder response = new StringBuilder();
        try {
            java.io.InputStream inputStream;
            if (responseCode >= 400) {
                inputStream = connection.getErrorStream();
                Log.e(TAG, "Error response code: " + responseCode);
            } else {
                inputStream = connection.getInputStream();
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                    response.append('\n');
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading response", e);
        }

        connection.disconnect();
        return response.toString();
    }

    private Map<String, String> getFormValues(String html) {
        Map<String, String> values = new HashMap<>();
        try {
            Document doc = Jsoup.parse(html);

            // Get standard ASP.NET form values
            String viewState = doc.select("input#__VIEWSTATE").attr("value");
            String viewStateGenerator = doc.select("input#__VIEWSTATEGENERATOR").attr("value");
            String eventValidation = doc.select("input#__EVENTVALIDATION").attr("value");

            values.put("__VIEWSTATE", viewState);
            values.put("__VIEWSTATEGENERATOR", viewStateGenerator);
            values.put("__EVENTVALIDATION", eventValidation);

            // Log extraction results
            Log.d(TAG, "JSoup extraction - ViewState: "
                    + (viewState.isEmpty() ? "EMPTY" : "Found, length: " + viewState.length()));
            Log.d(TAG, "JSoup extraction - ViewStateGenerator: "
                    + (viewStateGenerator.isEmpty() ? "EMPTY" : "Found, length: " + viewStateGenerator.length()));
            Log.d(TAG, "JSoup extraction - EventValidation: "
                    + (eventValidation.isEmpty() ? "EMPTY" : "Found, length: " + eventValidation.length()));

            // Get WiFi point-related values
            for (String field : new String[] { "HdnGecici", "HdnWifiPoint", "HdnLat", "HdnLong" }) {
                Element element = doc.select("input#" + field).first();
                if (element != null) {
                    values.put(field, element.attr("value"));
                    Log.d(TAG, "JSoup extraction - " + field + ": Found, value: " + element.attr("value"));
                } else {
                    Log.d(TAG, "JSoup extraction - " + field + ": NOT FOUND");
                }
            }

            // If JSoup fails, try regex
            if (viewState.isEmpty() || viewStateGenerator.isEmpty() || eventValidation.isEmpty()) {
                Log.d(TAG, "JSoup extraction incomplete, trying regex extraction");
                return extractFormValuesWithRegex(html, new HashMap<>());
            }

            return values;
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse HTML with JSoup", e);
            return extractFormValuesWithRegex(html, new HashMap<>());
        }
    }

    private Map<String, String> extractFormValuesWithRegex(String html, Map<String, String> values) {
        try {
            // Extract standard ASP.NET form values with regex
            Pattern pattern = Pattern.compile("id=\"__VIEWSTATE\" value=\"([^\"]*)\"");
            Matcher matcher = pattern.matcher(html);
            if (matcher.find()) {
                String viewState = matcher.group(1);
                values.put("__VIEWSTATE", viewState);
                Log.d(TAG, "Regex extraction - ViewState: Found, length: " + viewState.length());
            } else {
                Log.e(TAG, "Regex extraction - ViewState: NOT FOUND");
            }

            pattern = Pattern.compile("id=\"__VIEWSTATEGENERATOR\" value=\"([^\"]*)\"");
            matcher = pattern.matcher(html);
            if (matcher.find()) {
                String viewStateGenerator = matcher.group(1);
                values.put("__VIEWSTATEGENERATOR", viewStateGenerator);
                Log.d(TAG, "Regex extraction - ViewStateGenerator: Found, length: " + viewStateGenerator.length());
            } else {
                Log.e(TAG, "Regex extraction - ViewStateGenerator: NOT FOUND");
            }

            pattern = Pattern.compile("id=\"__EVENTVALIDATION\" value=\"([^\"]*)\"");
            matcher = pattern.matcher(html);
            if (matcher.find()) {
                String eventValidation = matcher.group(1);
                values.put("__EVENTVALIDATION", eventValidation);
                Log.d(TAG, "Regex extraction - EventValidation: Found, length: " + eventValidation.length());
            } else {
                Log.e(TAG, "Regex extraction - EventValidation: NOT FOUND");
            }

            // Extract WiFi point-related values with regex
            for (String field : new String[] { "HdnGecici", "HdnWifiPoint", "HdnLat", "HdnLong" }) {
                pattern = Pattern.compile("id=\"" + field + "\" value=\"([^\"]*)\"");
                matcher = pattern.matcher(html);
                if (matcher.find()) {
                    String value = matcher.group(1);
                    values.put(field, value);
                    Log.d(TAG, "Regex extraction - " + field + ": Found, value: " + value);
                } else {
                    Log.d(TAG, "Regex extraction - " + field + ": NOT FOUND");
                }
            }

            return values;
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse HTML with regex", e);
            return values;
        }
    }

    private Map<String, String> parseHiddenFields(String response) {
        Map<String, String> hiddenFields = new HashMap<>();

        try {
            // Parse hidden fields from AJAX response
            Pattern pattern = Pattern.compile("\\|hiddenField\\|(__\\w+)\\|([^\\|]*)");
            Matcher matcher = pattern.matcher(response);
            int count = 0;

            while (matcher.find()) {
                count++;
                String key = matcher.group(1);
                String value = matcher.group(2);
                hiddenFields.put(key, value);
                Log.d(TAG,
                        "Found hidden field #" + count + ": " + key + " = "
                                + (value.length() > 20 ? value.substring(0, 20) + "... (length: " + value.length() + ")"
                                        : value));
            }

            if (count == 0) {
                Log.e(TAG, "No hidden fields found in response");
                // Log a portion of the response to help debug
                Log.d(TAG, "Response preview (first 200 chars): " +
                        (response.length() > 200 ? response.substring(0, 200) : response));
            } else {
                Log.d(TAG, "Total hidden fields found: " + count);
            }

            return hiddenFields;
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse hidden fields", e);
            return hiddenFields;
        }
    }
}