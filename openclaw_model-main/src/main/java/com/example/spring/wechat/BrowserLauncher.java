package com.example.spring.wechat;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;

final class BrowserLauncher {
    private BrowserLauncher() {
    }

    static void open(String url) throws IOException {
        URI uri = validatedHttpUri(url);
        if (Desktop.isDesktopSupported()
            && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            try {
                Desktop.getDesktop().browse(uri);
                return;
            } catch (IOException | UnsupportedOperationException ignored) {
                // Use the operating-system launcher below.
            }
        }

        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            new ProcessBuilder(
                "rundll32",
                "url.dll,FileProtocolHandler",
                uri.toASCIIString()).start();
            return;
        }
        if (osName.contains("mac")) {
            new ProcessBuilder("open", uri.toASCIIString()).start();
            return;
        }
        new ProcessBuilder("xdg-open", uri.toASCIIString()).start();
    }

    static URI validatedHttpUri(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Login URL must not be blank");
        }
        URI uri = URI.create(url.trim());
        String scheme = uri.getScheme();
        if ((!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme))
            || uri.getHost() == null) {
            throw new IllegalArgumentException("Login URL must use HTTP or HTTPS");
        }
        return uri;
    }
}
