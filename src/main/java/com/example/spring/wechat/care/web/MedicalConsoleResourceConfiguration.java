package com.example.spring.wechat.care.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class MedicalConsoleResourceConfiguration implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String devConsoleLocation = Path.of("frontend", "medical-console")
                .toAbsolutePath()
                .normalize()
                .toUri()
                .toString();
        registry.addResourceHandler("/medical-console/**")
                .addResourceLocations(devConsoleLocation, "classpath:/static/medical-console/")
                .setCachePeriod(0);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/medical-console", "/medical-console/");
        registry.addRedirectViewController("/medical-console/", "/medical-console/index.html");
    }
}
