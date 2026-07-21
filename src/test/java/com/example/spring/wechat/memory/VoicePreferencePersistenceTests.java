package com.example.spring.wechat.memory;

import com.example.spring.wechat.voice.style.model.VoiceProfile;
import com.example.spring.wechat.voice.style.service.VoiceCatalog;
import com.example.spring.wechat.voice.style.service.VoicePreferenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(args = "/status")
@ActiveProfiles("test")
class VoicePreferencePersistenceTests {

    @Autowired
    private VoicePreferenceService preferenceService;

    @Autowired
    private VoiceCatalog voiceCatalog;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearPreferences() {
        TestDatabaseGuard.assertUsingTestDatabase(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM tool_execution_logs");
        jdbcTemplate.update("DELETE FROM conversation_summaries");
        jdbcTemplate.update("DELETE FROM conversation_messages");
        jdbcTemplate.update("DELETE FROM conversation_states");
        jdbcTemplate.update("DELETE FROM user_preferences");
        jdbcTemplate.update("DELETE FROM conversations");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void savesConfirmedVoiceAsLongTermWechatPreference() {
        VoiceProfile serena = voiceCatalog.findByVoice("Serena").orElseThrow();

        preferenceService.savePreference("wx-user", serena);

        assertThat(preferenceService.effectiveVoice("wx-user")).isEqualTo("Serena");
        assertThat(jdbcTemplate.queryForObject(
                """
                        SELECT preference_value_json
                        FROM user_preferences preference
                        JOIN users user_row ON preference.user_id = user_row.id
                        WHERE user_row.wechat_user_id = ? AND preference.preference_key = 'voice'
                        """,
                String.class,
                "wx-user")).contains("Serena");
    }
}
