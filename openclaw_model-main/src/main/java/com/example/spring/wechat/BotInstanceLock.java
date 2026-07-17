package com.example.spring.wechat;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BotInstanceLock implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(BotInstanceLock.class);
    private final WeChatBotProperties properties;
    private FileChannel channel;
    private FileLock lock;

    public BotInstanceLock(WeChatBotProperties properties) {
        this.properties = properties;
    }

    public synchronized boolean tryAcquire() {
        if (lock != null && lock.isValid()) {
            return true;
        }
        Path lockFile = properties.getLockFile().toAbsolutePath().normalize();
        try {
            Path parent = lockFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            channel = FileChannel.open(
                lockFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);
            lock = channel.tryLock();
            if (lock == null) {
                closeChannel();
                return false;
            }
            log.info("已获取微信机器人单实例锁：{}", lockFile);
            return true;
        } catch (OverlappingFileLockException exception) {
            closeChannel();
            return false;
        } catch (IOException exception) {
            closeChannel();
            throw new IllegalStateException("无法创建微信机器人单实例锁：" + lockFile, exception);
        }
    }

    @Override
    public synchronized void close() {
        if (lock != null) {
            try {
                lock.release();
            } catch (IOException exception) {
                log.warn("释放微信机器人单实例锁失败", exception);
            } finally {
                lock = null;
            }
        }
        closeChannel();
    }

    private void closeChannel() {
        if (channel != null) {
            try {
                channel.close();
            } catch (IOException exception) {
                log.warn("关闭微信机器人锁文件失败", exception);
            } finally {
                channel = null;
            }
        }
    }
}
