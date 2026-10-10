package com.meper.chatbi.web.auth;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebMvcConfigTest {

    @Test
    void registersTrimmedConfiguredOrigins() {
        AuthInterceptor interceptor = mock(AuthInterceptor.class);
        CorsRegistry registry = mock(CorsRegistry.class);
        CorsRegistration registration = mock(CorsRegistration.class);
        when(registry.addMapping("/api/**")).thenReturn(registration);
        when(registration.allowedOrigins("https://chatbi.meper.cn", "https://admin.meper.cn"))
                .thenReturn(registration);
        when(registration.allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS"))
                .thenReturn(registration);
        when(registration.allowedHeaders("*")).thenReturn(registration);

        WebMvcConfig config = new WebMvcConfig(
                interceptor,
                " https://chatbi.meper.cn, https://admin.meper.cn ");

        config.addCorsMappings(registry);

        verify(registration).allowedOrigins("https://chatbi.meper.cn", "https://admin.meper.cn");
        verify(registration).maxAge(3600);
    }

    @Test
    void rejectsWildcardOrigin() {
        AuthInterceptor interceptor = mock(AuthInterceptor.class);

        assertThrows(IllegalArgumentException.class,
                () -> new WebMvcConfig(interceptor, "https://chatbi.meper.cn,*"));
    }
}
