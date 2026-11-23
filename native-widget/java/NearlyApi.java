package com.benjamin.nearly; // CHANGE to your app's package

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Minimal REST helper (same style as the original widget: GET/PUT/DELETE on <path>.json). */
final class NearlyApi {
    static String get(String u) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(u).openConnection();
        try {
            h.setRequestMethod("GET");
            h.setConnectTimeout(8000);
            h.setReadTimeout(15000);
            int code = h.getResponseCode();
            if (code >= 300) throw new Exception("http " + code);
            return read(h.getInputStream());
        } finally {
            h.disconnect();
        }
    }

    /** method: PUT or DELETE. body is already-encoded JSON. */
    static void send(String method, String u, String body) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(u).openConnection();
        try {
            h.setRequestMethod(method);
            h.setConnectTimeout(8000);
            h.setReadTimeout(20000);
            if (!"DELETE".equals(method)) {
                h.setDoOutput(true);
                OutputStream o = h.getOutputStream();
                o.write((body == null ? "null" : body).getBytes("UTF-8"));
                o.close();
            }
            int code = h.getResponseCode();
            if (code >= 300) throw new Exception("http " + code);
            try { read(h.getInputStream()); } catch (Exception ignored) { }
        } finally {
            h.disconnect();
        }
    }

    private static String read(InputStream in) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        in.close();
        return b.toString("UTF-8");
    }
}
