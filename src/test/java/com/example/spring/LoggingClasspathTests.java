package com.example.spring;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingClasspathTests {

    @Test
    void commonsLoggingJarIsNotPresentBecauseSpringJclProvidesTheBridge() throws Exception {
        List<String> logFactoryResources = Collections
                .list(Thread.currentThread()
                        .getContextClassLoader()
                        .getResources("org/apache/commons/logging/LogFactory.class"))
                .stream()
                .map(URL::toString)
                .toList();

        assertThat(logFactoryResources)
                .anyMatch(resource -> resource.contains("spring-jcl"))
                .noneMatch(resource -> resource.contains("commons-logging"));
    }
}
