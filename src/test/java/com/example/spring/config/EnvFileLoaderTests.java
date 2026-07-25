package com.example.spring.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EnvFileLoaderTests {

    @TempDir
    Path tempDir;

    @Test
    void loadsSimpleEnvValuesIntoSystemProperties() throws Exception {
        Path envFile = tempDir.resolve(".env");
        Files.writeString(envFile, """
                # comment
                BAIDU_NETDISK_OAUTH_CLIENT_ID=124028715
                BAIDU_NETDISK_REDIRECT_URI=http://127.0.0.1:8080/api/netdisk/baidu/callback
                EMPTY_VALUE=
                """);

        EnvFileLoader.load(envFile);

        assertThat(System.getProperty("BAIDU_NETDISK_OAUTH_CLIENT_ID")).isEqualTo("124028715");
        assertThat(System.getProperty("BAIDU_NETDISK_REDIRECT_URI"))
                .isEqualTo("http://127.0.0.1:8080/api/netdisk/baidu/callback");
        assertThat(System.getProperty("EMPTY_VALUE")).isEqualTo("");
    }
}
