package com.nilsson.backend.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Controller responsible for orchestrating Single Page Application (SPA) routing and resource forwarding.
 * <p>
 * In a modern web architecture where the frontend (e.g., Vue.js, React) manages its own client-side routing,
 * this controller ensures that deep-linked URLs or browser refreshes on non-root paths are correctly
 * handled by the server. Without this mechanism, the server would attempt to find a physical resource
 * at the requested path and return a 404 error.
 * <p>
 * This controller intercepts all requests that do not contain a period (which typically denotes a static
 * file extension like .js, .css, or .png) and are not explicitly handled by other API controllers.
 * It then forwards these requests to {@code index.html}, allowing the client-side router to take over
 * and render the appropriate view based on the URL path.
 */
@Controller
public class SpaController {

    @RequestMapping(value = "/{path:[^\\.]*}")
    public String redirect() {
        return "forward:/index.html";
    }
}
