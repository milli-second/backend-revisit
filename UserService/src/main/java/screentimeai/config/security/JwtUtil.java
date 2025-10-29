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
    public TokenInfo generateTokens(String nickname, String subjectId) {
        String accessToken = generateAccessToken(nickname, subjectId);
        String refreshToken = generateRefreshToken(nickname, subjectId);

        return TokenInfo.builder()
                .grantType("Bearer")
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    // Access Token 생성
    public String generateAccessToken(String nickname, String subjectId) {
        return createToken(nickname, subjectId, ACCESS_TOKEN_VALIDITY_MS, true); // Access Token에만 jti 추가
    }

    // Refresh Token 생성
    public String generateRefreshToken(String nickname, String subjectId) {
        return createToken(nickname, subjectId, REFRESH_TOKEN_VALIDITY_MS, false); // Refresh Token에는 jti 추가 안함
    }

    private String createToken(String nickname, String subjectId, long validityMs, boolean includeJti) {
        return Jwts.builder()
                .setSubject(nickname)
                .claim("subjectId", subjectId)
                .claim("uuid", subjectId) // 하위 호환성을 위해 uuid도 포함
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

    // 토큰에서 subjectId 추출 (하위 호환성을 위해 uuid도 확인)
    public String getUuidFromToken(String token) {
        String subjectId = getClaimFromToken(token, claims -> claims.get("subjectId", String.class));
        if (subjectId == null) {
            subjectId = getClaimFromToken(token, claims -> claims.get("uuid", String.class));
        }
        return subjectId;
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
