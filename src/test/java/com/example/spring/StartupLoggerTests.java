package com.example.spring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class StartupLoggerTests {

    @Test
    void logsSuccessMessageWhenRun(CapturedOutput output) {
        new StartupLogger().run();

        assertThat(output).contains("Spring application started successfully");
    }
}
