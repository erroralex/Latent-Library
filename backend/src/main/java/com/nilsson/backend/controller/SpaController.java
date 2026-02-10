package com.nilsson.backend.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Controller responsible for orchestrating Single Page Application (SPA) routing.
 * <p>
 * In a modern web application where the frontend (Vue.js) handles its own routing, this controller
 * ensures that deep-linked URLs or browser refreshes on non-root paths are correctly handled by the
 * server. It intercepts all requests that do not contain a period (indicating they are not static
 * files like .js or .css) and are not handled by specific API controllers, forwarding them to
 * {@code index.html}. This allows the client-side router to take over and render the correct view.
 */
@Controller
public class SpaController {

    @RequestMapping(value = "/{path:[^\\.]*}")
    public String redirect() {
        return "forward:/index.html";
    }
}
