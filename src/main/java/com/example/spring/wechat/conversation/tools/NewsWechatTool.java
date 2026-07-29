package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.news.model.NewsQuery;
import com.example.spring.wechat.news.service.NewsResult;
import com.example.spring.wechat.news.service.NewsService;
import com.example.spring.wechat.bot.WechatReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 新闻查询微信工具
 */
@Component
public class NewsWechatTool implements WechatTool {

    private static final Logger log = LoggerFactory.getLogger(NewsWechatTool.class);

    @Autowired
    private NewsService newsService;

    // ✅ 分类关键词列表
    private static final List<String> CATEGORY_KEYWORDS = List.of(
            "科技", "体育", "财经", "娱乐", "军事", "国际", "国内", "社会", "足球", "人工智能"
    );

    // ✅ 改进的正则：提取核心关键词
    private static final Pattern SEARCH_PATTERN = Pattern.compile(
            "(?:我想?|我要|帮我|给我|来|看看|看|搜索|搜|查|查找|找一下|找|有关于|关于|有关)\\s*([^，,。.！!？?\\s]{1,30})\\s*(?:新闻|消息|资讯)?"
    );

    // ✅ 纯关键词匹配（直接输入关键词的情况，如"华为"）
    private static final Pattern KEYWORD_ONLY_PATTERN = Pattern.compile(
            "^([\\u4e00-\\u9fa5a-zA-Z0-9]{2,20})$"
    );

    @Override
    public String name() {
        return "news";
    }

    @Override
    public String description() {
        return "搜索新闻，支持关键词搜索和分页展示";
    }

    @Override
    public List<String> arguments() {
        return List.of();
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(
                WechatToolParameter.optionalString("keyword", "搜索关键词", null),
                WechatToolParameter.optionalString("action", "分页操作: more/all", null)
        );
    }

    @Override
    public WechatToolCapability capability() {
        return new WechatToolCapability(
                "根据用户输入的关键词搜索新闻，支持分页展示，每次展示2条",
                List.of(
                        "不能编造不存在的新闻",
                        "关键词从用户输入中提取"
                ),
                List.of(
                        "keyword：搜索关键词（可选）",
                        "action：分页操作（more/all，可选）"
                ),
                List.of("新闻摘要列表")
        );
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        try {
            log.info("===== NewsWechatTool 开始执行 =====");
            log.info("sessionKey: {}", request.sessionKey());
            log.info("userText: {}", request.userText());

            String sessionKey = request.sessionKey();
            String userText = request.userText();
            String userId = sessionKey != null ? sessionKey : UUID.randomUUID().toString();

            // 解析用户输入
            ParsedResult parsed = parseInput(userText);
            log.info("解析结果: keyword={}, action={}", parsed.keyword, parsed.action);

            // 构建查询
            NewsQuery query = new NewsQuery(userId, sessionKey, parsed.keyword, parsed.action);

            NewsResult result = newsService.searchNews(query);
            return formatResult(result);

        } catch (Exception e) {
            log.error("新闻工具执行失败", e);
            return WechatReply.text("❌ 获取新闻失败：" + e.getMessage());
        }
    }

    /**
     * 解析用户输入
     */
    private ParsedResult parseInput(String input) {
        ParsedResult result = new ParsedResult();

        if (input == null || input.trim().isEmpty()) {
            return result;
        }

        String text = input.trim();

        // 1. 分页操作
        if (text.equals("更多") || text.equals("下一条") || text.equals("继续")) {
            result.action = "more";
            return result;
        }
        if (text.equals("全部") || text.equals("所有") || text.equals("全部新闻")) {
            result.action = "all";
            return result;
        }

        // 2. 检查是否包含分类关键词
        for (String category : CATEGORY_KEYWORDS) {
            if (text.contains(category)) {
                result.keyword = category;
                log.debug("识别为分类关键词: {}", category);
                return result;
            }
        }

        // 3. ✅ 使用正则提取关键词（支持"有关于"等中间词）
        var matcher = SEARCH_PATTERN.matcher(text);
        if (matcher.find()) {
            String keyword = matcher.group(1).trim();
            if (!keyword.isEmpty() && !keyword.equals("新闻")) {
                result.keyword = keyword;
                log.debug("正则提取关键词: {}", keyword);
                return result;
            }
        }

        // 4. ✅ 纯关键词匹配（如用户直接输入"华为"）
        var keywordMatcher = KEYWORD_ONLY_PATTERN.matcher(text);
        if (keywordMatcher.matches()) {
            String keyword = keywordMatcher.group(1);
            // 排除纯"新闻"和"更多"等操作词
            if (!keyword.equals("新闻") && !keyword.equals("更多") && !keyword.equals("全部")) {
                result.keyword = keyword;
                log.debug("纯关键词匹配: {}", keyword);
                return result;
            }
        }

        // 5. ✅ 手动提取：移除常见前缀和后缀
        String cleaned = text;

        // 移除前缀
        String[] prefixes = {"我想找有关于", "我想找关于", "我想看", "我想要", "我要找", "我想", "我要",
                "帮我找", "帮我查", "给我找", "搜索", "搜一下", "查找", "找一下", "找", "查"};
        for (String prefix : prefixes) {
            if (cleaned.startsWith(prefix)) {
                cleaned = cleaned.substring(prefix.length()).trim();
                break;
            }
        }

        // 移除后缀
        String[] suffixes = {"的新闻", "新闻", "的消息", "消息", "资讯"};
        for (String suffix : suffixes) {
            if (cleaned.endsWith(suffix)) {
                cleaned = cleaned.substring(0, cleaned.length() - suffix.length()).trim();
                break;
            }
        }

        // 如果清理后还有内容，且不是纯"新闻"
        if (!cleaned.isEmpty() && !cleaned.equals("新闻")) {
            result.keyword = cleaned;
            log.debug("手动提取关键词: {}", cleaned);
            return result;
        }

        // 6. ✅ 如果文本本身是有效的搜索词（2-20个字符）
        if (text.length() >= 2 && text.length() <= 20) {
            String keyword = text.replaceAll("新闻$", "").trim();
            if (!keyword.isEmpty() && !keyword.equals("新闻") && !keyword.equals("更多") && !keyword.equals("全部")) {
                result.keyword = keyword;
                log.debug("直接作为关键词: {}", keyword);
                return result;
            }
        }

        return result;
    }

    /**
     * 格式化结果
     */
    private WechatReply formatResult(NewsResult result) {
        if (result.isSessionExpired()) {
            return WechatReply.text("⚠️ " + result.getMessage() + "，请重新输入关键词搜索");
        }

        if (!result.isSuccess() && result.getMessage() != null) {
            return WechatReply.text("❌ " + result.getMessage());
        }

        if (result.getArticles() == null || result.getArticles().isEmpty()) {
            String msg = result.getMessage() != null ? result.getMessage() : "没有找到相关新闻";
            return WechatReply.text("📰 " + msg);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("🔍 搜索结果（共 ").append(result.getTotal()).append(" 条）\n\n");

        int startIndex = result.getDisplayedCount() - result.getArticles().size() + 1;
        for (int i = 0; i < result.getArticles().size(); i++) {
            var article = result.getArticles().get(i);
            int num = startIndex + i;

            sb.append("【").append(num).append("】").append(article.getTitle()).append("\n");
            if (article.getCategoryName() != null && !article.getCategoryName().isEmpty()) {
                sb.append("   📂 ").append(article.getCategoryName());
            }
            if (article.getSource() != null && !article.getSource().isEmpty()) {
                sb.append("  📎 ").append(article.getSource());
            }
            if (article.getPublishTime() != null && !article.getPublishTime().isEmpty()) {
                sb.append("  📅 ").append(article.getPublishTime());
            }
            sb.append("\n   🔗 ").append(article.getUrl()).append("\n\n");
        }

        if (result.isHasMore()) {
            int remaining = result.getTotal() - result.getDisplayedCount();
            sb.append("📌 还有 ").append(remaining).append(" 条，回复「更多」继续查看");
        } else {
            sb.append("✅ 已展示全部 ").append(result.getTotal()).append(" 条新闻");
        }

        return WechatReply.text(sb.toString());
    }

    private static class ParsedResult {
        private String keyword;
        private String action;

        @Override
        public String toString() {
            return "ParsedResult{keyword='" + keyword + "', action='" + action + "'}";
        }
    }
}
