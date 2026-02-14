package com.nilsson.backend.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.UUID;

/**
 * Security configuration for the application, implementing a handshake-based authentication mechanism.
 * <p>
 * This class configures a {@link HandlerInterceptor} to protect API endpoints from unauthorized access.
 * It generates a unique, session-based handshake token that must be provided by the frontend in either
 * the {@code Authorization} header or as a {@code token} query parameter.
 * <p>
 * Key Security Features:
 * <ul>
 *   <li><b>Handshake Token:</b> A cryptographically strong UUID generated at startup, ensuring only
 *   the local frontend instance can communicate with the backend.</li>
 *   <li><b>CORS Preflight Handling:</b> Automatically permits {@code OPTIONS} requests to prevent
 *   browser-level blocking of cross-origin requests.</li>
 *   <li><b>Endpoint Exclusion:</b> Allows unauthenticated access to static assets and critical
 *   system commands (e.g., shutdown) that may be triggered by the host environment.</li>
 *   <li><b>Dual-Channel Auth:</b> Supports both Bearer tokens (for standard API calls) and query
 *   parameters (for media elements like {@code <img>} tags).</li>
 * </ul>
 */
@Configuration
public class SecurityConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    private static final String HANDSHAKE_TOKEN = UUID.randomUUID().toString();

    public static String getHandshakeToken() {
        return HANDSHAKE_TOKEN;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandshakeInterceptor());
    }

    private static class HandshakeInterceptor implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
            if (HttpMethod.OPTIONS.name().equalsIgnoreCase(request.getMethod())) {
                return true;
            }

            String path = request.getRequestURI();

            if (!path.startsWith("/api/") || path.equals("/api/system/shutdown")) {
                return true;
            }

            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.equals("Bearer " + HANDSHAKE_TOKEN)) {
                return true;
            }

            String tokenParam = request.getParameter("token");
            if (tokenParam != null && tokenParam.equals(HANDSHAKE_TOKEN)) {
                return true;
            }

            log.warn("Unauthorized API access blocked from IP: {} for path: {}", request.getRemoteAddr(), path);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Unauthorized: Invalid Handshake Token");
            return false;
        }
    }
}
