package com.example.spring.wechat.travel.client;

import com.example.spring.wechat.travel.config.MeituanTravelProperties;
import com.example.spring.wechat.travel.model.TravelQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MeituanTravelCliClientTests {

    @TempDir
    Path tempDir;

    @Test
    void buildsArgumentListWithoutShellConcatenation() {
        MeituanTravelCliClient client = client(true, "secret", "node", "C:/tools/ht-ai/dist/index.js");
        String untrustedQuery = "上海酒店 & whoami | echo hacked";

        List<String> command = client.command(new TravelQuery(untrustedQuery, "帮我找酒店", "上海"));

        assertThat(command.get(0)).endsWithIgnoringCase("node.exe");
        assertThat(command.subList(1, command.size())).containsExactly(
                "C:/tools/ht-ai/dist/index.js",
                "query",
                "--query",
                untrustedQuery,
                "--origin-query",
                "帮我找酒店",
                "--channel",
                "meituan-developer",
                "--city",
                "上海",
                "--output",
                "text");
    }

    @Test
    void sanitizesTerminalCodesAndUnsafeLinksButKeepsOfficialLinks() {
        String output = "\u001B[32m酒店结果\u001B[0m\n"
                + "[立即查看](https://hotel.meituan.com/example)\n"
                + "[危险链接](javascript:alert(1))";

        assertThat(MeituanTravelCliClient.sanitize(output))
                .contains("酒店结果", "[立即查看](https://hotel.meituan.com/example)")
                .contains("危险链接（链接已移除）")
                .doesNotContain("\u001B", "javascript:");
    }

    @Test
    void rejectsDisabledOrMissingTokenBeforeStartingProcess() {
        assertThatThrownBy(() -> client(false, "secret", "ht-ai", "")
                .query(new TravelQuery("北京酒店", "北京酒店", "北京")))
                .isInstanceOfSatisfying(MeituanTravelClientException.class,
                        error -> assertThat(error.kind()).isEqualTo(MeituanTravelClientException.Kind.CONFIGURATION));

        assertThatThrownBy(() -> client(true, "", "ht-ai", "")
                .query(new TravelQuery("北京酒店", "北京酒店", "北京")))
                .isInstanceOfSatisfying(MeituanTravelClientException.class,
                        error -> assertThat(error.kind()).isEqualTo(MeituanTravelClientException.Kind.CONFIGURATION));
    }

    @Test
    void resolvesWindowsNpmShimToOfficialNodeScript() throws Exception {
        Path shim = tempDir.resolve("ht-ai.cmd");
        Path script = tempDir.resolve("node_modules")
                .resolve("@meituan-travel")
                .resolve("ht-ai")
                .resolve("dist")
                .resolve("index.js");
        Files.createDirectories(script.getParent());
        Files.writeString(shim, "@echo off");
        Files.writeString(script, "#!/usr/bin/env node");

        assertThat(MeituanTravelCliClient.installedPackageScript(shim))
                .isEqualTo(script.toAbsolutePath().normalize());
        assertThat(MeituanTravelCliClient.windowsNpmLauncher(shim, "C:/node/node.exe"))
                .containsExactly("C:/node/node.exe", script.toAbsolutePath().normalize().toString());
    }

    @Test
    void classifiesOfficialCliTimeoutAndAuthenticationErrors() {
        assertThat(MeituanTravelCliClient.classifyFailure(
                1,
                "{\"code\":\"E_API_ERROR\",\"message\":\"请求超时，请稍后重试\"}"))
                .isEqualTo(MeituanTravelClientException.Kind.TIMEOUT);
        assertThat(MeituanTravelCliClient.classifyFailure(
                3,
                "{\"code\":\"E_AUTH\",\"message\":\"鉴权失败\"}"))
                .isEqualTo(MeituanTravelClientException.Kind.AUTHENTICATION);
    }

    private MeituanTravelCliClient client(boolean enabled, String token, String executable, String script) {
        return new MeituanTravelCliClient(new MeituanTravelProperties(
                enabled, token, executable, script, "meituan-developer", 1_000, 4096));
    }
}
