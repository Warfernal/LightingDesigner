package com.phoenixcorp.overlay.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    // /, /index, /home → index.html du dossier /static
    @GetMapping(value = { "/", "/index", "/home" })
    public String root() {
        return "forward:/index.html";
    }

    // Toute route sans extension (ex: /settings, /preview) → index.html (React Router)
    @GetMapping("/{path:[^\\.]*}")
    public String anySpaPath() {
        return "forward:/index.html";
    }
}
