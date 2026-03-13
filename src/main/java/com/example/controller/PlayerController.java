package com.example.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class PlayerController {

    @RequestMapping("/player")
    public String getPlayerByID(@PathVariable Long id) {
        return "test"+id;
    }
}
