package com.phoenixcorp.overlay.api;

import com.phoenixcorp.overlay.LightingOverrides;
import com.phoenixcorp.overlay.SelectCaptureArea;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.awt.Rectangle;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class LightingRuntimeController {

    private final LightingRuntime runtime;

    public LightingRuntimeController(LightingRuntime runtime) {
        this.runtime = runtime;
    }

    @PostMapping({"/runtime/start", "/start"})
    public Map<String, Object> startRuntime() {
        boolean started = runtime.start();
        Map<String, Object> payload = new HashMap<>();
        payload.put("running", true);
        payload.put("started", started);
        payload.put("status", started ? "OCR en cours" : "OCR déjà actif");
        return payload;
    }

    @PostMapping({"/runtime/stop", "/stop"})
    public Map<String, Object> stopRuntime() {
        boolean stopped = runtime.stop();
        Map<String, Object> payload = new HashMap<>();
        payload.put("running", false);
        payload.put("stopped", stopped);
        payload.put("status", stopped ? "Arrêté" : "Déjà arrêté");
        return payload;
    }

    @PutMapping("/overrides")
    public LightingOverrides updateOverrides(@RequestBody LightingOverrides overrides) {
        runtime.updateOverrides(overrides, true);
        return runtime.getOverrides();
    }

    @GetMapping("/overrides")
    public LightingOverrides getOverrides() {
        return runtime.getOverrides();
    }

    @GetMapping("/preview")
    public Map<String, Object> getPreview() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("matrix", runtime.currentPreviewMatrix());
        runtime.getLastSnapshot().ifPresent(snapshot -> payload.put("snapshot", Map.of(
                "hp", Map.of("cur", snapshot.hpCur, "max", snapshot.hpMax),
                "resource", Map.of("cur", snapshot.resCur, "max", snapshot.resMax)
        )));
        return payload;
    }

    @PostMapping({"/ocr/area", "/define-area"})
    public Map<String, Object> selectOcrArea() {
        SelectCaptureArea.SelectionResult selection = SelectCaptureArea.selectInteractiveForApi();
        Map<String, Object> payload = new HashMap<>();
        payload.put("timedOut", selection.timedOut());
        Rectangle area = selection.area();
        if (area != null) {
            runtime.defineOcrArea(area);
            payload.put("selected", true);
            payload.put("x", area.x);
            payload.put("y", area.y);
            payload.put("width", area.width);
            payload.put("height", area.height);
            payload.put("status", "Zone OCR définie");
        } else {
            payload.put("selected", false);
            payload.put("status", selection.timedOut() ? "Sélection OCR expirée" : "Sélection OCR annulée");
        }
        return payload;
    }
}
