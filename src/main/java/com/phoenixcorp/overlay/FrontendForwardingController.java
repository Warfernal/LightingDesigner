package com.phoenixcorp.overlay;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class FrontendForwardingController {

    @GetMapping({"/", "/{path:^(?!api$)(?!.*\\.).*$}", "/**/{path:^(?!.*\\.).*$}"})
    public String forward(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/api")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return "forward:/index.html";
    }
}
