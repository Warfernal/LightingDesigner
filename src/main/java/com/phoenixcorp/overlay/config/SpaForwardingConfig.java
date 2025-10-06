package com.phoenixcorp.overlay.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Fallback SPA : toute URL non API renvoie index.html */
@Configuration
public class SpaForwardingConfig implements WebMvcConfigurer {
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/{spring:(?!api).*}")
                .setViewName("forward:/index.html");
        registry.addViewController("/**/{spring:(?!api).*}")
                .setViewName("forward:/index.html");
    }
}
