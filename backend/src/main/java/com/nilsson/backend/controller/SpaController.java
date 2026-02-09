package com.nilsson.backend.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Controller responsible for handling Single Page Application (SPA) routing.
 * Forwards non-API and non-static resource requests to index.html to support client-side routing.
 */
@Controller
public class SpaController {

    @RequestMapping(value = "/{path:[^\\.]*}")
    public String redirect() {
        return "forward:/index.html";
    }
}
