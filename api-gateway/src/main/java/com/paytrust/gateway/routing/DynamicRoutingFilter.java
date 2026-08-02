package com.paytrust.gateway.routing;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Enumeration;

@Component
public class DynamicRoutingFilter extends OncePerRequestFilter {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${routes.user-service}")
    private String userServiceUrl;

    @Value("${routes.payment-service}")
    private String paymentServiceUrl;

    @Value("${routes.ledger-service}")
    private String ledgerServiceUrl;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String requestURI = request.getRequestURI();
        String targetBaseUrl = null;

        if (requestURI.startsWith("/api/v1/users") || requestURI.startsWith("/api/v1/auth")) {
            targetBaseUrl = userServiceUrl;
        } else if (requestURI.startsWith("/api/v1/payments")) {
            targetBaseUrl = paymentServiceUrl;
        } else if (requestURI.startsWith("/api/v1/ledger")) {
            // Note: our own gateway controller also listens on /api/v1/ledger/transfer
            // We'll proxy anything else or let local handle it if we want.
            // For now, if we have a local controller, we skip routing to avoid loop if it's the same port,
            // but the properties point to 8080 (Ledger Service) while gateway is 8082.
            targetBaseUrl = ledgerServiceUrl;
        }

        if (targetBaseUrl == null) {
            // No route matched, let the local controllers handle it (like actuator or local GatewayController)
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // Build the target URL
            String queryString = request.getQueryString();
            String targetUrl = targetBaseUrl + requestURI + (queryString != null ? "?" + queryString : "");
            URI uri = new URI(targetUrl);

            // Copy headers
            HttpHeaders headers = new HttpHeaders();
            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                if (!headerName.equalsIgnoreCase("host")) {
                    headers.addAll(headerName, Collections.list(request.getHeaders(headerName)));
                }
            }

            // Read body
            byte[] body = StreamUtils.copyToByteArray(request.getInputStream());
            HttpEntity<byte[]> httpEntity = new HttpEntity<>(body, headers);

            // Proxy the request
            ResponseEntity<byte[]> responseEntity = restTemplate.exchange(uri, HttpMethod.valueOf(request.getMethod()), httpEntity, byte[].class);

            // Copy response headers and status
            response.setStatus(responseEntity.getStatusCode().value());
            if (responseEntity.getHeaders() != null) {
                responseEntity.getHeaders().forEach((name, values) -> {
                    values.forEach(value -> response.addHeader(name, value));
                });
            }

            // Write response body
            if (responseEntity.getBody() != null) {
                response.getOutputStream().write(responseEntity.getBody());
            }

        } catch (URISyntaxException e) {
            logger.error("URI Syntax error for routing: ", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Routing error");
        } catch (Exception e) {
            logger.error("Proxy error: ", e);
            // Ignore connection refused for now if downstream isn't running yet
            response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Bad Gateway: Downstream service unreachable");
        }
    }
}
