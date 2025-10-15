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
                        .description("API for managing user screen time usage data")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Screen Time AI Team")
                                .email("support@screentimeai.com")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8082")
                                .description("Local Development Server"),
                        new Server()
                                .url("http://localhost:8088")
                                .description("Gateway Server")
                ));
    }
}
