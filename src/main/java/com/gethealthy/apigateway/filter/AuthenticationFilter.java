package com.gethealthy.apigateway.filter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Custom filter for handling authentication in the API Gateway.
 * This filter checks for the presence and validity of an Authorization header
 * and forwards requests to the authentication service to validate the token.
 */
@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    @Autowired
    private WebClient.Builder webClientBuilder;

    /**
     * Constructor for AuthenticationFilter.
     */
    public AuthenticationFilter() {
        super(Config.class);
    }

    /**
     * Applies the authentication filter logic.
     *
     * @param config The configuration for the filter (currently not used).
     * @return A GatewayFilter that processes the request and performs authentication.
     */
    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            // Extract the request path
            String requestPath = exchange.getRequest().getURI().getPath();

            // Skip authentication for /login and /signup endpoints
            if (requestPath.equals("/authentication-service/api/v1/auth/login") || requestPath.equals("/authentication-service/api/v1/auth/signup")) {
                return chain.filter(exchange);  // Proceed without authentication
            }

            // Check if the Authorization header is present
            if (!exchange.getRequest().getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                return this.onError(exchange, "No Authorization header");
            }

            // Safely get the Authorization header and check if it's null or not a valid Bearer token
            String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return this.onError(exchange, "Invalid Authorization header");
            }

            // Make an API call to authenticate the user
            return webClientBuilder.build()
                    .post()
                    .uri("http://localhost:8082/api/v1/auth/authenticate-user") // Consider externalizing the URL
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .retrieve()
                    .bodyToMono(String.class)  // Change to String to match "truetrue" and "falsefalse" (This is because the authenticate-user endpoint processes the request twice. still to figure out why)
                    .flatMap(response -> {
                        System.out.println("Received response from authenticate-user endpoint: " + response);

                        if ("truetrue".equals(response)) {
                            // Continue with the request if authenticated
                            return chain.filter(exchange);
                        } else {
                            // Return error if not authenticated
                            return this.onError(exchange, "Unauthorized");
                        }
                    })
                    .then();
        };
    }




    /**
     * Handles errors by setting the response status to Unauthorized.
     *
     * @param exchange The current server exchange.
     * @param err      The error message to be returned in the response.
     * @return A Mono indicating the completion of the error handling.
     */
    private Mono<Void> onError(ServerWebExchange exchange, String err) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return response.setComplete();
    }

    /**
     * Configuration class for the AuthenticationFilter.
     * This class can be used to define configuration properties if needed.
     */
    public static class Config {
        // Configuration properties if needed
    }
}
