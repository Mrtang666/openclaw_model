package com.example.spring.speech.voice;

import com.example.spring.memory.ConversationMemoryService;
import com.example.spring.speech.SpeechProperties;
import com.example.spring.speech.voice.VoiceProfile.Accent;
import com.example.spring.speech.voice.VoiceProfile.Gender;
import com.example.spring.speech.voice.VoiceProfile.Language;
import com.example.spring.speech.voice.VoiceProfile.Style;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class VoiceSelectionService {
    private static final Pattern CHANGE_INTENT = Pattern.compile(
        "(修改|更换|换|换个|换成|调整|改成|改为|切换|设置|使用|想要|想用|用).{0,8}(音色|声音|声线|男声|女声)"
            + "|(音色|声音|声线).{0,8}(修改|更换|换|调整|改成|改为|切换|设置)"
            + "|(声音|音色).{0,5}(温柔|沉稳|活泼|成熟|磁性).{0,3}(一点|一些|些)");
    private static final Pattern NEGATIVE_INTENT = Pattern.compile(
        "(不要|不用|别|不需要).{0,6}(修改|更换|换|调整).{0,6}(音色|声音|声线)"
            + "|(不要|不用|别).{0,6}(换音色|换声音)"
            + "|(取消|停止).{0,4}(修改|更换|换).{0,4}(音色|声音)");
    private static final Pattern CANCEL = Pattern.compile("^(取消|算了|退出|停止|不改了|保持原来).{0,6}$");
    private static final Pattern RESET = Pattern.compile(
        "(恢复|改回|换回|使用).{0,4}(默认|原始|最初).{0,4}(音色|声音)"
            + "|(音色|声音).{0,4}(恢复|改回|换回).{0,4}(默认|原始|最初)");
    private static final Pattern CURRENT = Pattern.compile(
        "(当前使用的|当前|现在使用的|正在使用的).{0,2}(音色|声音)"
            + "|(什么|哪个).{0,3}(音色|声音)");

    private final VoiceCatalog catalog;
    private final ConversationMemoryService memoryService;
    private final SpeechProperties speechProperties;
    private final Map<String, Session> sessions = new HashMap<>();

    public VoiceSelectionService(
        VoiceCatalog catalog,
        ConversationMemoryService memoryService,
        SpeechProperties speechProperties) {
        this.catalog = catalog;
        this.memoryService = memoryService;
        this.speechProperties = speechProperties;
    }

    public synchronized VoiceSelectionResult handle(String userId, String text) {
        String input = text == null ? "" : text.trim();
        if (input.isBlank()) {
            return VoiceSelectionResult.ignored();
        }
        if (RESET.matcher(input).find()) {
            sessions.remove(userId);
            memoryService.clearVoicePreference(userId);
            return VoiceSelectionResult.replied(
                "已恢复默认音色：" + speechProperties.getTtsVoice()
                    + "。后续语音回复将使用默认音色。");
        }
        if (CURRENT.matcher(input).find()) {
            VoicePreference preference = memoryService.getVoicePreference(userId);
            if (preference == null) {
                return VoiceSelectionResult.replied(
                    "当前使用默认音色：" + speechProperties.getTtsVoice() + "。");
            }
            return VoiceSelectionResult.replied(
                "当前音色是“" + preference.displayName() + "”（"
                    + preference.voiceId() + "）。");
        }

        Session session = sessions.get(userId);
        if (session == null) {
            if (NEGATIVE_INTENT.matcher(input).find() || !CHANGE_INTENT.matcher(input).find()) {
                return VoiceSelectionResult.ignored();
            }
            session = new Session(prefill(input), Step.LANGUAGE, List.of());
            session = advancePastKnownCriteria(session);
            sessions.put(userId, session);
            if (session.step() == Step.CANDIDATES) {
                return showCandidates(userId, session);
            }
            return VoiceSelectionResult.replied(introduction() + prompt(session.step(), session.criteria()));
        }

        if (CANCEL.matcher(input).matches()) {
            sessions.remove(userId);
            return VoiceSelectionResult.replied("已取消修改音色，继续使用之前的音色。");
        }
        if (CHANGE_INTENT.matcher(input).find()) {
            session = new Session(prefill(input), Step.LANGUAGE, List.of());
            session = advancePastKnownCriteria(session);
            sessions.put(userId, session);
            if (session.step() == Step.CANDIDATES) {
                return showCandidates(userId, session);
            }
            return VoiceSelectionResult.replied("已重新开始筛选。\n" + prompt(
                session.step(), session.criteria()));
        }
        if (session.step() == Step.CANDIDATES) {
            return selectCandidate(userId, session, input);
        }
        Choice<?> choice = parseChoice(session.step(), input, session.criteria());
        if (!choice.recognized()) {
            return VoiceSelectionResult.replied(
                "没有识别出这个选项，请按编号或描述回答。\n"
                    + prompt(session.step(), session.criteria()));
        }
        VoiceSelectionCriteria criteria = apply(session.criteria(), session.step(), choice.value());
        Session updated = advancePastKnownCriteria(
            new Session(criteria, next(session.step()), List.of()));
        sessions.put(userId, updated);
        if (updated.step() == Step.CANDIDATES) {
            return showCandidates(userId, updated);
        }
        return VoiceSelectionResult.replied(prompt(updated.step(), updated.criteria()));
    }

    public synchronized boolean hasActiveSession(String userId) {
        return sessions.containsKey(userId);
    }

    public synchronized boolean shouldHandle(String userId, String text) {
        String input = text == null ? "" : text.trim();
        if (input.isBlank()) {
            return sessions.containsKey(userId);
        }
        if (sessions.containsKey(userId)) {
            return true;
        }
        return RESET.matcher(input).find()
            || CURRENT.matcher(input).find()
            || (!NEGATIVE_INTENT.matcher(input).find() && CHANGE_INTENT.matcher(input).find());
    }

    private VoiceSelectionResult showCandidates(String userId, Session session) {
        List<VoiceProfile> candidates = catalog.recommend(session.criteria());
        boolean relaxed = false;
        if (candidates.isEmpty() && session.criteria().accent() != null) {
            candidates = catalog.recommend(session.criteria().withAccent(null));
            relaxed = true;
        }
        if (candidates.isEmpty() && session.criteria().style() != null) {
            candidates = catalog.recommend(
                session.criteria().withAccent(null).withStyle(null));
            relaxed = true;
        }
        if (candidates.isEmpty()) {
            candidates = catalog.recommend(new VoiceSelectionCriteria(
                session.criteria().language(), null, null, null));
            relaxed = true;
        }
        Session updated = new Session(session.criteria(), Step.CANDIDATES, candidates);
        sessions.put(userId, updated);
        StringBuilder reply = new StringBuilder();
        if (relaxed) {
            reply.append("完全符合全部条件的音色不足，已适当放宽次要条件。\n");
        }
        reply.append("按推荐热度为你找到以下音色：\n");
        for (int index = 0; index < candidates.size(); index++) {
            VoiceProfile profile = candidates.get(index);
            reply.append(index + 1).append(". ")
                .append(profile.displayName()).append("（")
                .append(profile.voiceId()).append("）：")
                .append(profile.description()).append('\n');
        }
        reply.append("请回复编号、中文音色名或 voice ID。")
            .append("发送“试听3”或“试听苏瑶”可以先听效果，试听不会立即切换音色。")
            .append("回复“取消”可退出修改。");
        return VoiceSelectionResult.replied(reply.toString());
    }

    private VoiceSelectionResult selectCandidate(String userId, Session session, String input) {
        boolean previewRequested = input.trim().startsWith("试听")
            || input.trim().startsWith("听听");
        String selectionInput = previewRequested
            ? input.trim().replaceFirst("^(试听|听听)", "").trim()
            : input;
        VoiceProfile selected = candidateByInput(session.candidates(), selectionInput);
        if (selected == null) {
            return VoiceSelectionResult.replied(
                "没有找到这个音色，请回复列表中的编号、中文音色名或 voice ID。"
                    + "例如“试听3”或“选择3”。回复“取消”可退出修改。");
        }
        String languageType = session.criteria().language() == Language.ENGLISH
            ? "English" : "Chinese";
        if (previewRequested) {
            return VoiceSelectionResult.preview(
                "正在生成“" + selected.displayName() + "”的试听音频，请稍等。",
                new VoicePreference(
                    selected.voiceId(), selected.displayName(), languageType));
        }
        boolean saved = memoryService.setVoicePreference(
            userId, new VoicePreference(selected.voiceId(), selected.displayName(), languageType));
        if (!saved) {
            return VoiceSelectionResult.replied("音色保存失败，请稍后重新选择。");
        }
        sessions.remove(userId);
        return VoiceSelectionResult.replied(
            "音色已修改为“" + selected.displayName() + "”（" + selected.voiceId()
                + "）。后续语音回复会一直使用该音色，直到你再次修改或恢复默认音色。");
    }

    private VoiceProfile candidateByInput(List<VoiceProfile> candidates, String input) {
        String normalized = input.trim().replaceAll("[，。！？,.!?]", "");
        var number = OrdinalChoiceParser.parse(normalized);
        if (number.isPresent()) {
            int index = number.getAsInt() - 1;
            if (index >= 0 && index < candidates.size()) {
                return candidates.get(index);
            }
        }
        for (VoiceProfile profile : candidates) {
            if (normalized.equalsIgnoreCase(profile.voiceId())
                || normalized.equalsIgnoreCase(profile.displayName())
                || normalized.equalsIgnoreCase("选择" + profile.displayName())
                || normalized.equalsIgnoreCase("用" + profile.displayName())) {
                return profile;
            }
        }
        return null;
    }

    private static Session advancePastKnownCriteria(Session session) {
        Step step = session.step();
        VoiceSelectionCriteria criteria = session.criteria();
        while (step != Step.CANDIDATES && known(criteria, step)) {
            step = next(step);
        }
        return new Session(criteria, step, session.candidates());
    }

    private static boolean known(VoiceSelectionCriteria criteria, Step step) {
        return switch (step) {
            case LANGUAGE -> criteria.language() != null;
            case GENDER -> criteria.gender() != null;
            case STYLE -> criteria.style() != null;
            case ACCENT -> criteria.accent() != null
                || criteria.language() == Language.CHINESE;
            case CANDIDATES -> false;
        };
    }

    private static Step next(Step step) {
        return switch (step) {
            case LANGUAGE -> Step.GENDER;
            case GENDER -> Step.STYLE;
            case STYLE -> Step.ACCENT;
            case ACCENT, CANDIDATES -> Step.CANDIDATES;
        };
    }

    private static VoiceSelectionCriteria apply(
        VoiceSelectionCriteria criteria,
        Step step,
        Object value) {
        return switch (step) {
            case LANGUAGE -> criteria.withLanguage((Language) value);
            case GENDER -> criteria.withGender((Gender) value);
            case STYLE -> criteria.withStyle((Style) value);
            case ACCENT -> criteria.withAccent((Accent) value);
            case CANDIDATES -> criteria;
        };
    }

    private static Choice<?> parseChoice(
        Step step,
        String input,
        VoiceSelectionCriteria criteria) {
        String value = normalize(input);
        return switch (step) {
            case LANGUAGE -> parseLanguage(value);
            case GENDER -> parseGender(value);
            case STYLE -> parseStyle(value);
            case ACCENT -> parseAccent(value, criteria.language());
            case CANDIDATES -> new Choice<>(false, null);
        };
    }

    private static VoiceSelectionCriteria prefill(String input) {
        String value = normalize(input);
        Language language = containsAny(value, "方言", "粤语", "四川话", "上海话", "北京话",
            "南京话", "陕西话", "闽南语", "天津话") ? Language.DIALECT
            : containsAny(value, "英文", "英语", "english", "美式", "英式") ? Language.ENGLISH
            : containsAny(value, "中文", "普通话", "国语") ? Language.CHINESE : null;
        Gender gender = containsAny(value, "女声", "女生", "女性") ? Gender.FEMALE
            : containsAny(value, "男声", "男生", "男性") ? Gender.MALE : null;
        Style style = containsAny(value, "温柔", "亲切", "舒缓", "柔和") ? Style.GENTLE
            : containsAny(value, "沉稳", "专业", "稳重") ? Style.STEADY
            : containsAny(value, "活泼", "年轻", "阳光", "有活力") ? Style.LIVELY
            : containsAny(value, "成熟", "磁性", "御姐", "大叔") ? Style.MATURE
            : containsAny(value, "自然", "日常", "普通") ? Style.NATURAL : null;
        Choice<Accent> accentChoice = parseAccent(value, language);
        Accent accent = accentChoice.recognized() ? accentChoice.value() : null;
        return new VoiceSelectionCriteria(language, gender, style, accent);
    }

    private static Choice<Language> parseLanguage(String value) {
        if (matchesChoice(value, "1") || containsAny(value, "中文", "普通话", "国语")) {
            return new Choice<>(true, Language.CHINESE);
        }
        if (matchesChoice(value, "2") || containsAny(value, "英文", "英语", "english")) {
            return new Choice<>(true, Language.ENGLISH);
        }
        if (matchesChoice(value, "3") || containsAny(value, "方言", "粤语", "四川话", "上海话")) {
            return new Choice<>(true, Language.DIALECT);
        }
        return new Choice<>(false, null);
    }

    private static Choice<Gender> parseGender(String value) {
        if (matchesChoice(value, "1") || containsAny(value, "女声", "女生", "女性")) {
            return new Choice<>(true, Gender.FEMALE);
        }
        if (matchesChoice(value, "2") || containsAny(value, "男声", "男生", "男性")) {
            return new Choice<>(true, Gender.MALE);
        }
        if (matchesChoice(value, "3") || containsAny(value, "不限", "都可以", "无所谓")) {
            return new Choice<>(true, Gender.ANY);
        }
        return new Choice<>(false, null);
    }

    private static Choice<Style> parseStyle(String value) {
        if (matchesChoice(value, "1") || containsAny(value, "温柔", "亲切", "舒缓", "柔和")) {
            return new Choice<>(true, Style.GENTLE);
        }
        if (matchesChoice(value, "2") || containsAny(value, "沉稳", "专业", "稳重")) {
            return new Choice<>(true, Style.STEADY);
        }
        if (matchesChoice(value, "3") || containsAny(value, "活泼", "年轻", "阳光")) {
            return new Choice<>(true, Style.LIVELY);
        }
        if (matchesChoice(value, "4") || containsAny(value, "成熟", "磁性", "御姐", "大叔")) {
            return new Choice<>(true, Style.MATURE);
        }
        if (matchesChoice(value, "5") || containsAny(value, "自然", "日常", "普通")) {
            return new Choice<>(true, Style.NATURAL);
        }
        if (matchesChoice(value, "6") || containsAny(value, "不限", "都可以", "无所谓")) {
            return new Choice<>(true, Style.ANY);
        }
        return new Choice<>(false, null);
    }

    private static Choice<Accent> parseAccent(String value, Language language) {
        if (containsAny(value, "粤语", "广东话")) return new Choice<>(true, Accent.CANTONESE);
        if (containsAny(value, "四川话", "川话")) return new Choice<>(true, Accent.SICHUAN);
        if (containsAny(value, "上海话", "沪语")) return new Choice<>(true, Accent.SHANGHAI);
        if (containsAny(value, "北京话", "北京腔")) return new Choice<>(true, Accent.BEIJING);
        if (containsAny(value, "南京话")) return new Choice<>(true, Accent.NANJING);
        if (containsAny(value, "陕西话")) return new Choice<>(true, Accent.SHAANXI);
        if (containsAny(value, "闽南语", "闽南话")) return new Choice<>(true, Accent.MINNAN);
        if (containsAny(value, "天津话")) return new Choice<>(true, Accent.TIANJIN);
        if (containsAny(value, "美式", "美国口音")) return new Choice<>(true, Accent.AMERICAN);
        if (containsAny(value, "国际", "不限", "都可以", "无所谓")) {
            return new Choice<>(true, Accent.ANY);
        }
        String number = value.replaceAll("[^0-9]", "");
        if (language == Language.ENGLISH) {
            return switch (number) {
                case "1" -> new Choice<>(true, Accent.AMERICAN);
                case "2" -> new Choice<>(true, Accent.ANY);
                default -> new Choice<>(false, null);
            };
        }
        return switch (number) {
            case "1" -> new Choice<>(true, Accent.CANTONESE);
            case "2" -> new Choice<>(true, Accent.SICHUAN);
            case "3" -> new Choice<>(true, Accent.SHANGHAI);
            case "4" -> new Choice<>(true, Accent.BEIJING);
            case "5" -> new Choice<>(true, Accent.MINNAN);
            case "6" -> new Choice<>(true, Accent.ANY);
            default -> new Choice<>(false, null);
        };
    }

    private static String introduction() {
        return "可以，我只会在你明确提出修改音色时进入这个流程。\n";
    }

    private static String prompt(Step step, VoiceSelectionCriteria criteria) {
        return switch (step) {
            case LANGUAGE -> "第1步：主要使用哪种语言？\n1. 中文\n2. 英文\n3. 中文方言";
            case GENDER -> "第2步：希望使用哪种声音？\n1. 女声\n2. 男声\n3. 不限";
            case STYLE -> "第3步：喜欢哪种风格？\n1. 温柔亲切\n2. 沉稳专业\n3. 活泼年轻\n4. 磁性成熟\n5. 自然日常\n6. 不限";
            case ACCENT -> criteria.language() == Language.DIALECT
                ? "第4步：希望使用哪种方言？\n1. 粤语\n2. 四川话\n3. 上海话\n4. 北京话\n5. 闽南语\n6. 不限"
                : "第4步：英文口音偏好？\n1. 美式\n2. 不限";
            case CANDIDATES -> "";
        };
    }

    private static boolean matchesChoice(String value, String number) {
        var parsed = OrdinalChoiceParser.parse(value);
        return parsed.isPresent() && parsed.getAsInt() == Integer.parseInt(number);
    }

    private static boolean containsAny(String value, String... words) {
        for (String word : words) {
            if (value.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT)
            .replaceAll("[，。！？,.!?\\s]", "")
            .trim();
    }

    private enum Step { LANGUAGE, GENDER, STYLE, ACCENT, CANDIDATES }

    private record Session(
        VoiceSelectionCriteria criteria,
        Step step,
        List<VoiceProfile> candidates) {
    }

    private record Choice<T>(boolean recognized, T value) {
    }
}
