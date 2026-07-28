package com.example.spring.wechat.travel.client;

import com.example.spring.wechat.travel.config.MeituanTravelProperties;
import com.example.spring.wechat.travel.model.MeituanTravelResult;
import com.example.spring.wechat.travel.model.TravelQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Component
public class MeituanTravelCliClient implements MeituanTravelClient {

    private static final Logger log = LoggerFactory.getLogger(MeituanTravelCliClient.class);
    private static final Pattern ANSI_ESCAPE = Pattern.compile("\\u001B(?:\\[[0-?]*[ -/]*[@-~]|\\][^\\u0007]*(?:\\u0007|\\u001B\\\\))");
    private static final Pattern UNSAFE_MARKDOWN_LINK = Pattern.compile(
            "(?i)\\[([^]\\r\\n]+)]\\(\\s*(?:javascript|data|file):[^)\\r\\n]*\\)");
    private static final Pattern UNSAFE_CONTROL = Pattern.compile("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]");

    private final MeituanTravelProperties properties;

    public MeituanTravelCliClient(MeituanTravelProperties properties) {
        this.properties = properties;
    }

    @Override
    public MeituanTravelResult query(TravelQuery travelQuery) {
        validate(travelQuery);
        Process process;
        try {
            ProcessBuilder builder = new ProcessBuilder(command(travelQuery));
            builder.environment().put("MEITUAN_HT_TOKEN", properties.token());
            builder.environment().put("MEITUAN_RAW_JSON", "0");
            process = builder.start();
        } catch (IOException exception) {
            log.warn("无法启动美团酒旅 CLI，executable={}, error={}",
                    properties.executable(), exception.getMessage());
            throw new MeituanTravelClientException(
                    MeituanTravelClientException.Kind.EXECUTION,
                    "无法启动美团酒旅 CLI",
                    exception);
        }

        ExecutorService readers = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "meituan-travel-cli-output");
            thread.setDaemon(true);
            return thread;
        });
        Future<StreamOutput> stdout = readers.submit(() -> read(process.getInputStream()));
        Future<StreamOutput> stderr = readers.submit(() -> read(process.getErrorStream()));
        try {
            if (!process.waitFor(properties.timeoutMs(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                waitAfterDestroy(process);
                throw new MeituanTravelClientException(
                        MeituanTravelClientException.Kind.TIMEOUT,
                        "美团酒旅查询超时");
            }
            StreamOutput out = get(stdout);
            StreamOutput error = get(stderr);
            if (out.overflow() || error.overflow()) {
                throw new MeituanTravelClientException(
                        MeituanTravelClientException.Kind.OUTPUT_LIMIT,
                        "美团酒旅返回内容超过大小限制");
            }
            return handleExit(process.exitValue(), out.text(), error.text());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new MeituanTravelClientException(
                    MeituanTravelClientException.Kind.EXECUTION,
                    "美团酒旅查询被中断",
                    exception);
        } finally {
            readers.shutdownNow();
        }
    }

    List<String> command(TravelQuery travelQuery) {
        List<String> command = new ArrayList<>(launcherCommand());
        command.add("query");
        command.add("--query");
        command.add(travelQuery.query());
        command.add("--origin-query");
        command.add(travelQuery.originQuery());
        command.add("--channel");
        command.add(properties.channel());
        if (!travelQuery.city().isBlank()) {
            command.add("--city");
            command.add(travelQuery.city());
        }
        command.add("--output");
        command.add("text");
        return List.copyOf(command);
    }

    private List<String> launcherCommand() {
        if (!properties.cliScript().isBlank()) {
            return List.of(resolveNodeExecutable(properties.executable()), properties.cliScript());
        }
        if (!isWindows()) {
            return List.of(properties.executable());
        }

        String executable = properties.executable();
        String lower = executable.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".exe")) {
            return List.of(executable);
        }
        if (lower.endsWith(".js")) {
            return List.of(resolveNodeExecutable("node"), executable);
        }

        Path shim = findWindowsNpmShim(executable)
                .orElseThrow(() -> new MeituanTravelClientException(
                        MeituanTravelClientException.Kind.CONFIGURATION,
                        "未找到美团酒旅 CLI，请安装 @meituan-travel/ht-ai"));
        return windowsNpmLauncher(shim, resolveNodeExecutable("node"));
    }

    static List<String> windowsNpmLauncher(Path shim, String nodeExecutable) {
        Path script = installedPackageScript(shim);
        if (!Files.isRegularFile(script)) {
            throw new MeituanTravelClientException(
                    MeituanTravelClientException.Kind.CONFIGURATION,
                    "美团酒旅 CLI 安装不完整，请重新安装 @meituan-travel/ht-ai");
        }
        return List.of(nodeExecutable, script.toAbsolutePath().normalize().toString());
    }

    static Path installedPackageScript(Path shim) {
        Path directory = shim.toAbsolutePath().normalize().getParent();
        if (directory == null) {
            return Path.of("node_modules", "@meituan-travel", "ht-ai", "dist", "index.js");
        }
        return directory.resolve("node_modules")
                .resolve("@meituan-travel")
                .resolve("ht-ai")
                .resolve("dist")
                .resolve("index.js");
    }

    private Optional<Path> findWindowsNpmShim(String executable) {
        Path configured = Path.of(executable);
        if (configured.getParent() != null) {
            if (Files.isRegularFile(configured)) {
                return Optional.of(configured);
            }
            return Optional.empty();
        }

        String baseName = executable.replaceFirst("(?i)\\.(?:cmd|ps1)$", "");
        for (Path directory : executableSearchDirectories()) {
            for (String extension : List.of(".cmd", ".ps1")) {
                Path candidate = directory.resolve(baseName + extension);
                if (Files.isRegularFile(candidate)) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }

    private String resolveNodeExecutable(String executable) {
        if (!isWindows()) {
            return executable;
        }
        String lower = executable.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".exe") || Path.of(executable).getParent() != null) {
            return executable;
        }
        return findOnSearchPath(executable + ".exe")
                .map(path -> path.toAbsolutePath().normalize().toString())
                .orElse(executable);
    }

    private Optional<Path> findOnSearchPath(String fileName) {
        return executableSearchDirectories().stream()
                .map(directory -> directory.resolve(fileName))
                .filter(Files::isRegularFile)
                .findFirst();
    }

    private List<Path> executableSearchDirectories() {
        Set<Path> directories = new LinkedHashSet<>();
        String path = System.getenv("PATH");
        if (path != null && !path.isBlank()) {
            for (String item : path.split(Pattern.quote(java.io.File.pathSeparator))) {
                String directory = item.strip();
                if (directory.length() >= 2 && directory.startsWith("\"") && directory.endsWith("\"")) {
                    directory = directory.substring(1, directory.length() - 1);
                }
                if (!directory.isBlank()) {
                    try {
                        directories.add(Path.of(directory));
                    } catch (RuntimeException ignored) {
                        // Ignore malformed PATH entries and continue with the remaining locations.
                    }
                }
            }
        }
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) {
            directories.add(Path.of(appData, "npm"));
        }
        return List.copyOf(directories);
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private void validate(TravelQuery travelQuery) {
        if (!properties.enabled()) {
            throw new MeituanTravelClientException(
                    MeituanTravelClientException.Kind.CONFIGURATION,
                    "美团酒旅服务未启用");
        }
        if (properties.token().isBlank()) {
            throw new MeituanTravelClientException(
                    MeituanTravelClientException.Kind.CONFIGURATION,
                    "未配置 MEITUAN_HT_TOKEN");
        }
        if (travelQuery == null || travelQuery.query().isBlank() || travelQuery.originQuery().isBlank()) {
            throw new MeituanTravelClientException(
                    MeituanTravelClientException.Kind.CONFIGURATION,
                    "缺少旅行查询内容");
        }
    }

    private MeituanTravelResult handleExit(int exitCode, String stdout, String stderr) {
        if (exitCode != 0) {
            MeituanTravelClientException.Kind kind = classifyFailure(exitCode, stdout + "\n" + stderr);
            log.warn("美团酒旅 CLI 执行失败，exitCode={}, category={}", exitCode, kind);
            throw new MeituanTravelClientException(
                    kind,
                    failureMessage(kind));
        }
        String content = sanitize(stdout);
        if (content.isBlank()) {
            throw new MeituanTravelClientException(
                    MeituanTravelClientException.Kind.NO_RESULT,
                    "美团酒旅未返回结果");
        }
        return new MeituanTravelResult(content);
    }

    static MeituanTravelClientException.Kind classifyFailure(int exitCode, String output) {
        String text = output == null ? "" : output.toLowerCase(Locale.ROOT);
        if (exitCode == 3
                || text.contains("e_auth")
                || text.contains("鉴权失败")
                || text.contains("token无效")
                || text.contains("unauthorized")
                || text.contains("authorization failed")) {
            return MeituanTravelClientException.Kind.AUTHENTICATION;
        }
        if (text.contains("请求超时")
                || text.contains("e_timeout")
                || text.contains("etimedout")
                || text.contains("econnaborted")) {
            return MeituanTravelClientException.Kind.TIMEOUT;
        }
        if (text.contains("暂无结果")
                || text.contains("e_no_result")
                || text.contains("未返回结果")) {
            return MeituanTravelClientException.Kind.NO_RESULT;
        }
        if (exitCode == 4 || text.contains("e_channel") || text.contains("渠道签名验证失败")) {
            return MeituanTravelClientException.Kind.CONFIGURATION;
        }
        return MeituanTravelClientException.Kind.EXECUTION;
    }

    private String failureMessage(MeituanTravelClientException.Kind kind) {
        return switch (kind) {
            case CONFIGURATION -> "美团酒旅 CLI 配置无效";
            case AUTHENTICATION -> "美团酒旅服务鉴权失败";
            case TIMEOUT -> "美团酒旅查询超时";
            case NO_RESULT -> "美团酒旅未返回结果";
            case OUTPUT_LIMIT -> "美团酒旅返回内容超过大小限制";
            case EXECUTION -> "美团酒旅查询失败";
        };
    }

    private StreamOutput read(InputStream input) throws IOException {
        int limit = properties.maxOutputBytes();
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(limit, 8192));
        byte[] buffer = new byte[8192];
        boolean overflow = false;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            int remaining = limit - output.size();
            if (remaining > 0) {
                output.write(buffer, 0, Math.min(remaining, read));
            }
            overflow |= read > remaining;
        }
        return new StreamOutput(output.toString(StandardCharsets.UTF_8), overflow);
    }

    private StreamOutput get(Future<StreamOutput> future) {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MeituanTravelClientException(
                    MeituanTravelClientException.Kind.EXECUTION,
                    "读取美团酒旅结果被中断",
                    exception);
        } catch (ExecutionException exception) {
            throw new MeituanTravelClientException(
                    MeituanTravelClientException.Kind.EXECUTION,
                    "读取美团酒旅结果失败",
                    exception.getCause());
        }
    }

    private void waitAfterDestroy(Process process) {
        try {
            process.waitFor(2, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        normalized = ANSI_ESCAPE.matcher(normalized).replaceAll("");
        normalized = UNSAFE_CONTROL.matcher(normalized).replaceAll("");
        normalized = UNSAFE_MARKDOWN_LINK.matcher(normalized).replaceAll("$1（链接已移除）");
        return normalized.strip();
    }

    private record StreamOutput(String text, boolean overflow) {
    }
}
