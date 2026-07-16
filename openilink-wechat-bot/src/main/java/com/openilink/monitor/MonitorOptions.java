package com.openilink.monitor;

import java.util.function.Consumer;

public class MonitorOptions {
    private Consumer<String> onBufUpdate;
    private Consumer<Exception> onError;
    private Runnable onSessionExpired;
    private String initialBuf;

    public MonitorOptions() {}

    public Consumer<String> getOnBufUpdate() { return onBufUpdate; }
    public void setOnBufUpdate(Consumer<String> onBufUpdate) { this.onBufUpdate = onBufUpdate; }
    public Consumer<Exception> getOnError() { return onError; }
    public void setOnError(Consumer<Exception> onError) { this.onError = onError; }
    public Runnable getOnSessionExpired() { return onSessionExpired; }
    public void setOnSessionExpired(Runnable onSessionExpired) { this.onSessionExpired = onSessionExpired; }
    public String getInitialBuf() { return initialBuf; }
    public void setInitialBuf(String initialBuf) { this.initialBuf = initialBuf; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Consumer<String> onBufUpdate;
        private Consumer<Exception> onError;
        private Runnable onSessionExpired;
        private String initialBuf;

        public Builder onBufUpdate(Consumer<String> onBufUpdate) { this.onBufUpdate = onBufUpdate; return this; }
        public Builder onError(Consumer<Exception> onError) { this.onError = onError; return this; }
        public Builder onSessionExpired(Runnable onSessionExpired) { this.onSessionExpired = onSessionExpired; return this; }
        public Builder initialBuf(String initialBuf) { this.initialBuf = initialBuf; return this; }

        public MonitorOptions build() {
            MonitorOptions opts = new MonitorOptions();
            opts.setOnBufUpdate(onBufUpdate);
            opts.setOnError(onError);
            opts.setOnSessionExpired(onSessionExpired);
            opts.setInitialBuf(initialBuf);
            return opts;
        }
    }
}
