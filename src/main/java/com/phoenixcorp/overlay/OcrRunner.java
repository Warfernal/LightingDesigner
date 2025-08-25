package com.phoenixcorp.overlay;

import java.util.concurrent.*;
import java.util.function.Consumer;

public final class OcrRunner {
    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ocr-runner"); t.setDaemon(true); return t;
    });
    private ScheduledFuture<?> task;
    private final OcrReader reader;
    private final Consumer<OcrReader.Snapshot> onSnapshot;
    private final long periodMs;

    public OcrRunner(OcrReader reader, Consumer<OcrReader.Snapshot> onSnapshot) {
        this(reader, onSnapshot, 100);
    }

    public OcrRunner(OcrReader reader, Consumer<OcrReader.Snapshot> onSnapshot, long periodMs) {
        this.reader = reader; this.onSnapshot = onSnapshot; this.periodMs = Math.max(30, periodMs);
    }

    public synchronized void start() {
        if (task != null && !task.isCancelled() && !task.isDone()) return;
        task = exec.scheduleAtFixedRate(() -> {
            try {
                OcrReader.Snapshot s = reader.read();
                if (s != null) onSnapshot.accept(s);
            } catch (Throwable t) {
                System.err.println("[OCR] Error: " + t.getMessage());
            }
        }, 0, periodMs, TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        if (task != null) { task.cancel(true); task = null; }
    }

    public synchronized boolean isRunning() {
        return task != null && !task.isCancelled() && !task.isDone();
    }

    public void shutdown() { stop(); exec.shutdownNow(); }
}
