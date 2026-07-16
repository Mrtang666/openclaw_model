package com.openilink.http;

import com.openilink.exception.HTTPError;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

public class DefaultHttpClient implements HttpDoer {

    @Override
    public byte[] doPost(String url, byte[] body, Map<String, String> headers, long timeoutMs) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout((int) timeoutMs);
            conn.setReadTimeout((int) timeoutMs);
            if (headers != null) headers.forEach(conn::setRequestProperty);
            try (OutputStream os = conn.getOutputStream()) { os.write(body); os.flush(); }
            int statusCode = conn.getResponseCode();
            byte[] respBody = readStream(statusCode >= 400 ? conn.getErrorStream() : conn.getInputStream());
            if (statusCode >= 400) throw new HTTPError(statusCode, respBody);
            return respBody;
        } finally { conn.disconnect(); }
    }

    @Override
    public byte[] doGet(String url, Map<String, String> headers, long timeoutMs) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout((int) timeoutMs);
            conn.setReadTimeout((int) timeoutMs);
            if (headers != null) headers.forEach(conn::setRequestProperty);
            int statusCode = conn.getResponseCode();
            byte[] respBody = readStream(statusCode >= 400 ? conn.getErrorStream() : conn.getInputStream());
            if (statusCode >= 400) throw new HTTPError(statusCode, respBody);
            return respBody;
        } finally { conn.disconnect(); }
    }

    private byte[] readStream(InputStream is) throws IOException {
        if (is == null) return new byte[0];
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096]; int n;
            while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
            return bos.toByteArray();
        }
    }
}
