package screentimeai.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI usageServiceAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Usage Service API")
                        .description("사용자 스크린 타임 사용 데이터 관리 API\n\n" +
                                "## 주요 기능\n" +
                                "- **Raw Data 수집**: 앱 사용 원본 데이터 저장 및 실시간 집계\n" +
                                "- **통계 조회**: 사용 패턴, Top 3 앱, 카테고리별 비율 등\n" +
                                "- **시계열 분석**: 7일 패턴, 3일 시간대별 분석\n\n" +
                                "## API 사용법\n" +
                                "1. POST `/usage/raw-data`: 사용 데이터를 전송합니다\n" +
                                "2. GET `/usage/stats/*`: 다양한 통계를 조회합니다\n" +
                                "3. 모든 UUID는 사용자의 subjectId를 의미합니다\n\n" +
                                "## 인증\n" +
                                "이 서비스는 Gateway/Istio 레벨에서 JWT 인증을 수행합니다.\n" +
                                "직접 호출 시 인증 헤더가 필요하지 않습니다.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Screen Time AI Team")
                                .email("crispylemon7120@gmail.com")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8088")
                                .description("Gateway Server")
                ));
    }
}
