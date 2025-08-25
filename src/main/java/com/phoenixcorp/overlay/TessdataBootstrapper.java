package com.phoenixcorp.overlay;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;

public final class TessdataBootstrapper {
    private TessdataBootstrapper() {}

    /**
     * Garantit la présence de ~/.lightingdesigner/tessdata et des fichiers <lang>.traineddata.
     * Retourne le chemin **du dossier tessdata** (à passer à Tesseract.setDatapath()).
     */
    public static Path ensureLocalTessdata(String... langs) {
        String home = System.getProperty("user.home");
        Path base = Paths.get(home, ".lightingdesigner");
        Path tessDir = base.resolve("tessdata");
        try {
            Files.createDirectories(tessDir);
            if (langs != null) {
                for (String lang : langs) {
                    String l = (lang == null || lang.isBlank()) ? "eng" : lang.trim();
                    String resourcePath = "/tessdata/" + l + ".traineddata";
                    Path target = tessDir.resolve(l + ".traineddata");
                    if (Files.notExists(target) || Files.size(target) == 0L) {
                        try (InputStream is = TessdataBootstrapper.class.getResourceAsStream(resourcePath)) {
                            if (is == null) {
                                System.err.println("[Tessdata] Ressource introuvable: " + resourcePath);
                            } else {
                                Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
                                System.out.println("[Tessdata] Copié: " + target);
                            }
                        }
                    }
                }
            }
            return tessDir; //
        } catch (IOException e) {
            throw new RuntimeException("Impossible de préparer tessdata: " + e.getMessage(), e);
        }
    }
}
