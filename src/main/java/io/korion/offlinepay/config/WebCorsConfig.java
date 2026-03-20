package io.korion.offlinepay.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebCorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(
                        "capacitor://localhost",
                        "ionic://localhost",
                        "http://localhost",
                        "http://127.0.0.1",
                        "https://localhost",
                        "https://127.0.0.1",
                        "http://98.91.96.182",
                        "https://98.91.96.182",
                        "https://korion.io.kr",
                        "https://api.korion.io.kr"
                )
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
