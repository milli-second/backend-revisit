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
public class
SwaggerConfig {

    @Value("${springdoc.swagger-ui.server-url:}")
    private String swaggerServerUrl;

    @Bean
    public OpenAPI userServiceAPI() {
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
                        .title("User Service API")
                        .description("사용자 인증 및 관리 API\n\n" +
                                "## 인증 방법\n" +
                                "1. `/users/login` 또는 `/users/signup` 엔드포인트를 통해 JWT 토큰을 발급받습니다.\n" +
                                "2. 우측 상단의 'Authorize' 버튼을 클릭합니다.\n" +
                                "3. 발급받은 토큰을 입력합니다. (Bearer 접두사 제외)\n" +
                                "4. 인증이 필요한 API를 테스트할 수 있습니다. \n" +
                                "주의: user password를 바꾼 이후에는 재로그인이 필요합니다.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Screen Time AI Team")
                                .email("crispylemon7120@gmail.com")))
                .servers(servers)
                .addSecurityItem(securityRequirement)
                .components(components);
    }
}
