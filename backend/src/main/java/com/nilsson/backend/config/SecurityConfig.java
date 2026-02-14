package com.nilsson.backend.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.UUID;

/**
 * Security configuration that implements a handshake token validation mechanism to protect the local API.
 * <p>
 * This configuration ensures that only the authorized Electron frontend can communicate with the
 * Spring Boot backend. It generates a unique, single-use UUID token upon application startup
 * and enforces its presence in all incoming API requests. This prevents Cross-Site Request
 * Forgery (CSRF) and unauthorized access from other local applications or browsers.
 * <p>
 * Key Responsibilities:
 * <ul>
 *   <li><b>Token Generation:</b> Creates a secure, random {@code HANDSHAKE_TOKEN} at runtime.</li>
 *   <li><b>Interceptor Registration:</b> Configures a {@link HandshakeInterceptor} to intercept
 *   all requests starting with {@code /api/}.</li>
 *   <li><b>Multi-Channel Validation:</b> Supports token validation via both standard
 *   {@code Authorization} headers (for API calls) and query parameters (for media resources
 *   loaded via {@code <img>} tags).</li>
 *   <li><b>Access Control:</b> Automatically allows requests for static frontend assets while
 *   strictly enforcing security for all data-driven endpoints.</li>
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
            String path = request.getRequestURI();

            if (!path.startsWith("/api/")) {
                return true;
            }

            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.equals("Bearer " + HANDSHAKE_TOKEN)) {
                return true;
            }

            if (path.equals("/api/images/content") ||
                    path.equals("/api/images/thumbnail") ||
                    path.startsWith("/api/scrub/preview/")) {

                String tokenParam = request.getParameter("token");
                if (tokenParam != null && tokenParam.equals(HANDSHAKE_TOKEN)) {
                    return true;
                }
            }

            log.warn("Unauthorized access attempt blocked from IP: {} for path: {}", request.getRemoteAddr(), path);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Unauthorized: Invalid Handshake Token");
            return false;
        }
    }
}
