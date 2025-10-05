package com.phoenixcorp.overlay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phoenixcorp.overlay.api.LightingRuntime;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.awt.Rectangle;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class LightingDesignerServer {

    public static void main(String[] args) {
        LightingOverrides overrides = LightingOverrides.loadOrDefaults();
        LightingRuntime runtime = new LightingRuntime(overrides);
        ObjectMapper mapper = new ObjectMapper();

        int port = Integer.parseInt(System.getProperty("lighting.port", System.getenv().getOrDefault("PORT", "8080")));

        Javalin app = Javalin.create(config -> {
            config.http.defaultContentType = "application/json";
        });

        app.events(event -> {
            event.serverStopping(runtime::shutdown);
        });

        app.post("/runtime/start", ctx -> {
            boolean started = runtime.start();
            ctx.json(Map.of("running", true, "started", started));
        });

        app.post("/runtime/stop", ctx -> {
            boolean stopped = runtime.stop();
            ctx.json(Map.of("running", false, "stopped", stopped));
        });

        app.put("/overrides", ctx -> {
            LightingOverrides body = mapper.readValue(ctx.body(), LightingOverrides.class);
            runtime.updateOverrides(body, true);
            ctx.json(runtime.getOverrides());
        });

        app.post("/ocr/area", ctx -> {
            SelectCaptureArea.SelectionResult selection = SelectCaptureArea.selectInteractiveForApi();
            Map<String, Object> payload = new HashMap<>();
            payload.put("timedOut", selection.timedOut());
            if (selection.area() != null) {
                Rectangle area = selection.area();
                runtime.defineOcrArea(area);
                payload.put("x", area.x);
                payload.put("y", area.y);
                payload.put("width", area.width);
                payload.put("height", area.height);
                payload.put("selected", true);
            } else {
                payload.put("selected", false);
            }
            ctx.json(payload);
        });

        app.get("/preview", ctx -> respondWithPreview(runtime, ctx));

        app.get("/overrides", ctx -> ctx.json(runtime.getOverrides()));

        app.start(port);
    }

    private static void respondWithPreview(LightingRuntime runtime, Context ctx) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("matrix", runtime.currentPreviewMatrix());
        Optional<OcrReader.Snapshot> snap = runtime.getLastSnapshot();
        snap.ifPresent(snapshot -> payload.put("snapshot", Map.of(
                "hp", Map.of("cur", snapshot.hpCur, "max", snapshot.hpMax),
                "resource", Map.of("cur", snapshot.resCur, "max", snapshot.resMax)
        )));
        ctx.json(payload);
    }
}
