package io.github.gohoski.notpipe.http;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

import io.github.gohoski.notpipe.BuildConfig;
import io.github.gohoski.notpipe.NotPipe;
import io.github.gohoski.notpipe.Utils;

/**
 * Created by Gleb on 21.08.2025.
 * HTTP Client
 */
public class HttpClient {
    public static final String USER_AGENT = "notPipe/" + BuildConfig.VERSION_NAME + " (https://github.com/gohoski/notPipe)";
    private static final int TIMEOUT = 15000;
    public static final int VIDEO_TIMEOUT = 60000;
    public static final int CONVERSION_TIMEOUT = 90000;

    public interface DownloadProgressListener {
        /**
         * Invoked to publish downloaded bytes progression.
         * @return true to continue downloading, false to abort and clean up.
         */
        boolean onProgress(long bytesDownloaded, long totalBytes);
    }

    public static InputStream execute(HttpRequest request) throws IOException {
        return execute(request, TIMEOUT);
    }

    /**
     * Executes an HTTP request with a custom timeout
     * @param request The request to execute
     * @param timeout Timeout in milliseconds
     * @return InputStream with the response
     * @throws IOException If there's an error executing the request
     */
    public static InputStream execute(HttpRequest request, int timeout) throws IOException {
        return execute(request, timeout, 0);
    }

    private static InputStream execute(HttpRequest request, int timeout, int retryCount) throws IOException {
        Utils.waitForConnection(NotPipe.getAppContext());
        HttpURLConnection connection = null;
        try {
            URL url = new URL(request.getBaseUrl() + request.getEndpoint());
            Log.d("HttpClient", url.toString());
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(request.getMethod());
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(timeout);

            connection.setRequestProperty("User-Agent", USER_AGENT);

            // Apply all custom headers from HttpRequest
            for (Map.Entry<String, String> entry : request.getHeaders().entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }

            // Write POST/PUT body if present
            if (request.getBody() != null && request.getBody().length() > 0) {
                connection.setDoOutput(true);
                if (!request.getHeaders().containsKey("Content-Type"))
                    connection.setRequestProperty("Content-Type", "application/json");
                byte[] postData = request.getBody().getBytes("UTF-8");
                connection.setFixedLengthStreamingMode(postData.length);
                OutputStream os = connection.getOutputStream();
                os.write(postData);
                os.flush();
                os.close();
            }

            int status;
            try {
                status = connection.getResponseCode();
            } catch (IOException e) {
                String msg = e.getMessage();
                if (msg != null && msg.toLowerCase().contains("authentication challenge")) {
                    String errorBody = null;
                    try { errorBody = streamToString(connection.getErrorStream()); } catch (Exception ignored) {}
                    connection.disconnect();
                    throw new HttpException(401, "HTTP 401 " + url + " (authentication challenge is null)", errorBody);
                }
                throw e;
            }

            // If the socket was dropped by the server or connection pool, retry once with full request data
            if (status == -1) {
                connection.disconnect();
                if (retryCount < 1) {
                    Log.w("HttpClient", "Received HTTP -1 (stale connection), retrying request: " + url);
                    return execute(request, timeout, retryCount + 1);
                }
                throw new HttpException(-1, "HTTP -1 (Connection dropped or invalid response) " + url, null);
            }

            if (status >= 200 && status < 300) {
                return new ConnectionInputStream(connection.getInputStream(), connection);
            } else {
                InputStream errorStream = connection.getErrorStream();
                String errorBody = streamToString(errorStream);
                connection.disconnect();
                throw new HttpException(status, "HTTP " + status + " " + url, errorBody);
            }
        } catch (IOException e) {
            if (e instanceof HttpException) {
                throw e;
            }
            if (connection != null) {
                connection.disconnect();
            }
            throw e;
        }
    }

    /**
     * Executes the API request and returns the response body as a String
     *
     * @param request The API request to execute
     * @return The response body as a String
     * @throws HttpException If there's an error executing the request
     */
    @SuppressWarnings("ConstantConditions")
    public static String executeToString(HttpRequest request) throws IOException {
        return executeToString(request, TIMEOUT);
    }

    /**
     * Executes the API request with custom timeout and returns the response body as a String
     *
     * @param request The API request to execute
     * @param timeout Timeout in milliseconds
     * @return The response body as a String
     * @throws HttpException If there's an error executing the request
     */
    @SuppressWarnings("ConstantConditions")
    public static String executeToString(HttpRequest request, int timeout) throws IOException {
        InputStream in = null;
        try {
            in = execute(request, timeout);
            return streamToString(in);
        } catch (HttpException e) {
            // If the server threw a 4xx/5xx but handed us a structured JSON error payload,
            // return it as a normal String so the API classes can extract "error" or "message".
            String body = e.getResponseBody();
            if (body != null) body = body.trim();
            if (body != null && body.startsWith("{") && body.endsWith("}")) {
                return body;
            }
            throw e;
        } finally {
            if (in != null) {
                try { in.close(); } catch (IOException ignored) {}
            }
        }
    }

    /**
     * Makes a GET request and returns the redirect URL from the Location header.
     *
     * @param baseUrl The base URL
     * @param urlString The URL path to request
     * @return The redirect URL from the Location header
     * @throws IOException If there's an error making the request
     */
    public static String getRedirectUrl(String baseUrl, String urlString) throws IOException {
        return getRedirectUrl(baseUrl, urlString, 40000);
    }

    /**
     * Makes a GET request and returns the redirect URL from the Location header with custom timeout.
     *
     * @param baseUrl The base URL
     * @param urlString The URL path to request
     * @param timeout Timeout in milliseconds
     * @return The redirect URL from the Location header
     * @throws IOException If there's an error making the request
     */
    public static String getRedirectUrl(String baseUrl, String urlString, int timeout) throws IOException {
        Utils.waitForConnection(NotPipe.getAppContext());
        HttpURLConnection connection = null;
        try {
            URL url = new URL(baseUrl+urlString);
            Log.d("HttpClient", url.toString());
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(timeout);
            connection.setReadTimeout(timeout);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setInstanceFollowRedirects(false);
            int status;
            try {
                status = connection.getResponseCode();
            } catch (IOException e) {
                String msg = e.getMessage();
                if (msg != null && msg.toLowerCase().indexOf("authentication challenge") != -1) {
                    throw new HttpException(401, "HTTP 401 " + url + " (authentication challenge is null)", null);
                }
                throw e;
            }
            if (status == 404) {
                throw new FileNotFoundException("Video quality not available (HTTP 404)");
            }
            String loc = connection.getHeaderField("Location");
            if (loc == null)
                throw new IOException("No video url returned");
            else
                return baseUrl + loc;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Helper method to convert InputStream to String
     */
    private static String streamToString(InputStream is) throws IOException {
        if (is == null) return "";
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[1024];
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        buffer.flush();
        return new String(buffer.toByteArray(), "UTF-8");
    }

    /**
     * Downloads a file from URL and saves it to the specified path.
     * If download fails, the partially downloaded file is deleted.
     *
     * @param urlString The URL to download from
     * @param outputPath The file path to save to
     * @throws IOException If there's an error downloading or saving
     */
    public static void downloadToFile(String urlString, String outputPath) throws IOException {
        downloadToFile(urlString, outputPath, urlString.contains("&codec=") ? CONVERSION_TIMEOUT : VIDEO_TIMEOUT, null);
    }

    /**
     * Downloads a file from URL and saves it to the specified path with progress reporting.
     * If download fails, the partially downloaded file is deleted.
     *
     * @param urlString The URL to download from
     * @param outputPath The file path to save to
     * @param listener Callback for download progress updates
     * @throws IOException If there's an error downloading or saving
     */
    public static void downloadToFile(String urlString, String outputPath, DownloadProgressListener listener) throws IOException {
        downloadToFile(urlString, outputPath, urlString.contains("&codec=") ? CONVERSION_TIMEOUT : VIDEO_TIMEOUT, listener);
    }

    /**
     * Downloads a file from URL and saves it to the specified path with custom timeout and progress reporting.
     * If download fails, the partially downloaded file is deleted.
     *
     * @param urlString The URL to download from
     * @param outputPath The file path to save to
     * @param timeout Timeout in milliseconds
     * @param listener Callback for download progress updates
     * @throws IOException If there's an error downloading or saving
     */
    public static void downloadToFile(String urlString, String outputPath, int timeout, DownloadProgressListener listener) throws IOException {
        HttpRequest request = new HttpRequest(urlString, "");
        InputStream in = null;
        java.io.BufferedInputStream bin = null;
        java.io.FileOutputStream out = null;
        java.io.File file = new java.io.File(outputPath);
        boolean success = false;
        // 32 KB buffer matches standard FAT32 cluster sizes on SD cards, reducing I/O context switches
        final int BUFFER_SIZE = 32768;
        try {
            in = execute(request, timeout);
            bin = new java.io.BufferedInputStream(in, BUFFER_SIZE);
            long totalBytes = -1;
            if (in instanceof ConnectionInputStream) {
                totalBytes = ((ConnectionInputStream) in).getContentLength();
            }
            java.io.File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            out = new java.io.FileOutputStream(file);
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            long bytesDownloaded = 0;
            while ((bytesRead = bin.read(buffer, 0, BUFFER_SIZE)) != -1) {
                out.write(buffer, 0, bytesRead);
                bytesDownloaded += bytesRead;
                if (listener != null) {
                    if (!listener.onProgress(bytesDownloaded, totalBytes)) {
                        throw new IOException("Download aborted by client request.");
                    }
                }
            }
            out.flush();
            // Sync file descriptors to ensure data is committed to SD card before returning
            try {
                out.getFD().sync();
            } catch (Exception ignored) {}

            success = true;
        } finally {
            if (bin != null) {
                try { bin.close(); } catch (IOException ignored) {}
            } else if (in != null) {
                try { in.close(); } catch (IOException ignored) {}
            }
            if (out != null) {
                try { out.close(); } catch (IOException ignored) {}
            }
            if (!success && file.exists()) {
                file.delete();
            }
        }
    }
}