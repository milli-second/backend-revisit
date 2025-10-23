package screentimeai.config.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import screentimeai.presentation.dto.TokenInfo;

import java.security.Key;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

@Component
public class JwtUtil {

    private final Key SECRET_KEY;
    private static final long ACCESS_TOKEN_VALIDITY_MS = 30 * 60 * 1000; // 30분
    private static final long REFRESH_TOKEN_VALIDITY_MS = 7 * 24 * 60 * 60 * 1000; // 7일

    public JwtUtil(@Value("${jwt.secret}") String secret) {
        this.SECRET_KEY = Keys.hmacShaKeyFor(secret.getBytes());
        System.out.println("✅ Loaded JWT Secret: " + secret);
    }

    // Access Token과 Refresh Token을 모두 생성
    public TokenInfo generateTokens(String nickname, String uuid) {
        String accessToken = generateAccessToken(nickname, uuid);
        String refreshToken = generateRefreshToken(nickname, uuid);

        return TokenInfo.builder()
                .grantType("Bearer")
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    // Access Token 생성
    public String generateAccessToken(String nickname, String uuid) {
        return createToken(nickname, uuid, ACCESS_TOKEN_VALIDITY_MS, true); // Access Token에만 jti 추가
    }

    // Refresh Token 생성
    public String generateRefreshToken(String nickname, String uuid) {
        return createToken(nickname, uuid, REFRESH_TOKEN_VALIDITY_MS, false); // Refresh Token에는 jti 추가 안함
    }

    private String createToken(String nickname, String uuid, long validityMs, boolean includeJti) {
        return Jwts.builder()
                .setSubject(nickname)
                .claim("uuid", uuid)
                .claim("jti", includeJti ? UUID.randomUUID().toString() : null) // jti 클레임 추가
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + validityMs))
                .signWith(SECRET_KEY)
                .compact();
    }

    // 토큰에서 username 추출
    public String getUsernameFromToken(String token) {
        return getClaimFromToken(token, Claims::getSubject);
    }

    // 토큰에서 uuid 추출
    public String getUuidFromToken(String token) {
        return getClaimFromToken(token, claims -> claims.get("uuid", String.class));
    }

    // 토큰에서 jti 추출
    public String getJtiFromToken(String token) {
        return getClaimFromToken(token, claims -> claims.get("jti", String.class));
    }

    // 토큰이 만료됐는지 확인
    public Boolean isTokenExpired(String token) {
        try {
            return getExpirationDateFromToken(token).before(new Date());
        } catch (Exception e) {
            // 만료되거나 서명이 잘못된 토큰 등 파싱 실패 시 true 반환
            return true;
        }
    }

    // 유효성 검증
    public Boolean validateToken(String token, String username) {
        final String extractedUsername = getUsernameFromToken(token);
        return (extractedUsername.equals(username) && !isTokenExpired(token));
    }

    // ================= 내부 유틸 메서드 =================
    public Date getExpirationDateFromToken(String token) {
        return getClaimFromToken(token, Claims::getExpiration);
    }

    private <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }

    private Claims getAllClaimsFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(SECRET_KEY)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
