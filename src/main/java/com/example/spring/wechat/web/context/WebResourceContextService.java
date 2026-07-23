package com.example.spring.wechat.web.context;

import com.example.spring.wechat.web.model.WebPageContent;
import com.example.spring.wechat.web.model.WebSearchResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 微信端网页资源上下文服务。
 *
 * <p>用于记录最近几次搜索结果和阅读网页，让用户可以继续说“第二个网页”“刚才那个链接”。
 * 它只保存轻量引用信息，不保存网页全文，避免把大量网页内容长期塞进会话上下文。</p>
 */
@Service
public class WebResourceContextService {

    private static final int MAX_SEARCHES = 3;
    private static final int MAX_RESULTS_PER_SEARCH = 5;
    private static final int MAX_READS = 5;
    private static final Pattern ORDINAL_PATTERN = Pattern.compile("第\\s*([一二三四五五六七八九十12345678910]+)\\s*(个|条)?");

    private final ConcurrentMap<String, UserWebResources> resources = new ConcurrentHashMap<>();

    public void recordSearch(String sessionKey, String query, List<WebSearchResult> results) {
        if (results == null || results.isEmpty()) {
            return;
        }
        List<WebSearchResult> safeResults = results.stream()
                .filter(result -> result != null && result.url() != null && !result.url().isBlank())
                .limit(MAX_RESULTS_PER_SEARCH)
                .toList();
        if (safeResults.isEmpty()) {
            return;
        }
        userResources(sessionKey).recordSearch(new WebSearchSnapshot(query, safeResults, Instant.now()));
    }

    public void recordRead(String sessionKey, WebPageContent page) {
        if (page == null || page.url() == null || page.url().isBlank()) {
            return;
        }
        userResources(sessionKey).recordRead(new WebReadSnapshot(
                page.title(),
                page.url(),
                summarize(page.content(), 180),
                Instant.now()));
    }

    public List<WebSearchSnapshot> recentSearches(String sessionKey) {
        return userResources(sessionKey).recentSearches();
    }

    public List<WebReadSnapshot> recentReads(String sessionKey) {
        return userResources(sessionKey).recentReads();
    }

    public Optional<String> resolveUrl(String sessionKey, String userText) {
        String text = userText == null ? "" : userText.strip();
        if (text.isBlank()) {
            return Optional.empty();
        }
        if (mentionsPreviousRead(text)) {
            return recentReads(sessionKey).stream()
                    .findFirst()
                    .map(WebReadSnapshot::url);
        }
        Optional<Integer> ordinal = ordinal(text);
        if (ordinal.isPresent()) {
            int index = ordinal.get() - 1;
            List<WebSearchResult> latestResults = recentSearches(sessionKey).stream()
                    .findFirst()
                    .map(WebSearchSnapshot::results)
                    .orElse(List.of());
            if (index >= 0 && index < latestResults.size()) {
                return Optional.ofNullable(latestResults.get(index).url())
                        .filter(value -> !value.isBlank());
            }
        }
        return Optional.empty();
    }

    public String contextText(String sessionKey) {
        StringBuilder text = new StringBuilder();
        List<WebSearchSnapshot> searches = recentSearches(sessionKey);
        if (!searches.isEmpty()) {
            text.append("最近搜索：");
            for (int searchIndex = 0; searchIndex < searches.size(); searchIndex++) {
                WebSearchSnapshot search = searches.get(searchIndex);
                text.append("\n搜索").append(searchIndex + 1).append("：").append(search.query());
                for (int index = 0; index < search.results().size(); index++) {
                    WebSearchResult result = search.results().get(index);
                    text.append("\n  ").append(index + 1).append(". ")
                            .append(result.title()).append(" - ").append(result.url());
                }
            }
        }
        List<WebReadSnapshot> reads = recentReads(sessionKey);
        if (!reads.isEmpty()) {
            if (!text.isEmpty()) {
                text.append("\n\n");
            }
            text.append("最近阅读：");
            for (int index = 0; index < reads.size(); index++) {
                WebReadSnapshot read = reads.get(index);
                text.append("\n").append(index + 1).append(". ")
                        .append(read.title()).append(" - ").append(read.url());
                if (!read.summary().isBlank()) {
                    text.append("\n   摘要：").append(read.summary());
                }
            }
        }
        return text.toString().strip();
    }

    private UserWebResources userResources(String sessionKey) {
        return resources.computeIfAbsent(safeSession(sessionKey), key -> new UserWebResources());
    }

    private Optional<Integer> ordinal(String text) {
        Matcher matcher = ORDINAL_PATTERN.matcher(text);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return parseOrdinal(matcher.group(1));
    }

    private Optional<Integer> parseOrdinal(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String text = value.strip();
        try {
            return Optional.of(Integer.parseInt(text));
        } catch (NumberFormatException ignored) {
            return switch (text) {
                case "一" -> Optional.of(1);
                case "二" -> Optional.of(2);
                case "三" -> Optional.of(3);
                case "四" -> Optional.of(4);
                case "五" -> Optional.of(5);
                case "六" -> Optional.of(6);
                case "七" -> Optional.of(7);
                case "八" -> Optional.of(8);
                case "九" -> Optional.of(9);
                case "十" -> Optional.of(10);
                default -> Optional.empty();
            };
        }
    }

    private boolean mentionsPreviousRead(String text) {
        return text.contains("刚才那个")
                || text.contains("刚刚那个")
                || text.contains("上一个链接")
                || text.contains("上个链接")
                || text.contains("刚才的网页")
                || text.contains("刚刚看的");
    }

    private String summarize(String value, int maxLength) {
        String text = value == null ? "" : value.replaceAll("\\s+", " ").strip();
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    private String safeSession(String sessionKey) {
        return sessionKey == null || sessionKey.isBlank() ? "default" : sessionKey.strip();
    }

    private static final class UserWebResources {

        private final Deque<WebSearchSnapshot> searches = new ArrayDeque<>();
        private final Deque<WebReadSnapshot> reads = new ArrayDeque<>();

        synchronized void recordSearch(WebSearchSnapshot snapshot) {
            searches.addFirst(snapshot);
            while (searches.size() > MAX_SEARCHES) {
                searches.removeLast();
            }
        }

        synchronized void recordRead(WebReadSnapshot snapshot) {
            reads.addFirst(snapshot);
            while (reads.size() > MAX_READS) {
                reads.removeLast();
            }
        }

        synchronized List<WebSearchSnapshot> recentSearches() {
            return List.copyOf(new ArrayList<>(searches));
        }

        synchronized List<WebReadSnapshot> recentReads() {
            return List.copyOf(new ArrayList<>(reads));
        }
    }
}
