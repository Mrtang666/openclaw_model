package com.example.spring.wechat.bot.concurrency;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Executes different conversations concurrently while preserving order inside one conversation. */
public final class WechatMessageDispatcher implements AutoCloseable {

    private final int userQueueCapacity;
    private final int globalQueueCapacity;
    private final ThreadPoolExecutor workers;
    private final Map<ConversationKey, Mailbox> mailboxes = new ConcurrentHashMap<>();
    private final AtomicInteger queuedTasks = new AtomicInteger();
    private final AtomicInteger activeTasks = new AtomicInteger();
    private final AtomicBoolean closed = new AtomicBoolean();

    public WechatMessageDispatcher(WechatConcurrencyProperties properties) {
        int threads = properties.getWorkerThreads();
        this.userQueueCapacity = properties.getUserQueueCapacity();
        this.globalQueueCapacity = properties.getGlobalQueueCapacity();
        AtomicInteger threadNumber = new AtomicInteger();
        this.workers = new ThreadPoolExecutor(
                threads,
                threads,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(globalQueueCapacity),
                task -> {
                    Thread thread = new Thread(task, "wechat-message-worker-" + threadNumber.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    public void submit(ConversationKey key, Runnable task) {
        if (closed.get() || workers.isShutdown()) {
            throw new RejectedExecutionException("微信消息处理器已停止");
        }
        Mailbox mailbox = mailboxes.computeIfAbsent(key, ignored -> new Mailbox());
        boolean schedule;
        synchronized (mailbox) {
            if (mailbox.queue.size() >= userQueueCapacity) {
                throw new RejectedExecutionException("当前用户消息队列已满");
            }
            if (queuedTasks.get() >= globalQueueCapacity) {
                throw new RejectedExecutionException("全局消息队列已满");
            }
            mailbox.queue.add(task);
            queuedTasks.incrementAndGet();
            schedule = mailbox.running.compareAndSet(false, true);
        }
        if (schedule) {
            scheduleNext(key, mailbox);
        }
    }

    private void scheduleNext(ConversationKey key, Mailbox mailbox) {
        Runnable next;
        synchronized (mailbox) {
            if (closed.get()) {
                int dropped = mailbox.queue.size();
                mailbox.queue.clear();
                mailbox.running.set(false);
                mailboxes.remove(key, mailbox);
                subtractQueued(dropped);
                return;
            }
            next = mailbox.queue.poll();
            if (next == null) {
                mailbox.running.set(false);
                mailboxes.remove(key, mailbox);
                return;
            }
            queuedTasks.decrementAndGet();
        }
        try {
            workers.execute(() -> {
                activeTasks.incrementAndGet();
                try {
                    next.run();
                } finally {
                    activeTasks.decrementAndGet();
                    scheduleNext(key, mailbox);
                }
            });
        } catch (RejectedExecutionException exception) {
            synchronized (mailbox) {
                mailbox.running.set(false);
                if (closed.get()) {
                    int dropped = mailbox.queue.size();
                    mailbox.queue.clear();
                    mailboxes.remove(key, mailbox);
                    subtractQueued(dropped);
                    return;
                }
            }
            mailboxes.remove(key, mailbox);
            throw exception;
        }
    }

    public int queuedTasks() { return queuedTasks.get(); }
    public int activeTasks() { return activeTasks.get(); }
    public int workerThreads() { return workers.getCorePoolSize(); }
    public int mailboxCount() { return mailboxes.size(); }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        workers.shutdownNow();
        for (Map.Entry<ConversationKey, Mailbox> entry : mailboxes.entrySet()) {
            ConversationKey key = entry.getKey();
            Mailbox mailbox = entry.getValue();
            if (mailbox == null) {
                continue;
            }
            int dropped;
            synchronized (mailbox) {
                dropped = mailbox.queue.size();
                mailbox.queue.clear();
                mailbox.running.set(false);
            }
            subtractQueued(dropped);
            mailboxes.remove(key, mailbox);
        }
    }

    private void subtractQueued(int count) {
        queuedTasks.updateAndGet(current -> Math.max(0, current - Math.max(0, count)));
    }

    private static final class Mailbox {
        private final Queue<Runnable> queue = new ArrayDeque<>();
        private final AtomicBoolean running = new AtomicBoolean();
    }
}
