package com.example.spring.wechat.bot.concurrency;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WechatMessageDispatcherTests {

    @Test
    void executesDifferentUsersConcurrently() throws Exception {
        WechatConcurrencyProperties properties = properties(2);
        try (WechatMessageDispatcher dispatcher = new WechatMessageDispatcher(properties)) {
            CountDownLatch started = new CountDownLatch(2);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch finished = new CountDownLatch(2);
            Runnable task = () -> {
                started.countDown();
                await(release);
                finished.countDown();
            };

            dispatcher.submit(new ConversationKey("connection-a", "user-a"), task);
            dispatcher.submit(new ConversationKey("connection-a", "user-b"), task);

            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            assertThat(finished.await(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void preservesOrderAndNeverOverlapsForSameUser() throws Exception {
        WechatConcurrencyProperties properties = properties(4);
        try (WechatMessageDispatcher dispatcher = new WechatMessageDispatcher(properties)) {
            List<Integer> order = new CopyOnWriteArrayList<>();
            AtomicInteger concurrent = new AtomicInteger();
            AtomicInteger maxConcurrent = new AtomicInteger();
            CountDownLatch finished = new CountDownLatch(3);

            for (int index = 1; index <= 3; index++) {
                int value = index;
                dispatcher.submit(new ConversationKey("connection-a", "user-a"), () -> {
                    int active = concurrent.incrementAndGet();
                    maxConcurrent.accumulateAndGet(active, Math::max);
                    order.add(value);
                    concurrent.decrementAndGet();
                    finished.countDown();
                });
            }

            assertThat(finished.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(order).containsExactly(1, 2, 3);
            assertThat(maxConcurrent).hasValue(1);
        }
    }

    @Test
    void closeDropsQueuedWorkWithoutGoingNegativeOrRescheduling() throws Exception {
        WechatConcurrencyProperties properties = properties(2);
        AtomicBoolean secondRan = new AtomicBoolean();
        try (WechatMessageDispatcher dispatcher = new WechatMessageDispatcher(properties)) {
            CountDownLatch firstStarted = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);

            dispatcher.submit(new ConversationKey("connection-a", "user-a"), () -> {
                firstStarted.countDown();
                await(releaseFirst);
            });
            dispatcher.submit(new ConversationKey("connection-a", "user-a"), () -> secondRan.set(true));

            assertThat(firstStarted.await(2, TimeUnit.SECONDS)).isTrue();
            dispatcher.close();
            dispatcher.close();
            releaseFirst.countDown();
            Thread.sleep(200);

            assertThat(secondRan).isFalse();
            assertThat(dispatcher.queuedTasks()).isZero();
            assertThat(dispatcher.mailboxCount()).isZero();
            assertThat(dispatcher.activeTasks()).isZero();
            assertThatThrownBy(() -> dispatcher.submit(new ConversationKey("connection-a", "user-a"), () -> {
            }))
                    .isInstanceOf(RejectedExecutionException.class);
        }
    }

    @Test
    void compositeSessionKeySeparatesSameWechatUserAcrossConnections() {
        assertThat(new ConversationKey("connection-a", "same-user").sessionKey())
                .isNotEqualTo(new ConversationKey("connection-b", "same-user").sessionKey());
    }

    private WechatConcurrencyProperties properties(int workers) {
        WechatConcurrencyProperties properties = new WechatConcurrencyProperties();
        properties.setWorkerThreads(workers);
        properties.setUserQueueCapacity(5);
        properties.setGlobalQueueCapacity(20);
        properties.setTaskTimeout(Duration.ofSeconds(5));
        return properties;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
