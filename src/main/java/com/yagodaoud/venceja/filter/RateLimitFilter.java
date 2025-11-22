package com.yagodaoud.venceja.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Filtro para rate limiting global usando Bucket4j
 */
@Provider
@Singleton
public class RateLimitFilter implements ContainerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    @ConfigProperty(name = "rate-limit.requests-per-minute", defaultValue = "100")
    int requestsPerMinute;

    private Bucket bucket;

    @PostConstruct
    public void init() {
        Bandwidth limit = Bandwidth.classic(
                requestsPerMinute,
                Refill.intervally(requestsPerMinute, Duration.ofMinutes(1)));
        this.bucket = Bucket.builder()
                .addLimit(limit)
                .build();
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String path = requestContext.getUriInfo().getPath();
        
        // Ignorar endpoints públicos ou estáticos se necessário
        if (path.contains("/auth/login") || path.contains("/h2-console") || path.contains("/actuator")) {
            return;
        }

        if (bucket.tryConsume(1)) {
            // Request allowed
        } else {
            log.warn("Rate limit exceeded for path: {}", path);
            requestContext.abortWith(Response.status(Response.Status.TOO_MANY_REQUESTS)
                    .entity("Muitas requisições. Tente novamente mais tarde.")
                    .build());
        }
    }
}
