package com.izylife.izykube.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FallBackController {

    @GetMapping("/")
    public String redirect() {
        return "forward:/index.html";
    }

}
