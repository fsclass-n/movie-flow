package com.onrender.movieflow.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UserController {

    @GetMapping("/user/signup")
    public String signup() {
        return "user/signup";
    }

    @GetMapping("/user/login")
    public String login() {
        return "user/login";
    }
}
