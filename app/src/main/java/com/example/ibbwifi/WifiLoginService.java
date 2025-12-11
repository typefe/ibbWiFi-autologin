package com.example.ibbwifi;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WifiLoginService extends Service {
    private static final String TAG = "WifiLoginService";
    private static final String PREFS_NAME = "IBBWifiPrefs";
    private static final String PREF_PHONE_NUMBER = "phone_number";
    private static final String PREF_PASSWORD = "password";
    private static final String PREF_MAC_ADDRESS = "mac_address";

    private static final String PORTAL_HOST = "captive.ibbwifi.istanbul";
    private static final String COUNTRY_CODE = "90";
    private static final String FLAG_CODE = "tr";
    private static final int MAX_REDIRECTS = 4;
    // Force a desktop user-agent to prevent captive portal from redirecting to mobile-app flows.
    static final String DESKTOP_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36";

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

    private void performLogin() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String phoneNumberRaw = prefs.getString(PREF_PHONE_NUMBER, "");
        String password = prefs.getString(PREF_PASSWORD, "");
        String macAddress = prefs.getString(PREF_MAC_ADDRESS, "");

        if (phoneNumberRaw.isEmpty() || password.isEmpty() || macAddress.isEmpty()) {
            Log.e(TAG, "Missing credentials");
            showToast("Missing credentials. Please set them in the app.");

            Intent mainIntent = new Intent(this, MainActivity.class);
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(mainIntent);
            return;
        }

        String phoneNumberDigits = phoneNumberRaw.replaceAll("\\D", "");
        if (phoneNumberDigits.length() < 10) {
            showToast("Phone number looks incomplete");
            Log.e(TAG, "Phone number missing digits: " + phoneNumberRaw);
            return;
        }

        try {
            String encodedMac = URLEncoder.encode(macAddress, "UTF-8");

            CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
            CookieHandler.setDefault(cookieManager);

            Map<String, String> baseHeaders = buildBaseHeaders();

            PortalConnection portalConnection = connectToPortal(encodedMac, baseHeaders);
            if (portalConnection == null) {
                showToast("Login failed: portal unreachable");
                Log.e(TAG, "Portal unreachable");
                return;
            }

            String baseUrl = portalConnection.baseUrl;
            logCookieStatus(cookieManager);

            Map<String, String> jsonHeaders = new HashMap<>(baseHeaders);
            jsonHeaders.put("User-Agent", DESKTOP_USER_AGENT);
            jsonHeaders.put("Content-Type", "application/json");
            jsonHeaders.put("Origin", baseUrl);
            jsonHeaders.put("Referer", baseUrl + "/");

            // Step 2: Send phone number
            String phonePayload = String.format(Locale.US,
                    "{\"PhoneNumber\":\"%s\",\"CountryCode\":\"%s\",\"FlagCode\":\"%s\"}",
                    phoneNumberDigits, COUNTRY_CODE, FLAG_CODE);

            HttpResult phoneResult = executePostJson(baseUrl + "/LandingCheck", jsonHeaders, phonePayload);
            Log.d(TAG, "Phone submission status: " + phoneResult.code + " final URL: " + phoneResult.finalUrl);

            String loginUuid = extractUserId(phoneResult.body);
            if (loginUuid == null) {
                Log.e(TAG, "Failed to find login UUID after phone submission");
                Log.d(TAG, "Phone response preview: " + preview(phoneResult.body));
                showToast("Login failed: UUID not found after phone step");
                return;
            }
            Log.d(TAG, "Login UUID: " + loginUuid);

            // Final step: Send password
            String passwordPayload = String.format(Locale.US, "{\"Password\":\"%s\"}", password);
            HttpResult loginResult = executePostJson(baseUrl + "/" + loginUuid + "/Login", jsonHeaders,
                    passwordPayload);
            Log.d(TAG, "Password submission status: " + loginResult.code);
            Log.d(TAG, "Login response preview: " + preview(loginResult.body));

            boolean likelySuccess = loginResult.code == HttpURLConnection.HTTP_OK
                    && !loginResult.body.toLowerCase(Locale.US).contains("error");
            if (likelySuccess) {
                showToast("Login request sent. Checking internet...");
            } else {
                showToast("Login response returned possible error");
            }

            // Verify connectivity after giving the portal a moment to process
            Thread.sleep(3000);
            if (hasInternet()) {
                showToast("Internet connection verified!");
            } else {
                showToast("Still no internet after login");
            }

        } catch (Exception e) {
            Log.e(TAG, "Login process failed", e);
            showToast("Login failed: " + e.getMessage());
        }
    }

    private Map<String, String> buildBaseHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", DESKTOP_USER_AGENT);
        headers.put("Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");
        headers.put("Accept-Language", "en-US,en;q=0.9");
        headers.put("Sec-Ch-Ua", "\"Chromium\";v=\"135\", \"Not-A.Brand\";v=\"8\"");
        headers.put("Sec-Ch-Ua-Mobile", "?0");
        headers.put("Sec-Ch-Ua-Platform", "\"Linux\"");
        headers.put("Upgrade-Insecure-Requests", "1");
        return headers;
    }

    private HttpResult executeGet(String url, Map<String, String> headers) throws IOException {
        Log.d(TAG, "GET " + url);
        return executeWithRedirects(url, "GET", headers, null);
    }

    private HttpResult executePostJson(String url, Map<String, String> headers, String payload) throws IOException {
        Log.d(TAG, "POST " + url + " payload length: " + payload.length());
        return executeWithRedirects(url, "POST", headers, payload);
    }

    @Nullable
    private PortalConnection connectToPortal(String encodedMac, Map<String, String> baseHeaders) {
        String baseUrl = "http://" + PORTAL_HOST;
        String step1Url = baseUrl + "/?mac=" + encodedMac;
        try {
            Log.d(TAG, "Connecting to portal (HTTP first, will follow HTTPS redirect if any)");
            HttpResult result = executeGet(step1Url, baseHeaders);
            String resolvedBaseUrl = extractBaseUrl(result.finalUrl != null ? result.finalUrl : baseUrl);
            Log.d(TAG, "Portal reachable at: " + resolvedBaseUrl);
            return new PortalConnection(resolvedBaseUrl, result);
        } catch (IOException e) {
            Log.w(TAG, "Portal connection failed: " + e.getMessage());
            return null;
        }
    }

    private HttpResult executeWithRedirects(String url, String method, Map<String, String> headers,
            @Nullable String payload) throws IOException {
        String currentUrl = url;
        String currentMethod = method;

        for (int redirect = 0; redirect < MAX_REDIRECTS; redirect++) {
            HttpURLConnection connection = openConnection(currentUrl, currentMethod, headers);
            if ("POST".equals(currentMethod) && payload != null) {
                connection.setDoOutput(true);
                byte[] input = payload.getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(input);
                }
            }

            int code = connection.getResponseCode();
            String body = readResponse(connection);

            if (isRedirect(code)) {
                String location = connection.getHeaderField("Location");
                if (location != null) {
                    currentUrl = resolveUrl(currentUrl, location);
                    currentMethod = "GET";
                    Log.d(TAG, "Following redirect to: " + currentUrl);
                    connection.disconnect();
                    continue;
                }
            }

            connection.disconnect();
            return new HttpResult(code, body, currentUrl);
        }

        throw new IOException("Too many redirects");
    }

    private HttpURLConnection openConnection(String urlString, String method, Map<String, String> headers)
            throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod(method);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);

        for (Map.Entry<String, String> header : headers.entrySet()) {
            connection.setRequestProperty(header.getKey(), header.getValue());
        }

        return connection;
    }

    private String readResponse(HttpURLConnection connection) throws IOException {
        InputStream inputStream;
        int responseCode = connection.getResponseCode();
        try {
            if (responseCode >= HttpURLConnection.HTTP_BAD_REQUEST) {
                inputStream = connection.getErrorStream();
            } else {
                inputStream = connection.getInputStream();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to obtain response stream", e);
            throw new IOException("Failed to read response stream", e);
        }

        if (inputStream == null) {
            return "";
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line).append('\n');
            }
        }
        return response.toString();
    }

    private boolean isRedirect(int code) {
        return code == HttpURLConnection.HTTP_MOVED_PERM
                || code == HttpURLConnection.HTTP_MOVED_TEMP
                || code == HttpURLConnection.HTTP_SEE_OTHER
                || code == 307
                || code == 308;
    }

    private String resolveUrl(String base, String location) throws IOException {
        URL baseUrl = new URL(base);
        URL resolved = new URL(baseUrl, location);
        return resolved.toString();
    }

    @Nullable
    private String extractUserId(String html) {
        try {
            Document doc = Jsoup.parse(html);
            Element userIdDiv = doc.getElementById("UserId");
            if (userIdDiv != null) {
                String value = userIdDiv.attr("data-userid");
                if (value != null && !value.isEmpty()) {
                    return value;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing HTML for userId", e);
        }

        try {
            Pattern pattern = Pattern.compile("id=\"UserId\"[^>]*data-userid=\"([^\"]+)\"");
            Matcher matcher = pattern.matcher(html);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            Log.e(TAG, "Fallback regex parse failed", e);
        }

        return null;
    }

    private void logCookieStatus(CookieManager cookieManager) {
        try {
            List<HttpCookie> cookies = cookieManager.getCookieStore().getCookies();
            for (HttpCookie cookie : cookies) {
                Log.d(TAG, "Cookie: " + cookie.getName() + "=" + cookie.getValue());
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to read cookies", e);
        }
    }

    private String preview(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 200 ? text.substring(0, 200) + "..." : text;
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

    private static class HttpResult {
        final int code;
        final String body;
        final String finalUrl;

        HttpResult(int code, String body, String finalUrl) {
            this.code = code;
            this.body = body;
            this.finalUrl = finalUrl;
        }
    }

    private static class PortalConnection {
        final String baseUrl;
        final HttpResult initialResult;

        PortalConnection(String baseUrl, HttpResult initialResult) {
            this.baseUrl = baseUrl;
            this.initialResult = initialResult;
        }
    }

    private String extractBaseUrl(String urlString) {
        try {
            URL url = new URL(urlString);
            return url.getProtocol() + "://" + url.getHost();
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse base URL from: " + urlString, e);
            return "http://" + PORTAL_HOST;
        }
    }
}
