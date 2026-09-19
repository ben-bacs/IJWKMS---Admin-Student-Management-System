package edu.wvsu.ijwkms.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI ijwkmsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("IJWKMS Next API")
                        .description("Academic Operations and Student Success Platform")
                        .version("v1")
                        .contact(new Contact().name("IJWKMS Maintainers")));
    }
}
