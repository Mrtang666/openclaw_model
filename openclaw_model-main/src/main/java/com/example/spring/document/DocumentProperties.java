package com.example.spring.document;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "document")
public class DocumentProperties {
    private boolean enabled = true;
    private Path dataDirectory = Path.of("runtime-data");
    private long maxUploadBytes = 20L * 1024 * 1024;
    private int maxExtractedCharacters = 200_000;
    private int chunkCharacters = 12_000;
    private int maxFilesPerUser = 20;
    private long maxBytesPerUser = 100L * 1024 * 1024;
    private Duration retention = Duration.ofDays(7);
    private Path pdfFontPath;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Path getDataDirectory() { return dataDirectory; }
    public void setDataDirectory(Path dataDirectory) { this.dataDirectory = dataDirectory; }
    public long getMaxUploadBytes() { return maxUploadBytes; }
    public void setMaxUploadBytes(long maxUploadBytes) { this.maxUploadBytes = maxUploadBytes; }
    public int getMaxExtractedCharacters() { return maxExtractedCharacters; }
    public void setMaxExtractedCharacters(int value) { this.maxExtractedCharacters = value; }
    public int getChunkCharacters() { return chunkCharacters; }
    public void setChunkCharacters(int chunkCharacters) { this.chunkCharacters = chunkCharacters; }
    public int getMaxFilesPerUser() { return maxFilesPerUser; }
    public void setMaxFilesPerUser(int maxFilesPerUser) { this.maxFilesPerUser = maxFilesPerUser; }
    public long getMaxBytesPerUser() { return maxBytesPerUser; }
    public void setMaxBytesPerUser(long maxBytesPerUser) { this.maxBytesPerUser = maxBytesPerUser; }
    public Duration getRetention() { return retention; }
    public void setRetention(Duration retention) { this.retention = retention; }
    public Path getPdfFontPath() { return pdfFontPath; }
    public void setPdfFontPath(Path pdfFontPath) { this.pdfFontPath = pdfFontPath; }
}
