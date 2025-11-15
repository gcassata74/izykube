package com.izylife.izykube.web;

import jakarta.annotation.security.PermitAll;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FallBackController {

    @PermitAll
    @GetMapping("/")
    public String redirect() {
        return "forward:/index.html";
    }

}
