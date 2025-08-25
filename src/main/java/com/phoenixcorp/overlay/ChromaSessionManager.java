package com.phoenixcorp.overlay;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Client REST Razer Chroma SDK (Broadcast).
 * - register auto
 * - heartbeat périodique
 * - auto-reconnect si session perdue
 * - rejoue la dernière frame
 * - API "fond" pour colorer tous les devices disponibles
 */
public class ChromaSessionManager implements AutoCloseable {
    private static final String REG_URL = "http://localhost:54235/razer/chromasdk";
    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final long HEARTBEAT_MS = 2000;

    private final ObjectMapper om = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    private String sessionUri;
    private String keyboardUrl;
    private String heartbeatUrl;

    // Autres endpoints (pour couleurs statiques)
    private String mouseUrl, mousepadUrl, headsetUrl, keypadUrl, chromalinkUrl;

    private volatile boolean closed = false;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "chroma-heartbeat"); t.setDaemon(true); return t;
    });
    private ScheduledFuture<?> heartbeatTask;

    private volatile int[][] lastFrame; // rejouée après reconnect

    public ChromaSessionManager() {
        try {
            ensureSession();
            startHeartbeat();
            System.out.println("[Chroma] Session ready: " + sessionUri);
        } catch (Exception e) {
            System.err.println("[Chroma] init failed: " + e.getMessage());
        }
    }

    // ================== API publique ==================

    /** Envoie une frame 6x22 (BGR) au clavier. */
    public synchronized void keyboardCustom(int[][] matrixBgr) {
        if (closed || matrixBgr == null) return;
        lastFrame = matrixBgr;
        try {
            ensureSession();
            putKeyboard(matrixBgr);
        } catch (Exception e) {
            System.err.println("[Chroma] keyboardCustom failed: " + e.getMessage());
            tryReconnectAndRetry(matrixBgr);
        }
    }

    /** Met une couleur de fond statique sur tous les périphériques enregistrés. */
    public synchronized void setStaticAllDevices(int bgr) {
        if (closed) return;
        try {
            ensureSession();
            // Clavier: on ne touche pas la matrice ici (le fond clavier est géré par SnapshotToMatrix)
            // Autres devices: on pousse CHROMA_STATIC
            putStatic(mouseUrl, bgr);
            putStatic(mousepadUrl, bgr);
            putStatic(headsetUrl, bgr);
            putStatic(keypadUrl, bgr);
            putStatic(chromalinkUrl, bgr);
        } catch (Exception e) {
            System.err.println("[Chroma] setStaticAllDevices failed: " + e.getMessage());
        }
    }

    /** Eteint tous les devices connus. */
    public synchronized void clearAll() {
        try {
            ensureSession();
            putStatic(mouseUrl, 0);
            putStatic(mousepadUrl, 0);
            putStatic(headsetUrl, 0);
            putStatic(keypadUrl, 0);
            putStatic(chromalinkUrl, 0);
            // Clavier → matrice vide
            putKeyboard(new int[6][22]);
        } catch (Exception ignore) { }
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        stopHeartbeat();
        try { clearAll(); } catch (Exception ignore) {}
        if (sessionUri != null) {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .timeout(TIMEOUT)
                        .uri(URI.create(sessionUri))
                        .DELETE()
                        .build();
                http.send(req, HttpResponse.BodyHandlers.discarding());
                System.out.println("[Chroma] Session closed.");
            } catch (Exception ignore) { }
        }
        sessionUri = null;
        keyboardUrl = heartbeatUrl = null;
        mouseUrl = mousepadUrl = headsetUrl = keypadUrl = chromalinkUrl = null;
        scheduler.shutdownNow();
    }

    // ================== interne ==================

    private void ensureSession() throws IOException, InterruptedException {
        if (sessionUri != null && keyboardUrl != null && heartbeatUrl != null) return;
        register();
        startHeartbeat();
    }

    private void register() throws IOException, InterruptedException {
        Map<String, Object> body = new HashMap<>();
        body.put("title", "LightingDesigner");
        body.put("description", "OCR-driven effects");
        body.put("author", Map.of("name", "PhoenixCorp", "contact", "n/a"));
        body.put("device_supported", new String[]{
                "keyboard", "mouse", "mousepad", "headset", "keypad", "chromalink"
        });
        body.put("category", "application");

        HttpRequest req = HttpRequest.newBuilder()
                .timeout(TIMEOUT)
                .uri(URI.create(REG_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(om.writeValueAsString(body)))
                .build();

        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                throw new IOException("Register failed: HTTP " + res.statusCode() + " body=" + res.body());
            }
            Map<?,?> m = om.readValue(res.body(), Map.class);
            Object uri = m.get("uri");
            if (uri == null) throw new IOException("Register response has no 'uri'");
            sessionUri = uri.toString();
            keyboardUrl   = sessionUri + "/keyboard";
            heartbeatUrl  = sessionUri + "/heartbeat";
            mouseUrl      = sessionUri + "/mouse";
            mousepadUrl   = sessionUri + "/mousepad";
            headsetUrl    = sessionUri + "/headset";
            keypadUrl     = sessionUri + "/keypad";
            chromalinkUrl = sessionUri + "/chromalink";
        } catch (ConnectException ce) {
            throw new IOException("Chroma service not reachable. Is Razer Synapse/Chroma SDK running?", ce);
        }
    }

    private void startHeartbeat() {
        stopHeartbeat();
        heartbeatTask = scheduler.scheduleAtFixedRate(() -> {
            if (closed || heartbeatUrl == null) return;
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .timeout(TIMEOUT)
                        .uri(URI.create(heartbeatUrl))
                        .PUT(HttpRequest.BodyPublishers.noBody()) // heartbeat = PUT sans body
                        .build();
                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() == 404 || res.statusCode() == 410) {
                    System.err.println("[Chroma] Heartbeat lost session, re-registering...");
                    invalidateSession();
                    ensureSession();
                    resendLastFrame();
                }
            } catch (Exception ignore) { /* on retente au tick suivant */ }
        }, HEARTBEAT_MS, HEARTBEAT_MS, TimeUnit.MILLISECONDS);
    }

    private void stopHeartbeat() {
        if (heartbeatTask != null) { heartbeatTask.cancel(true); heartbeatTask = null; }
    }

    private synchronized void invalidateSession() {
        sessionUri = null;
        keyboardUrl = heartbeatUrl = null;
        mouseUrl = mousepadUrl = headsetUrl = keypadUrl = chromalinkUrl = null;
    }

    private synchronized void tryReconnectAndRetry(int[][] matrix) {
        try {
            invalidateSession();
            ensureSession();
            if (matrix != null) putKeyboard(matrix);
        } catch (Exception e2) {
            System.err.println("[Chroma] retry failed: " + e2.getMessage());
        }
    }

    private synchronized void resendLastFrame() {
        int[][] lf = lastFrame;
        if (lf == null) return;
        try { putKeyboard(lf); } catch (Exception ignore) {}
    }

    private void putKeyboard(int[][] matrixBgr) throws IOException, InterruptedException {
        Map<String,Object> body = new HashMap<>();
        body.put("effect", "CHROMA_CUSTOM");
        body.put("param", matrixBgr);

        HttpRequest req = HttpRequest.newBuilder()
                .timeout(TIMEOUT)
                .uri(URI.create(keyboardUrl))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(om.writeValueAsString(body)))
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 404 || res.statusCode() == 410) {
            throw new IOException("Session lost (HTTP " + res.statusCode() + ")");
        }
        if (res.statusCode() / 100 != 2) {
            throw new IOException("PUT /keyboard failed: HTTP " + res.statusCode() + " body=" + res.body());
        }
    }

    /** Envoie CHROMA_STATIC sur un endpoint device (mouse, mousepad, etc.). */
    private void putStatic(String deviceUrl, int bgr) throws IOException, InterruptedException {
        if (deviceUrl == null) return; // device pas dispo dans l'env
        Map<String,Object> body = new HashMap<>();
        body.put("effect", "CHROMA_STATIC");
        body.put("param", Map.of("color", bgr));

        HttpRequest req = HttpRequest.newBuilder()
                .timeout(TIMEOUT)
                .uri(URI.create(deviceUrl))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(om.writeValueAsString(body)))
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 404 || res.statusCode() == 410) {
            // session peut être tombée → relance silencieuse
            invalidateSession();
            ensureSession();
            // re-try une fois
            putStatic(deviceUrl, bgr);
            return;
        }
        if (res.statusCode() / 100 != 2) {
            // on log soft et on continue (certains devices peuvent ne pas supporter STATIC)
            System.err.println("[Chroma] PUT " + deviceUrl + " failed: HTTP " + res.statusCode());
        }
    }
}
