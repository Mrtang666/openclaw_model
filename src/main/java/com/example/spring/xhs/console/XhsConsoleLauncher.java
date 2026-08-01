package com.example.spring.xhs.console;

import org.springframework.stereotype.Service;

import java.awt.Desktop;
import java.net.URI;

@Service
public class XhsConsoleLauncher {

    public boolean open(String url) {
        try {
            URI uri = URI.create(url);
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri);
                return true;
            }
            if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", uri.toASCIIString()).start();
                return true;
            }
        } catch (Exception ignored) {
            // The CLI still prints the URL so the console remains accessible.
        }
        return false;
    }
}
