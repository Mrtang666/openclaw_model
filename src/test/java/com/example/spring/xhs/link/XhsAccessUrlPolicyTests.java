package com.example.spring.xhs.link;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class XhsAccessUrlPolicyTests {

    @Test
    void keepsOnlyAllowedAccessParameters() {
        String result = XhsAccessUrlPolicy.sanitize(
                "https://www.xiaohongshu.com/explore/note-1?xsec_token=a%2Bb&xsec_source=pc_search&cookie=secret",
                "note-1");

        assertThat(result).isEqualTo(
                "https://www.xiaohongshu.com/explore/note-1?xsec_token=a%2Bb&xsec_source=pc_search");
    }

    @Test
    void rejectsWrongHostPathAndMissingToken() {
        assertThat(XhsAccessUrlPolicy.sanitize(
                "https://evilxiaohongshu.com/explore/note-1?xsec_token=value", "note-1")).isBlank();
        assertThat(XhsAccessUrlPolicy.sanitize(
                "https://www.xiaohongshu.com/explore/other?xsec_token=value", "note-1")).isBlank();
        assertThat(XhsAccessUrlPolicy.sanitize(
                "https://www.xiaohongshu.com/explore/note-1?xsec_source=pc_search", "note-1")).isBlank();
    }
}
