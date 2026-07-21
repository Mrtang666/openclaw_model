package com.example.wechat.memory;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConversationMemory {

    private static final int MAX_HISTORY_SIZE = 30;
    private static final long VOICE_MODE_TIMEOUT_MS = 5 * 60 * 1000;

    // 用户ID -> 对话历史
    private final Map<String, List<Message>> memory = new ConcurrentHashMap<>();

    // 用户ID -> 语音模式偏好
    private final Map<String, Boolean> voiceModePreference = new ConcurrentHashMap<>();

    // 用户ID -> 语音模式最后活跃时间
    private final Map<String, Long> voiceModeLastActiveTime = new ConcurrentHashMap<>();

    // 用户ID -> 是否刚退出语音模式
    private final Map<String, Boolean> recentlyInVoiceMode = new ConcurrentHashMap<>();

    // 用户ID -> 当前使用的音色（默认Cherry）
    private final Map<String, String> userVoicePreference = new ConcurrentHashMap<>();

    // 用户ID -> 是否正在等待选择音色（只在用户主动切换音色时使用）
    private final Map<String, Boolean> waitingForVoiceSelection = new ConcurrentHashMap<>();

    // 用户ID -> 等待音色选择时的原始问题
    private final Map<String, String> pendingVoiceQuestion = new ConcurrentHashMap<>();

    // ===== 默认音色 =====
    private static final String DEFAULT_VOICE = "Cherry";

    // ===== 音色定义（带标签） =====
    public static class VoiceInfo {
        public final String name;
        public final String displayName;
        public final String description;
        public final List<String> tags;

        public VoiceInfo(String name, String displayName, String description, String... tags) {
            this.name = name;
            this.displayName = displayName;
            this.description = description;
            this.tags = Arrays.asList(tags);
        }
    }

    // 音色库
    public static final List<VoiceInfo> VOICE_LIBRARY = Arrays.asList(
            // ===== 温柔女声类 =====
            new VoiceInfo("Cherry", "芊悦", "阳光积极、亲切自然的温柔女声",
                    "温柔", "女声", "亲切", "自然", "甜美", "小姐姐", "治愈", "暖"),
            new VoiceInfo("Serena", "苏瑶", "温柔婉约、娓娓道来的知性女声",
                    "温柔", "女声", "知性", "婉约", "舒缓", "文艺", "姐姐"),
            new VoiceInfo("Maia", "四月", "知性与温柔的碰撞，兼具知性和温柔",
                    "温柔", "女声", "知性", "沉稳", "成熟", "姐姐"),
            new VoiceInfo("Mia", "乖小妹", "温顺乖巧，声音甜美",
                    "温柔", "女声", "甜美", "乖巧", "可爱", "妹妹", "治愈"),

            // ===== 阳光活力男声 =====
            new VoiceInfo("Ethan", "晨煦", "阳光、温暖、充满活力的男声",
                    "男声", "阳光", "活力", "温暖", "朝气", "哥哥", "清爽"),
            new VoiceInfo("Moon", "月白", "率性帅气、清爽干净的男声",
                    "男声", "帅气", "清爽", "干净", "开朗", "哥哥"),
            new VoiceInfo("Kai", "凯", "声音有磁性，让耳朵很舒服",
                    "男声", "磁性", "低沉", "稳重", "成熟", "大叔"),
            new VoiceInfo("Ryan", "甜茶", "节奏感强，戏感炸裂的活力男声",
                    "男声", "活力", "节奏", "戏感", "开朗", "哥哥"),

            // ===== 可爱/二次元 =====
            new VoiceInfo("Chelsie", "千雪", "二次元虚拟女友，甜美可爱的少女音",
                    "可爱", "二次元", "萌", "少女", "动漫", "女友"),
            new VoiceInfo("Momo", "茉兔", "撒娇搞怪、活泼可爱的声音",
                    "可爱", "撒娇", "搞怪", "活泼", "萌", "少女"),
            new VoiceInfo("Bella", "萌宝", "调皮可爱的小萝莉音",
                    "可爱", "萝莉", "调皮", "萌", "小朋友"),

            // ===== 御姐/成熟女声 =====
            new VoiceInfo("Vivian", "十三", "拽拽的、带点小暴躁的御姐",
                    "御姐", "女声", "成熟", "高冷", "帅气", "姐姐"),
            new VoiceInfo("Katerina", "卡捷琳娜", "御姐音色，韵律感十足",
                    "御姐", "女声", "成熟", "韵律", "磁性", "姐姐"),

            // ===== 英语/国际 =====
            new VoiceInfo("Jennifer", "詹妮弗", "电影质感的美式英语女声",
                    "英语", "美式", "电影", "女声", "国际"),
            new VoiceInfo("Aiden", "艾登", "美式英语大男孩，阳光开朗",
                    "英语", "美式", "男声", "国际", "阳光"),

            // ===== 特殊/搞怪 =====
            new VoiceInfo("Nofish", "不吃鱼", "略带口音、不会翘舌音的设计师",
                    "搞怪", "特别", "有趣", "设计师"),
            new VoiceInfo("Eldric Sage", "沧明子", "沉稳睿智的老者，有智慧感",
                    "老者", "沉稳", "睿智", "大叔", "长辈")
    );

    // ===== 音色标签关键词索引 =====
    private static final Map<String, List<VoiceInfo>> TAG_INDEX = new HashMap<>();
    static {
        for (VoiceInfo voice : VOICE_LIBRARY) {
            for (String tag : voice.tags) {
                TAG_INDEX.computeIfAbsent(tag, k -> new ArrayList<>()).add(voice);
            }
        }
    }

    // 音色显示名称映射
    private static final Map<String, String> VOICE_DISPLAY_NAME = new HashMap<>();
    static {
        for (VoiceInfo voice : VOICE_LIBRARY) {
            VOICE_DISPLAY_NAME.put(voice.name, voice.displayName);
        }
    }

    // 音色描述映射
    private static final Map<String, String> VOICE_DESCRIPTION = new HashMap<>();
    static {
        for (VoiceInfo voice : VOICE_LIBRARY) {
            VOICE_DESCRIPTION.put(voice.name, voice.description);
        }
    }

    // ===== 音色查找方法 =====

    public List<VoiceInfo> findVoicesByTags(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String lowerKeyword = keyword.toLowerCase().trim();
        Set<VoiceInfo> matched = new LinkedHashSet<>();

        for (Map.Entry<String, List<VoiceInfo>> entry : TAG_INDEX.entrySet()) {
            if (entry.getKey().contains(lowerKeyword) || lowerKeyword.contains(entry.getKey())) {
                matched.addAll(entry.getValue());
            }
        }

        for (VoiceInfo voice : VOICE_LIBRARY) {
            if (voice.displayName.contains(lowerKeyword) ||
                    voice.description.contains(lowerKeyword)) {
                matched.add(voice);
            }
        }

        return new ArrayList<>(matched);
    }

    public String getVoiceByTags(String keyword) {
        List<VoiceInfo> matched = findVoicesByTags(keyword);
        if (matched.isEmpty()) {
            return null;
        }
        return matched.get(0).name;
    }

    public List<String> getSuggestedVoices(String keyword) {
        List<VoiceInfo> matched = findVoicesByTags(keyword);
        List<String> suggestions = new ArrayList<>();
        for (int i = 0; i < Math.min(3, matched.size()); i++) {
            suggestions.add(matched.get(i).name);
        }
        return suggestions;
    }

    public String getVoiceListByCategory(String category) {
        List<VoiceInfo> voices = findVoicesByTags(category);
        if (voices.isEmpty()) {
            return "未找到相关音色，请试试其他关键词。";
        }

        StringBuilder sb = new StringBuilder("🎤 找到以下相关音色：\n\n");
        for (VoiceInfo voice : voices) {
            sb.append("• **").append(voice.displayName).append("** (")
                    .append(voice.name).append(")\n")
                    .append("  ").append(voice.description).append("\n")
                    .append("  标签: ").append(String.join("、", voice.tags)).append("\n\n");
        }
        sb.append("💡 回复「切换音色 + 名称」即可切换，例如：切换音色 芊悦");
        return sb.toString();
    }

    public String getVoiceCategoriesSummary() {
        return "🎤 **音色切换指南**\n\n" +
                "你可以通过以下关键词切换音色：\n\n" +
                "• **温柔女声** → 切换音色 温柔女声\n" +
                "• **阳光男声** → 切换音色 阳光男声\n" +
                "• **可爱/二次元** → 切换音色 可爱\n" +
                "• **御姐音** → 切换音色 御姐\n" +
                "• **英语发音** → 切换音色 英语\n" +
                "• **搞怪有趣** → 切换音色 搞怪\n" +
                "• **沉稳老者** → 切换音色 沉稳\n\n" +
                "也可以直接说：**我想要温柔女声**、**换个阳光男声**\n\n" +
                "💡 输入「查看音色 温柔」查看该分类下的所有音色";
    }

    // ===== 音色偏好管理 =====

    public String getUserVoice(String userId) {
        return userVoicePreference.getOrDefault(userId, DEFAULT_VOICE);
    }

    public boolean hasUserVoice(String userId) {
        return userVoicePreference.containsKey(userId);
    }

    public void setUserVoice(String userId, String voice) {
        if (isValidVoice(voice)) {
            userVoicePreference.put(userId, voice);
            System.out.println("🎤 用户 " + userId + " 切换音色为: " + voice);
        }
    }

    // 重置音色为默认
    public void resetUserVoice(String userId) {
        userVoicePreference.remove(userId);
        System.out.println("🔄 用户 " + userId + " 重置音色为默认: " + DEFAULT_VOICE);
    }

    public boolean isValidVoice(String voice) {
        for (VoiceInfo info : VOICE_LIBRARY) {
            if (info.name.equals(voice)) {
                return true;
            }
        }
        return false;
    }

    public String getVoiceDisplayName(String voice) {
        return VOICE_DISPLAY_NAME.getOrDefault(voice, voice);
    }

    public String getVoiceDescription(String voice) {
        return VOICE_DESCRIPTION.getOrDefault(voice, "");
    }

    // ===== 等待音色选择管理 =====

    public void setWaitingForVoiceSelection(String userId, boolean waiting, String question) {
        if (waiting) {
            waitingForVoiceSelection.put(userId, true);
            pendingVoiceQuestion.put(userId, question);
            System.out.println("⏳ 用户 " + userId + " 等待选择音色，问题: " + question);
        } else {
            waitingForVoiceSelection.remove(userId);
            pendingVoiceQuestion.remove(userId);
        }
    }

    public boolean isWaitingForVoiceSelection(String userId) {
        return waitingForVoiceSelection.getOrDefault(userId, false);
    }

    public String getPendingVoiceQuestion(String userId) {
        return pendingVoiceQuestion.get(userId);
    }

    // ===== 原有方法 =====

    public static class Message {
        private final String role;
        private final String content;
        private final long timestamp;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
            this.timestamp = System.currentTimeMillis();
        }

        public String getRole() { return role; }
        public String getContent() { return content; }
        public long getTimestamp() { return timestamp; }
    }

    public void addUserMessage(String userId, String content) {
        List<Message> history = memory.computeIfAbsent(userId, k -> Collections.synchronizedList(new ArrayList<>()));
        history.add(new Message("user", content));
        trimHistory(userId);
        System.out.println("📝 添加用户消息到记忆: " + content);
    }

    public void addAssistantMessage(String userId, String content) {
        List<Message> history = memory.get(userId);
        if (history != null) {
            history.add(new Message("assistant", content));
            trimHistory(userId);
            System.out.println("📝 添加助手消息到记忆: " + (content.length() > 50 ? content.substring(0, 50) + "..." : content));
        }
    }

    public List<Message> getHistory(String userId) {
        return memory.getOrDefault(userId, Collections.emptyList());
    }

    public List<Map<String, String>> getMessagesForModel(String userId) {
        List<Message> history = getHistory(userId);
        List<Map<String, String>> messages = new ArrayList<>();

        Map<String, String> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", getSystemPrompt(userId));
        messages.add(systemMsg);

        int start = Math.max(0, history.size() - 10);
        for (int i = start; i < history.size(); i++) {
            Message msg = history.get(i);
            Map<String, String> m = new HashMap<>();
            m.put("role", msg.getRole());
            m.put("content", msg.getContent());
            messages.add(m);
        }

        return messages;
    }

    public void clear(String userId) {
        memory.remove(userId);
        voiceModePreference.remove(userId);
        voiceModeLastActiveTime.remove(userId);
        recentlyInVoiceMode.remove(userId);
        userVoicePreference.remove(userId);
        waitingForVoiceSelection.remove(userId);
        pendingVoiceQuestion.remove(userId);
    }

    private void trimHistory(String userId) {
        List<Message> history = memory.get(userId);
        if (history != null && history.size() > MAX_HISTORY_SIZE) {
            List<Message> trimmed = new ArrayList<>(
                    history.subList(history.size() - MAX_HISTORY_SIZE, history.size())
            );
            memory.put(userId, trimmed);
        }
    }

    private String getSystemPrompt(String userId) {
        String voiceModeHint = "";
        if (isVoiceMode(userId)) {
            String currentVoice = getUserVoice(userId);
            String displayName = getVoiceDisplayName(currentVoice);
            voiceModeHint = "\n\n⚠️ 当前用户处于【语音回复模式】，所有回复内容都需要适合朗读，并且系统会自动转成语音发送。当前音色: " + displayName;
        }
        return "你是一个智能微信助手，可以帮助用户完成各种任务。你具备以下能力：\n" +
                "1. 日常聊天对话（可以闲聊、回答问题）\n" +
                "2. 查询天气信息（用户会明确说\"XX天气\"）\n" +
                "3. 图片理解/图生文（用户发送图片时自动处理）\n" +
                "4. 文生图/图片生成（用户说\"生成图片\"、\"画XX\"时调用）\n\n" +
                "请记住之前的对话内容，保持上下文连贯性。" +
                voiceModeHint;
    }



    // ========== 语音模式管理 ==========

    public void setVoiceMode(String userId, boolean enabled) {
        if (enabled) {
            voiceModePreference.put(userId, true);
            voiceModeLastActiveTime.put(userId, System.currentTimeMillis());
            recentlyInVoiceMode.remove(userId);
            waitingForVoiceSelection.remove(userId);
            pendingVoiceQuestion.remove(userId);
            System.out.println("🔊 用户 " + userId + " 进入语音回复模式");
        } else {
            voiceModePreference.remove(userId);
            voiceModeLastActiveTime.remove(userId);
            // 用户主动退出，清除超时标记，不触发超时提示
            recentlyInVoiceMode.remove(userId);
            System.out.println("🔇 用户 " + userId + " 主动退出语音回复模式");
        }
    }

    public void refreshVoiceModeActiveTime(String userId) {
        if (voiceModePreference.containsKey(userId)) {
            voiceModeLastActiveTime.put(userId, System.currentTimeMillis());
            System.out.println("🔄 刷新语音模式活跃时间");
        }
    }

    public boolean isVoiceMode(String userId) {
        Boolean isActive = voiceModePreference.get(userId);
        if (isActive == null || !isActive) {
            return false;
        }

        Long lastActive = voiceModeLastActiveTime.get(userId);
        if (lastActive != null) {
            long idleTime = System.currentTimeMillis() - lastActive;
            if (idleTime > VOICE_MODE_TIMEOUT_MS) {
                System.out.println("⏰ 用户 " + userId + " 语音模式超时 (" + (idleTime / 1000) + "秒)，自动关闭");
                voiceModePreference.remove(userId);
                voiceModeLastActiveTime.remove(userId);
                recentlyInVoiceMode.put(userId, true);
                waitingForVoiceSelection.remove(userId);
                pendingVoiceQuestion.remove(userId);
                return false;
            }
        }
        return true;
    }

    public long getVoiceModeRemainingSeconds(String userId) {
        Long lastActive = voiceModeLastActiveTime.get(userId);
        if (lastActive == null) {
            return 0;
        }
        long elapsed = System.currentTimeMillis() - lastActive;
        long remaining = (VOICE_MODE_TIMEOUT_MS - elapsed) / 1000;
        return Math.max(0, remaining);
    }

    public boolean wasRecentlyInVoiceMode(String userId) {
        return recentlyInVoiceMode.getOrDefault(userId, false);
    }

    public void clearRecentlyInVoiceMode(String userId) {
        recentlyInVoiceMode.remove(userId);
    }
}