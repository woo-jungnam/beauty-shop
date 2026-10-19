package com.core.beautyshop.shared.config;
import com.core.beautyshop.shared.security.StaffPermissionInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration @RequiredArgsConstructor
public class StaffPermissionWebConfig implements WebMvcConfigurer {
    private final StaffPermissionInterceptor permissions;
    @Override public void addInterceptors(InterceptorRegistry registry){registry.addInterceptor(permissions).addPathPatterns("/api/v1/**");}
}
