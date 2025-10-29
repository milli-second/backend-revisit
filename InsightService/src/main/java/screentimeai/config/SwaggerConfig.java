package screentimeai.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
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
        // JWT 인증 스키마 정의
        String jwtSchemeName = "JWT Authentication";
        SecurityRequirement securityRequirement = new SecurityRequirement().addList(jwtSchemeName);

        Components components = new Components()
                .addSecuritySchemes(jwtSchemeName, new SecurityScheme()
                        .name(jwtSchemeName)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT 토큰을 입력하세요. 'Bearer ' 접두사는 자동으로 추가됩니다."));

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
                                "## 인증 방법\n" +
                                "1. UserService의 `/users/login` 엔드포인트를 통해 JWT 토큰을 발급받습니다.\n" +
                                "2. 우측 상단의 'Authorize' 버튼을 클릭합니다.\n" +
                                "3. 발급받은 토큰을 입력합니다. (Bearer 접두사 제외)\n" +
                                "4. Gateway를 통해 API를 테스트할 수 있습니다.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Screen Time AI Team")
                                .email("crispylemon7120@gmail.com")))
                .servers(servers)
                .addSecurityItem(securityRequirement)
                .components(components);
    }
}
