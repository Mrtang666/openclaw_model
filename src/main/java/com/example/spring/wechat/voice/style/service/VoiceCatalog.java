package com.example.spring.wechat.voice.style.service;

import com.example.spring.wechat.voice.style.model.VoiceProfile;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * qwen3-tts-flash 官方音色目录。
 * 这里先把常用官方音色固化在代码中，后续如果接入数据库或远程音色列表，只需要替换这个目录实现。
 */
@Component
public class VoiceCatalog {

    private static final List<String> ALL_LANGUAGES = List.of(
            "中文", "英文", "法语", "德语", "俄语", "意大利语", "西班牙语", "葡萄牙语", "日语", "韩语");

    private final List<VoiceProfile> voices = List.of(
            voice("Serena", "苏瑶", "女声", List.of("温柔", "自然", "陪伴"), List.of("聊天", "朗读", "助手"), "温柔小姐姐，适合日常陪伴和轻柔朗读"),
            voice("Maia", "四月", "女声", List.of("知性", "温柔", "柔和"), List.of("朗读", "陪伴", "文章"), "知性与温柔的碰撞，适合文章朗读和温和表达"),
            voice("Mia", "乖小妹", "女声", List.of("温顺", "乖巧", "温柔"), List.of("聊天", "故事", "陪伴"), "温顺如春水，乖巧如初雪"),
            voice("Cherry", "芊悦", "女声", List.of("阳光", "亲切", "自然"), List.of("聊天", "助手", "日常回复"), "阳光积极、亲切自然小姐姐，默认音色"),
            voice("Ethan", "晨煦", "男声", List.of("阳光", "温暖", "活力"), List.of("聊天", "助手", "故事"), "阳光、温暖、有活力的男声"),
            voice("Neil", "阿闻", "男声", List.of("专业", "清晰", "播报"), List.of("新闻", "播报", "知识讲解"), "字正腔圆、专业新闻主持人风格"),
            voice("Eldric Sage", "沧明子", "男声", List.of("沉稳", "睿智", "沧桑"), List.of("故事", "旁白", "讲解"), "沉稳睿智的老者，适合故事旁白"),
            voice("Ryan", "甜茶", "男声", List.of("戏感", "节奏", "张力"), List.of("故事", "角色", "演绎"), "节奏感强、戏感强，适合演绎"),
            voice("Bella", "萌宝", "女声", List.of("可爱", "活泼", "童真"), List.of("故事", "陪伴", "角色"), "小萝莉音色，适合轻松活泼内容"),
            voice("Katerina", "卡捷琳娜", "女声", List.of("成熟", "韵律", "御姐"), List.of("朗读", "播报", "旁白"), "成熟御姐音色，韵律感较强"),
            voice("Jennifer", "詹妮弗", "女声", List.of("英文", "电影感", "品牌感"), List.of("英文", "播报", "旁白"), "品牌级、电影质感般美语女声"),
            voice("Chelsie", "千雪", "女声", List.of("二次元", "甜美", "活泼"), List.of("聊天", "角色", "陪伴"), "二次元虚拟女友风格"),
            voice("Momo", "茉兔", "女声", List.of("撒娇", "搞怪", "活泼"), List.of("聊天", "娱乐", "陪伴"), "撒娇搞怪，适合轻松聊天"),
            voice("Vivian", "十三", "女声", List.of("可爱", "率性", "活泼"), List.of("聊天", "角色", "娱乐"), "拽拽的、可爱的小暴躁"),
            voice("Aiden", "艾登", "男声", List.of("英文", "年轻", "自然"), List.of("英文", "聊天", "故事"), "美语大男孩风格"),
            voice("Moon", "月白", "男声", List.of("率性", "帅气", "自然"), List.of("聊天", "旁白", "故事"), "率性帅气的男声"),
            voice("Kai", "凯", "男声", List.of("舒适", "柔和", "自然"), List.of("聊天", "朗读", "陪伴"), "听感舒适，适合自然朗读"),
            voice("Nofish", "不吃鱼", "男声", List.of("设计师", "自然", "特色"), List.of("聊天", "讲解", "轻松内容"), "不会翘舌音的设计师风格"),
            voice("Bellona", "燕铮莺", "女声", List.of("洪亮", "清晰", "热血"), List.of("故事", "演绎", "旁白"), "声音洪亮、人物鲜活，适合热血故事"),
            voice("Vincent", "田叔", "男声", List.of("沙哑", "江湖", "豪情"), List.of("故事", "旁白", "演绎"), "沙哑烟嗓，适合江湖故事和豪情旁白"),
            voice("Bunny", "萌小姬", "女声", List.of("萌", "可爱", "活泼"), List.of("聊天", "故事", "陪伴"), "萌属性强的小萝莉音色"),
            voice("Elias", "墨讲师", "女声", List.of("严谨", "讲解", "知性"), List.of("知识讲解", "课程", "播报"), "适合把复杂知识讲清楚的讲师风格")
    );

    public List<VoiceProfile> all() {
        return voices;
    }

    public Optional<VoiceProfile> findByVoice(String voice) {
        if (voice == null || voice.isBlank()) {
            return Optional.empty();
        }
        String expected = normalize(voice);
        return voices.stream()
                .filter(profile -> normalize(profile.voice()).equals(expected)
                        || normalize(profile.displayName()).equals(expected))
                .findFirst();
    }

    public List<VoiceProfile> search(String query) {
        String text = query == null ? "" : query.strip();
        VoiceSearchConstraints constraints = VoiceSearchConstraints.from(text);
        return voices.stream()
                .filter(constraints::matches)
                .map(profile -> new ScoredVoice(profile, score(profile, text)))
                .filter(scored -> scored.score() > 0)
                .sorted(Comparator.comparingInt(ScoredVoice::score).reversed())
                .map(ScoredVoice::profile)
                .toList();
    }

    private int score(VoiceProfile profile, String query) {
        if (query.isBlank()) {
            return 0;
        }

        int score = 0;
        String normalized = normalize(query);
        if (normalized.contains(normalize(profile.voice())) || normalized.contains(normalize(profile.displayName()))) {
            score += 100;
        }
        if (containsAny(query, "女", "女声", "女生", "小姐姐") && "女声".equals(profile.gender())) {
            score += 30;
        }
        if (containsAny(query, "男", "男声", "男生", "大叔", "老者") && "男声".equals(profile.gender())) {
            score += 30;
        }
        score += matchTags(query, profile.styles()) * 12;
        score += matchTags(query, profile.scenes()) * 8;
        score += matchTags(query, profile.languages()) * 6;
        if (query.contains("讲故事") || query.contains("故事")) {
            score += profile.scenes().contains("故事") ? 10 : 0;
        }
        if (query.contains("播报") || query.contains("新闻")) {
            score += profile.scenes().contains("播报") || profile.scenes().contains("新闻") ? 10 : 0;
        }
        return score;
    }

    private int matchTags(String query, List<String> tags) {
        int count = 0;
        for (String tag : tags) {
            if (query.contains(tag)) {
                count++;
            }
        }
        return count;
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private static VoiceProfile voice(String voice, String displayName, String gender, List<String> styles, List<String> scenes, String description) {
        return new VoiceProfile(voice, displayName, gender, ALL_LANGUAGES, styles, scenes, description);
    }

    private String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private record VoiceSearchConstraints(Optional<String> gender) {

        static VoiceSearchConstraints from(String query) {
            String text = query == null ? "" : query;
            boolean rejectsMale = containsAnyText(text, "不要男声", "不是男声", "别用男声", "不用男声", "不要男生", "别用男生", "不要男的", "别用男的");
            boolean rejectsFemale = containsAnyText(text, "不要女声", "不是女声", "别用女声", "不用女声", "不要女生", "别用女生", "不要女的", "别用女的");
            boolean wantsFemale = containsAnyText(text, "女声", "女生", "女孩子", "女性", "小姐姐", "女的", "女音", "女声线") && !rejectsFemale;
            boolean wantsMale = containsAnyText(text, "男声", "男生", "男性", "男孩子", "大叔", "老者", "男的", "男音", "男声线") && !rejectsMale;
            if ((wantsFemale || rejectsMale) && !wantsMale) {
                return new VoiceSearchConstraints(Optional.of("女声"));
            }
            if ((wantsMale || rejectsFemale) && !wantsFemale) {
                return new VoiceSearchConstraints(Optional.of("男声"));
            }
            return new VoiceSearchConstraints(Optional.empty());
        }

        boolean matches(VoiceProfile profile) {
            return gender.isEmpty() || gender.get().equals(profile.gender());
        }

        private static boolean containsAnyText(String text, String... values) {
            for (String value : values) {
                if (text.contains(value)) {
                    return true;
                }
            }
            return false;
        }
    }

    private record ScoredVoice(VoiceProfile profile, int score) {
    }
}
