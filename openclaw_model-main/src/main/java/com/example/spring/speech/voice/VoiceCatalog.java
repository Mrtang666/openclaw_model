package com.example.spring.speech.voice;

import static com.example.spring.speech.voice.VoiceProfile.Accent.AMERICAN;
import static com.example.spring.speech.voice.VoiceProfile.Accent.BEIJING;
import static com.example.spring.speech.voice.VoiceProfile.Accent.CANTONESE;
import static com.example.spring.speech.voice.VoiceProfile.Accent.INTERNATIONAL;
import static com.example.spring.speech.voice.VoiceProfile.Accent.MANDARIN;
import static com.example.spring.speech.voice.VoiceProfile.Accent.MINNAN;
import static com.example.spring.speech.voice.VoiceProfile.Accent.NANJING;
import static com.example.spring.speech.voice.VoiceProfile.Accent.SHAANXI;
import static com.example.spring.speech.voice.VoiceProfile.Accent.SHANGHAI;
import static com.example.spring.speech.voice.VoiceProfile.Accent.SICHUAN;
import static com.example.spring.speech.voice.VoiceProfile.Accent.TIANJIN;
import static com.example.spring.speech.voice.VoiceProfile.Gender.FEMALE;
import static com.example.spring.speech.voice.VoiceProfile.Gender.MALE;
import static com.example.spring.speech.voice.VoiceProfile.Language.CHINESE;
import static com.example.spring.speech.voice.VoiceProfile.Language.DIALECT;
import static com.example.spring.speech.voice.VoiceProfile.Language.ENGLISH;
import static com.example.spring.speech.voice.VoiceProfile.Style.GENTLE;
import static com.example.spring.speech.voice.VoiceProfile.Style.LIVELY;
import static com.example.spring.speech.voice.VoiceProfile.Style.MATURE;
import static com.example.spring.speech.voice.VoiceProfile.Style.NATURAL;
import static com.example.spring.speech.voice.VoiceProfile.Style.STEADY;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class VoiceCatalog {
    private static final Set<VoiceProfile.Language> STANDARD_LANGUAGES = Set.of(CHINESE, ENGLISH);
    private static final List<VoiceProfile> PROFILES = List.of(
        profile("Cherry", "芊悦", FEMALE, Set.of(NATURAL, LIVELY), STANDARD_LANGUAGES,
            MANDARIN, "阳光积极、亲切自然", 100),
        profile("Serena", "苏瑶", FEMALE, Set.of(GENTLE, NATURAL), STANDARD_LANGUAGES,
            MANDARIN, "温柔亲切", 98),
        profile("Ethan", "晨煦", MALE, Set.of(LIVELY, NATURAL), STANDARD_LANGUAGES,
            MANDARIN, "阳光温暖、富有活力", 96),
        profile("Maia", "四月", FEMALE, Set.of(GENTLE, STEADY), STANDARD_LANGUAGES,
            MANDARIN, "知性温柔", 94),
        profile("Moon", "月白", MALE, Set.of(NATURAL, MATURE), STANDARD_LANGUAGES,
            MANDARIN, "率性帅气", 92),
        profile("Kai", "凯", MALE, Set.of(GENTLE, STEADY), STANDARD_LANGUAGES,
            MANDARIN, "舒缓耐听", 90),
        profile("Neil", "阿闻", MALE, Set.of(STEADY, MATURE), STANDARD_LANGUAGES,
            MANDARIN, "专业新闻主持风格", 89),
        profile("Andre", "安德雷", MALE, Set.of(STEADY, MATURE, NATURAL), STANDARD_LANGUAGES,
            INTERNATIONAL, "磁性、自然、沉稳", 88),
        profile("Mia", "乖小妹", FEMALE, Set.of(GENTLE, NATURAL), STANDARD_LANGUAGES,
            MANDARIN, "温顺柔和", 87),
        profile("Katerina", "卡捷琳娜", FEMALE, Set.of(MATURE, STEADY), STANDARD_LANGUAGES,
            INTERNATIONAL, "成熟御姐音色", 86),
        profile("Jennifer", "詹妮弗", FEMALE, Set.of(STEADY, MATURE), Set.of(ENGLISH, CHINESE),
            AMERICAN, "电影质感的美语女声", 85),
        profile("Aiden", "艾登", MALE, Set.of(NATURAL, LIVELY), Set.of(ENGLISH, CHINESE),
            AMERICAN, "自然亲切的美语男声", 84),
        profile("Chelsie", "千雪", FEMALE, Set.of(LIVELY, GENTLE), STANDARD_LANGUAGES,
            MANDARIN, "清晰活泼的年轻女声", 83),
        profile("Ryan", "甜茶", MALE, Set.of(LIVELY, MATURE), STANDARD_LANGUAGES,
            INTERNATIONAL, "富有节奏与表现力", 82),
        profile("Eldric Sage", "沧明子", MALE, Set.of(STEADY, MATURE), STANDARD_LANGUAGES,
            MANDARIN, "沉稳睿智的老者", 81),
        profile("Vincent", "田叔", MALE, Set.of(MATURE, STEADY), STANDARD_LANGUAGES,
            MANDARIN, "沙哑磁性的成熟男声", 80),
        profile("Jada", "上海-阿珍", FEMALE, Set.of(LIVELY, NATURAL), Set.of(DIALECT),
            SHANGHAI, "风风火火的上海话女声", 79),
        profile("Dylan", "北京-晓东", MALE, Set.of(LIVELY, NATURAL), Set.of(DIALECT),
            BEIJING, "北京胡同少年音色", 78),
        profile("Li", "南京-老李", MALE, Set.of(GENTLE, STEADY), Set.of(DIALECT),
            NANJING, "耐心舒缓的南京话男声", 77),
        profile("Marcus", "陕西-秦川", MALE, Set.of(STEADY, MATURE), Set.of(DIALECT),
            SHAANXI, "沉稳质朴的陕西话男声", 76),
        profile("Roy", "闽南-阿杰", MALE, Set.of(LIVELY, NATURAL), Set.of(DIALECT),
            MINNAN, "诙谐直爽的闽南语男声", 75),
        profile("Peter", "天津-李彼得", MALE, Set.of(LIVELY, NATURAL), Set.of(DIALECT),
            TIANJIN, "幽默的天津话男声", 74),
        profile("Sunny", "四川-晴儿", FEMALE, Set.of(GENTLE, LIVELY), Set.of(DIALECT),
            SICHUAN, "甜美的四川话女声", 73),
        profile("Eric", "四川-程川", MALE, Set.of(LIVELY, NATURAL), Set.of(DIALECT),
            SICHUAN, "跳脱自然的四川话男声", 72),
        profile("Rocky", "粤语-阿强", MALE, Set.of(LIVELY, NATURAL), Set.of(DIALECT),
            CANTONESE, "幽默风趣的粤语男声", 71),
        profile("Kiki", "粤语-阿清", FEMALE, Set.of(GENTLE, LIVELY), Set.of(DIALECT),
            CANTONESE, "甜美亲切的粤语女声", 70)
    );

    public List<VoiceProfile> recommend(VoiceSelectionCriteria criteria) {
        return PROFILES.stream()
            .filter(profile -> criteria.language() == null
                || criteria.language() == VoiceProfile.Language.ANY
                || profile.languages().contains(criteria.language()))
            .filter(profile -> criteria.gender() == null
                || criteria.gender() == VoiceProfile.Gender.ANY
                || profile.gender() == criteria.gender())
            .filter(profile -> criteria.style() == null
                || criteria.style() == VoiceProfile.Style.ANY
                || profile.styles().contains(criteria.style()))
            .filter(profile -> criteria.accent() == null
                || criteria.accent() == VoiceProfile.Accent.ANY
                || profile.accent() == criteria.accent())
            .sorted(Comparator.comparingInt(VoiceProfile::popularity).reversed())
            .limit(10)
            .toList();
    }

    public VoiceProfile find(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return PROFILES.stream()
            .filter(profile -> profile.voiceId().equalsIgnoreCase(normalized)
                || profile.displayName().equalsIgnoreCase(normalized))
            .findFirst()
            .orElse(null);
    }

    private static VoiceProfile profile(
        String voiceId,
        String displayName,
        VoiceProfile.Gender gender,
        Set<VoiceProfile.Style> styles,
        Set<VoiceProfile.Language> languages,
        VoiceProfile.Accent accent,
        String description,
        int popularity) {
        return new VoiceProfile(
            voiceId, displayName, gender, styles, languages, accent, description, popularity);
    }
}
