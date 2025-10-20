package screentimeai.config.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.function.Function;
import java.security.Key;

@Component
public class JwtUtil {

    //private final String SECRET_KEY = "mySecretKey"; //  환경변수나 yml로 빼는 게 보안상 좋음
    private final Key SECRET_KEY;

    public JwtUtil(@Value("${jwt.secret}") String secret) {
        this.SECRET_KEY = Keys.hmacShaKeyFor(secret.getBytes());
        System.out.println("✅ Loaded JWT Secret: " + secret);
    }
    // 토큰 생성
    public String generateToken(String nickname, String uuid) {
        return Jwts.builder()
                .setSubject(nickname) // subject = username
                .claim("uuid", uuid)
                .setIssuedAt(new Date(System.currentTimeMillis())) // 발급 시간
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 10)) // 10시간 유효
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

    // 토큰이 만료됐는지 확인
    public Boolean isTokenExpired(String token) {
        return getExpirationDateFromToken(token).before(new Date());
    }

    // 유효성 검증
    public Boolean validateToken(String token, String username) {
        final String extractedUsername = getUsernameFromToken(token);
        return (extractedUsername.equals(username) && !isTokenExpired(token));
    }

    // ================= 내부 유틸 메서드 =================
    private Date getExpirationDateFromToken(String token) {
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
