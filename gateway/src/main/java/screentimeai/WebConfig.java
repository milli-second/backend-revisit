package screentimeai;

import org.springframework.context.annotation.Configuration;

//TODO: 프론트 URL 나오면 추가하기
//@Configuration
//public class WebConfig implements WebMvcConfigurer {
//
//    @Override
//    public void addCorsMappings(CorsRegistry registry) {
//
//        registry.addMapping("/**") // 모든 API 경로에 대해 CORS 허용
//                .allowedOrigins("프론트 주소") // Vercel 앱의 정확한 도메인 지정
//                // .allowedOrigins("*") // 모든 Origin 허용 (보안상 권장되지 않음, 개발 단계에서만 사용)
//                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // 허용할 HTTP 메서드
//                .allowedHeaders("*") // 모든 헤더 허용
//                .allowCredentials(true) // (필요하다면) 인증 정보 (쿠키, HTTP 인증 헤더 등) 전송 허용
//                .maxAge(3600); // Preflight 요청 캐시 시간 (초)
//    }
//}
