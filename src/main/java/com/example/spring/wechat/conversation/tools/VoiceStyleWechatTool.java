package com.example.spring.wechat.conversation.tools;

import com.example.spring.chat.ChatService;
import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.wechat.voice.style.model.VoiceCandidatePage;
import com.example.spring.wechat.voice.style.model.VoiceProfile;
import com.example.spring.wechat.voice.style.service.VoicePreferenceService;
import com.example.spring.wechat.voice.synthesis.model.VoiceSynthesisSegment;
import com.example.spring.wechat.voice.synthesis.service.VoiceSynthesisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 微信音色修改工具。
 * 负责按用户描述筛选 qwen3-tts-flash 官方音色、生成试听语音、确认保存用户音色偏好。
 */
@Component
public class VoiceStyleWechatTool implements WechatTool {

    private static final Logger log = LoggerFactory.getLogger(VoiceStyleWechatTool.class);
    private static final String DEFAULT_PREVIEW_TEXT = "你好，我是你的 AI 助手，很高兴用这个声音陪你聊天。";

    private final ChatService chatService;
    private final VoiceSynthesisService voiceSynthesisService;
    private final VoicePreferenceService voicePreferenceService;

    private enum VoiceStyleAction {
        SHOW_CANDIDATES,
        PREVIEW,
        CONFIRM,
        MORE,
        ASK_CLARIFY
    }

    public VoiceStyleWechatTool(
            ChatService chatService,
            VoiceSynthesisService voiceSynthesisService,
            VoicePreferenceService voicePreferenceService) {
        this.chatService = chatService;
        this.voiceSynthesisService = voiceSynthesisService;
        this.voicePreferenceService = voicePreferenceService;
    }

    @Override
    public String name() {
        return "voice_style";
    }

    @Override
    public String description() {
        return "音色修改工具：当用户想修改、筛选、试听或确认语音合成音色时使用；支持温柔女声、沉稳男声、讲故事、播报等偏好筛选";
    }

    @Override
    public List<String> arguments() {
        return List.of("action", "query", "voice", "index", "preview_text");
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(
                WechatToolParameter.optionalEnum(
                        "action",
                        "音色流程动作：展示候选、试听、确认选择、换一批或追问",
                        List.of("show_candidates", "preview", "confirm", "more", "ask_clarify"),
                        "show_candidates"),
                WechatToolParameter.optionalString(
                        "query",
                        "用户对音色的自然语言偏好，要结合上下文补全，例如温柔女声、沉稳男声、适合讲故事",
                        "温柔女声"),
                WechatToolParameter.optionalString(
                        "voice",
                        "用户明确指定的音色名称",
                        "Cherry"),
                WechatToolParameter.optionalString(
                        "index",
                        "用户在候选列表中选择或试听的序号，例如第一个、第五个",
                        "5"),
                WechatToolParameter.optionalString(
                        "preview_text",
                        "试听时要合成的一小段文本；为空时使用默认试听文本",
                        "你好，我是你的 AI 助手"));
    }

    @Override
    public WechatToolCapability capability() {
        return new WechatToolCapability(
                "根据用户偏好筛选、试听、确认并长期保存语音合成音色。",
                List.of(
                        "用户需求模糊时需要追问性别、语言、风格或用途。",
                        "用户说选择第几个、把第几个当音色时，要结合上一批候选列表理解，不能重新生成一批候选。",
                        "性别约束必须严格遵守：女声只给女声，男声只给男声。"),
                List.of("action：show_candidates/preview/confirm/more/ask_clarify", "query：音色偏好", "index 或 voice：候选序号或音色名"),
                List.of("候选音色列表", "试听语音", "已保存的用户音色偏好"));
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        String text = firstNonBlank(request.argument("query"), request.argument("message"), request.userText());
        String action = request.argument("action");

        Optional<VoiceProfile> explicitVoice = explicitVoice(request);
        VoiceStyleAction resolvedAction = resolveAction(request, text, action, explicitVoice);
        return switch (resolvedAction) {
            case PREVIEW -> handlePreview(request, text, explicitVoice);
            case MORE -> showCandidates(voicePreferenceService.nextCandidatePage(request.sessionKey()));
            case CONFIRM -> handleConfirm(request, text, explicitVoice);
            case ASK_CLARIFY -> WechatReply.text("你想换成什么感觉的声音？可以直接说“温柔女声”“沉稳男声”“适合讲故事的声音”，也可以告诉我性别、语言、风格和用途。");
            case SHOW_CANDIDATES -> showCandidates(voicePreferenceService.searchAndRememberCandidates(
                    request.sessionKey(),
                    searchQueryWithContext(request.sessionKey(), text)));
        };
    }

    private VoiceStyleAction resolveAction(
            WechatToolRequest request,
            String text,
            String action,
            Optional<VoiceProfile> explicitVoice) {
        if (isPreviewRequest(text, action)) {
            return VoiceStyleAction.PREVIEW;
        }
        if (isMoreRequest(text, action)) {
            return VoiceStyleAction.MORE;
        }
        if (isExplicitConfirmAction(action) || explicitVoice.isPresent()) {
            return VoiceStyleAction.CONFIRM;
        }

        boolean hasDisplayedCandidates = voicePreferenceService.hasDisplayedCandidates(request.sessionKey());
        int ordinal = ordinal(firstNonBlank(request.argument("index"), text));
        if (hasDisplayedCandidates && ordinal > 0 && refersToDisplayedCandidate(text)) {
            return VoiceStyleAction.CONFIRM;
        }

        if (voicePreferenceService.recentPreview(request.sessionKey()).isPresent() && refersToRecentPreview(text)) {
            return VoiceStyleAction.CONFIRM;
        }
        if (isVagueSelection(text) || isGenericChangeRequest(text)) {
            return VoiceStyleAction.ASK_CLARIFY;
        }
        return VoiceStyleAction.SHOW_CANDIDATES;
    }

    private String searchQueryWithContext(String sessionKey, String text) {
        if (!shouldRefinePreviousQuery(sessionKey, text)) {
            return text;
        }
        return voicePreferenceService.lastQuery(sessionKey)
                .map(lastQuery -> lastQuery + "，" + text)
                .orElse(text);
    }

    private WechatReply handlePreview(WechatToolRequest request, String text, Optional<VoiceProfile> explicitVoice) {
        Optional<VoiceProfile> profile = explicitVoice
                .or(() -> voiceByOrdinal(request, text))
                .or(() -> voicePreferenceService.recentPreview(request.sessionKey()));
        if (profile.isEmpty()) {
            return WechatReply.text("你想试听哪一个音色？可以回复“试听第一个”或直接说“试听 Serena”。");
        }

        VoiceProfile selected = profile.get();
        String previewText = previewText(request, text);
        try {
            List<VoiceSynthesisSegment> segments = voiceSynthesisService.synthesizeForWechat(previewText, selected.voice());
            voicePreferenceService.rememberPreview(request.sessionKey(), selected);
            List<WechatReply.Part> parts = new ArrayList<>();
            for (VoiceSynthesisSegment segment : segments) {
                parts.add(WechatReply.Part.voice(new WechatReply.Voice(
                        segment.audioBytes(),
                        segment.fileName(),
                        segment.durationMs(),
                        segment.sampleRate(),
                        segment.encodeType(),
                        segment.bitsPerSample(),
                        segment.transcriptText())));
            }
            return WechatReply.ordered(parts);
        } catch (RuntimeException exception) {
            log.warn("微信音色试听失败，userId={}, voice={}, error={}",
                    request.sessionKey(),
                    selected.voice(),
                    rootMessage(exception));
            return WechatReply.text("试听语音生成失败，请稍后重试。");
        }
    }

    private WechatReply handleConfirm(WechatToolRequest request, String text, Optional<VoiceProfile> explicitVoice) {
        Optional<VoiceProfile> profile = explicitVoice
                .or(() -> voiceByOrdinal(request, text))
                .or(() -> confirmCurrentPreview(text) ? voicePreferenceService.recentPreview(request.sessionKey()) : Optional.empty());

        if (profile.isEmpty()) {
            return WechatReply.text("你想确认哪一个音色？可以回复“选第一个”或直接说音色名。");
        }

        VoiceProfile selected = profile.get();
        voicePreferenceService.savePreference(request.sessionKey(), selected);
        return WechatReply.text("已切换为 " + selected.voice() + "（" + selected.displayName() + "）。之后你的语音回复都会优先使用这个音色。");
    }

    private WechatReply showCandidates(VoiceCandidatePage page) {
        if (page.candidates().isEmpty()) {
            return WechatReply.text("我还需要你给一个大致方向，比如温柔女声、沉稳男声、适合讲故事或适合播报。");
        }

        StringBuilder reply = new StringBuilder();
        reply.append("我找到这些比较合适的音色：").append('\n');
        for (int index = 0; index < page.candidates().size(); index++) {
            VoiceProfile profile = page.candidates().get(index);
            reply.append(index + 1)
                    .append(". ")
                    .append(profile.voice())
                    .append("（")
                    .append(profile.displayName())
                    .append("）：")
                    .append(profile.description())
                    .append('\n');
        }
        reply.append('\n').append("你可以回复“试听第一个”“选第二个”");
        if (page.hasMore()) {
            reply.append("或“换一批”");
        }
        reply.append("。");
        return WechatReply.text(reply.toString());
    }

    private Optional<VoiceProfile> explicitVoice(WechatToolRequest request) {
        String voice = firstNonBlank(request.argument("voice"), directVoiceName(request.userText()));
        return voicePreferenceService.findByVoice(voice);
    }

    private Optional<VoiceProfile> voiceByOrdinal(WechatToolRequest request, String text) {
        int ordinal = ordinal(firstNonBlank(request.argument("index"), text));
        return ordinal <= 0 ? Optional.empty() : voicePreferenceService.candidateByOrdinal(request.sessionKey(), ordinal);
    }

    private String directVoiceName(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        for (VoiceProfile profile : voicePreferenceService.catalog().all()) {
            String lowerText = text.toLowerCase(Locale.ROOT);
            if (lowerText.contains(profile.voice().toLowerCase(Locale.ROOT)) || text.contains(profile.displayName())) {
                return profile.voice();
            }
        }
        return "";
    }

    private String previewText(WechatToolRequest request, String text) {
        String explicit = request.argument("preview_text");
        if (!explicit.isBlank()) {
            return explicit;
        }
        if (text.contains("故事") || text.contains("播报") || text.contains("英文") || text.toLowerCase(Locale.ROOT).contains("english")) {
            try {
                String generated = chatService.reply("""
                        请生成一句适合试听 TTS 音色的短句。
                        要求：20-40 个中文字符；不要使用 Markdown；如果用户要求英文试听，可以输出一句简单英文。
                        用户试听要求：%s
                        """.formatted(text));
                if (generated != null && !generated.isBlank()) {
                    return generated.strip();
                }
            } catch (RuntimeException exception) {
                log.debug("试听文本生成失败，使用默认试听文本，error={}", rootMessage(exception));
            }
        }
        return DEFAULT_PREVIEW_TEXT;
    }

    private boolean isPreviewRequest(String text, String action) {
        return "preview".equalsIgnoreCase(action)
                || "试听".equals(action)
                || text.contains("试听")
                || text.contains("听听");
    }

    private boolean isMoreRequest(String text, String action) {
        return "more".equalsIgnoreCase(action)
                || text.contains("更多")
                || text.contains("换一批")
                || text.contains("还有吗");
    }

    private boolean isConfirmRequest(String text, String action) {
        return "confirm".equalsIgnoreCase(action)
                || "select".equalsIgnoreCase(action)
                || "choose".equalsIgnoreCase(action)
                || "use".equalsIgnoreCase(action)
                || text.contains("选第")
                || text.contains("确认第")
                || text.contains("就用")
                || text.contains("用刚才")
                || text.contains("确定这个")
                || text.contains("换成它")
                || !directVoiceName(text).isBlank();
    }

    private boolean isExplicitConfirmAction(String action) {
        return "confirm".equalsIgnoreCase(action)
                || "select".equalsIgnoreCase(action)
                || "choose".equalsIgnoreCase(action)
                || "use".equalsIgnoreCase(action)
                || "确认".equals(action)
                || "选择".equals(action)
                || "使用".equals(action);
    }

    private boolean refersToDisplayedCandidate(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return ordinal(text) > 0
                && (text.contains("第")
                || text.contains("号")
                || text.contains("个")
                || text.contains("用")
                || text.contains("选")
                || text.contains("选择")
                || text.contains("确认")
                || text.contains("当成")
                || text.contains("作为")
                || text.matches(".*\\d+.*"));
    }

    private boolean refersToRecentPreview(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.strip();
        return containsAny(normalized,
                "就用这个", "用刚才", "确定这个", "换成它", "就它", "这个声音可以", "这个可以", "刚才那个", "刚刚那个", "用这个");
    }

    private boolean confirmCurrentPreview(String text) {
        return text.contains("就用这个")
                || text.contains("用刚才")
                || text.contains("确定这个")
                || text.contains("换成它")
                || text.contains("这个声音可以")
                || text.contains("这个可以")
                || text.contains("刚才那个")
                || text.contains("刚刚那个");
    }

    private boolean isGenericChangeRequest(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        boolean mentionsChange = text.contains("修改")
                || text.contains("更换")
                || text.contains("换")
                || text.contains("调整")
                || text.contains("设置");
        boolean mentionsVoiceStyle = text.contains("音色") || text.contains("声音") || text.contains("声线");
        boolean hasPreference = containsAny(text,
                "男", "女", "中文", "英文", "温柔", "沉稳", "活泼", "知性", "专业", "播报", "故事", "朗读", "可爱", "自然");
        return mentionsChange && mentionsVoiceStyle && !hasPreference;
    }

    private boolean shouldRefinePreviousQuery(String sessionKey, String text) {
        if (text == null || text.isBlank() || voicePreferenceService.lastQuery(sessionKey).isEmpty()) {
            return false;
        }
        if (hasExplicitGenderPreference(text) || directVoiceName(text) != null && !directVoiceName(text).isBlank()) {
            return false;
        }
        return containsAny(text, "更", "再", "还要", "还是", "偏", "稍微", "一点", "一些", "不要", "别用", "成熟", "年轻", "自然", "温柔", "沉稳", "活泼", "可爱");
    }

    private boolean hasExplicitGenderPreference(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return containsAny(text,
                "女声", "女生", "女孩子", "女性", "小姐姐", "女的", "女音", "女声线",
                "男声", "男生", "男孩子", "男性", "大叔", "老者", "男的", "男音", "男声线");
    }

    private boolean isVagueSelection(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        String normalized = text.strip();
        return "可以".equals(normalized)
                || "确定".equals(normalized)
                || "随便".equals(normalized)
                || "都行".equals(normalized)
                || "你看着办".equals(normalized);
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private int ordinal(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        if (containsAny(text, "第一个", "第1个", "第一", "一号", "1")) {
            return 1;
        }
        if (containsAny(text, "第二个", "第2个", "第二", "二号", "2")) {
            return 2;
        }
        if (containsAny(text, "第三个", "第3个", "第三", "三号", "3")) {
            return 3;
        }
        if (containsAny(text, "第四个", "第4个", "第四", "四号", "4")) {
            return 4;
        }
        if (containsAny(text, "第五个", "第5个", "第五", "五号", "5")) {
            return 5;
        }
        Matcher matcher = Pattern.compile("(\\d+)").matcher(text);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        return "";
    }

    private String rootMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
