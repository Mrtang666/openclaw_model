package com.example.spring.memory;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "memory")
public class MemoryProperties {
    private boolean enabled = true;
    private Path dataDirectory = Path.of("runtime-data");
    private int maxEntriesPerUser = 40;
    private int promptEntries = 12;
    private int maxImagesPerUser = 12;
    private long maxImageBytesPerUser = 50L * 1024 * 1024;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Path getDataDirectory() {
        return dataDirectory;
    }

    public void setDataDirectory(Path dataDirectory) {
        this.dataDirectory = dataDirectory;
    }

    public int getMaxEntriesPerUser() {
        return maxEntriesPerUser;
    }

    public void setMaxEntriesPerUser(int maxEntriesPerUser) {
        this.maxEntriesPerUser = maxEntriesPerUser;
    }

    public int getPromptEntries() {
        return promptEntries;
    }

    public void setPromptEntries(int promptEntries) {
        this.promptEntries = promptEntries;
    }

    public int getMaxImagesPerUser() {
        return maxImagesPerUser;
    }

    public void setMaxImagesPerUser(int maxImagesPerUser) {
        this.maxImagesPerUser = maxImagesPerUser;
    }

    public long getMaxImageBytesPerUser() {
        return maxImageBytesPerUser;
    }

    public void setMaxImageBytesPerUser(long maxImageBytesPerUser) {
        this.maxImageBytesPerUser = maxImageBytesPerUser;
    }
}
