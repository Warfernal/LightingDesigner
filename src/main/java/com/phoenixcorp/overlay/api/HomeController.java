package com.phoenixcorp.overlay.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    // Sert le frontend React (index.html dans /static)
    @GetMapping("/")
    public String root() {
        return "forward:/index.html";
    }
}
