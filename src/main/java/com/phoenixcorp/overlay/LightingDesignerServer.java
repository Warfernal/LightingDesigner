package com.phoenixcorp.overlay;

import com.phoenixcorp.overlay.api.LightingRuntime;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class LightingDesignerServer {

    public static void main(String[] args) {
        SpringApplication.run(LightingDesignerServer.class, args);
    }

    @Bean
    public LightingRuntime lightingRuntime() {
        return new LightingRuntime(LightingOverrides.loadOrDefaults());
    }

    @Bean
    public DisposableBean lightingRuntimeShutdownHook(LightingRuntime runtime) {
        return runtime::shutdown;
    }
}
