package screentimeai.config.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import screentimeai.config.IpRateLimitFilter;

@Configuration
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final IpRateLimitFilter ipRateLimitFilter; //

    public SecurityConfig(JwtUtil jwtUtil, IpRateLimitFilter ipRateLimitFilter) {

        this.jwtUtil = jwtUtil;
        this.ipRateLimitFilter = ipRateLimitFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); // BCrypt 권장
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            @Qualifier("authRedisTemplate") RedisTemplate<String, String> authRedisTemplate
    ) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable) // JWT 기반이라 CSRF 비활성화
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST,"/users/signup", "/users/login").permitAll() // 회원가입/로그인만 허용
                        .requestMatchers("/users/swagger-ui.html", "/users/swagger-ui/**", "/users/v3/api-docs", "/users/v3/api-docs/**").permitAll() // Swagger 허용
                        .anyRequest().authenticated() // 나머지는 인증 필요
                )
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS) // 세션 필요없음
                )
                .addFilterBefore(ipRateLimitFilter, UsernamePasswordAuthenticationFilter.class) //가장 먼저 실행해서 요청을 제한
                .addFilterBefore(new JwtAuthenticationFilter(jwtUtil,authRedisTemplate),// ratelitit을 통과한 요청에 대해 JWT 인증 처리
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
