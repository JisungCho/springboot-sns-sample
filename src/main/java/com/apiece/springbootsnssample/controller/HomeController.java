package com.apiece.springbootsnssample.controller;

import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @GetMapping("/")
    public String home(Principal principal) {
        return "안녕하세요, " + principal.getName() + "님!";
    }
}