package com.phoenixcorp.overlay;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {
    private static final ConfigManager INSTANCE = new ConfigManager();
    public static ConfigManager getInstance(){ return INSTANCE; }

    private final ObjectMapper om = new ObjectMapper();
    private final Path configPath = Path.of(System.getProperty("user.dir"), "config.json");
    private Config cached;

    private ConfigManager(){}

    public Config getConfig() {
        if (cached != null) return cached;
        try {
            if (Files.exists(configPath)) {
                cached = om.readValue(configPath.toFile(), Config.class);
            } else {
                cached = new Config();
                save(cached);
            }
        } catch (Exception e) {
            System.err.println("[Config] load error: " + e.getMessage());
            cached = new Config();
        }
        return cached;
    }

    public void save(Config cfg) {
        try {
            om.writerWithDefaultPrettyPrinter().writeValue(configPath.toFile(), cfg);
            cached = cfg;
        } catch (Exception e) {
            System.err.println("[Config] save error: " + e.getMessage());
        }
    }
}
