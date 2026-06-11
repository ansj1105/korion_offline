package io.korion.offlinepay.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

class WebCorsConfigTest {

    @Test
    void apiCorsAllowsCredentialedRequestsFromKorionWeb() throws Exception {
        CorsRegistry registry = new CorsRegistry();

        new WebCorsConfig().addCorsMappings(registry);

        CorsConfiguration configuration = getCorsConfiguration(registry, "/api/**");
        assertNotNull(configuration);
        assertEquals(Boolean.TRUE, configuration.getAllowCredentials());
        assertTrue(configuration.getAllowedOrigins().contains("https://korion.io.kr"));
        assertTrue(configuration.getAllowedMethods().contains("GET"));
        assertTrue(configuration.getAllowedHeaders().contains("*"));
    }

    @SuppressWarnings("unchecked")
    private static CorsConfiguration getCorsConfiguration(CorsRegistry registry, String pathPattern) throws Exception {
        Method method = CorsRegistry.class.getDeclaredMethod("getCorsConfigurations");
        method.setAccessible(true);
        Map<String, CorsConfiguration> configurations = (Map<String, CorsConfiguration>) method.invoke(registry);
        return configurations.get(pathPattern);
    }
}
