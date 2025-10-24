package screentimeai.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class SwaggerConfig {

    @Value("${springdoc.swagger-ui.server-url:}")
    private String swaggerServerUrl;

    @Bean
    public OpenAPI insightServiceAPI() {
        // 서버 URL 설정 - 환경 변수로 설정 가능하도록 함
        List<Server> servers = new ArrayList<>();
        if (swaggerServerUrl != null && !swaggerServerUrl.isEmpty()) {
            servers.add(new Server().url(swaggerServerUrl).description("API Gateway"));
        } else {
            // 기본값: 상대 경로 사용 (현재 브라우저에서 접속한 호스트 기준)
            servers.add(new Server().url("/").description("Current Host"));
        }

        return new OpenAPI()
                .info(new Info()
                        .title("Insight Service API")
                        .description("사용자 행동 패턴 분석 및 인사이트 제공 API\n\n" +
                                "## 주요 기능\n" +
                                "- **사용자 분류**: 행동 패턴 + 콘텐츠 선호 기반 자동 분류\n" +
                                "- **요일별 패턴 분석**: 7일간 사용 패턴 추이 제공\n" +
                                "- **카테고리별 인사이트**: 주요 사용 카테고리 및 비율 분석\n\n" +
                                "## 분류 방식\n" +
                                "1. **행동 패턴**: 픽업 횟수 + 평균 세션 시간 기반\n" +
                                "   - COMPULSIVE_CHECKER: 강박적 확인 패턴\n" +
                                "   - NORMAL_BEHAVIOR: 일반적 사용 패턴\n\n" +
                                "2. **콘텐츠 선호**: 카테고리별 비율 + 절대 시간 기반\n" +
                                "   - GAME_FOCUSED: 게임 중심\n" +
                                "   - SOCIAL_FOCUSED: SNS/소통 중심\n" +
                                "   - LEARNING_FOCUSED: 학습 중심\n" +
                                "   - ENTERTAINMENT_FOCUSED: 엔터테인먼트 중심\n" +
                                "   - BALANCED: 균형잡힌 사용\n\n" +
                                "## API 사용법\n" +
                                "1. GET `/insights/daily-patterns/{userId}`: 요일별 패턴 조회 (어제부터 7일치)\n\n" +
                                "## 인증\n" +
                                "이 서비스는 Gateway/Istio 레벨에서 JWT 인증을 수행합니다.\n" +
                                "직접 호출 시 인증 헤더가 필요하지 않습니다.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Screen Time AI Team")
                                .email("crispylemon7120@gmail.com")))
                .servers(servers);
    }
}
