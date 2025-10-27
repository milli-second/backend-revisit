package screentimeai.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * JWT 검증 및 UUID 추출 필터
 *
 * 기능:
 * 1. Authorization 헤더에서 JWT 토큰 추출
 * 2. JWT 검증 (서명, 만료 시간)
 * 3. UUID 추출하여 X-User-Id 헤더에 추가
 * 4. 다운스트림 서비스로 전달
 */
@Component
@Slf4j
public class JwtAuthenticationFilter extends AbstractGatewayFilterFactory<JwtAuthenticationFilter.Config> {

    @Value("${jwt.secret}")
    private String jwtSecret;

    public JwtAuthenticationFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            // 1. Authorization 헤더 확인
            if (!request.getHeaders().containsKey("Authorization")) {
                log.warn("Missing Authorization header");
                return onError(exchange, "Missing Authorization header", HttpStatus.UNAUTHORIZED);
            }

            String authHeader = request.getHeaders().getFirst("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("Invalid Authorization header format");
                return onError(exchange, "Invalid Authorization header", HttpStatus.UNAUTHORIZED);
            }

            // 2. JWT 토큰 추출
            String token = authHeader.substring(7);

            try {
                // 3. JWT 검증 및 Claims 추출
                SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
                Claims claims = Jwts.parser()
                        .verifyWith(key)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                // 4. subjectId 추출 (JWT의 "subjectId" claim, 하위 호환성을 위해 "uuid"도 확인)
                String subjectId = claims.get("subjectId", String.class);
                if (subjectId == null || subjectId.isEmpty()) {
                    subjectId = claims.get("uuid", String.class); // 하위 호환성
                }

                if (subjectId == null || subjectId.isEmpty()) {
                    log.warn("Missing subjectId/uuid in JWT");
                    return onError(exchange, "Invalid token: missing subjectId", HttpStatus.UNAUTHORIZED);
                }

                log.debug("JWT validated successfully. UUID: {}", subjectId);

                // 5. X-User-Id 헤더 추가
                ServerHttpRequest modifiedRequest = request.mutate()
                        .header("X-User-Id", subjectId)
                        .build();

                ServerWebExchange modifiedExchange = exchange.mutate()
                        .request(modifiedRequest)
                        .build();

                return chain.filter(modifiedExchange);

            } catch (Exception e) {
                log.error("JWT validation failed", e);
                return onError(exchange, "Invalid or expired token", HttpStatus.UNAUTHORIZED);
            }
        };
    }

    private Mono<Void> onError(ServerWebExchange exchange, String message, HttpStatus status) {
        exchange.getResponse().setStatusCode(status);
        log.error("Authentication failed: {}", message);
        return exchange.getResponse().setComplete();
    }

    public static class Config {
        // 필터 설정이 필요하면 여기에 추가
    }
}
