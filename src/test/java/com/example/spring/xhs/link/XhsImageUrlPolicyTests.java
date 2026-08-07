package com.example.spring.xhs.link;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class XhsImageUrlPolicyTests {

    @Test
    void acceptsOnlyHttpsXhsCdnHosts() {
        assertThat(XhsImageUrlPolicy.sanitize("https://sns-img-qc.xhscdn.com/a.jpg?x=1"))
                .isEqualTo("https://sns-img-qc.xhscdn.com/a.jpg?x=1");
        assertThat(XhsImageUrlPolicy.sanitize("http://sns-img-qc.xhscdn.com/a.jpg")).isBlank();
        assertThat(XhsImageUrlPolicy.sanitize("https://xhscdn.com.evil.test/a.jpg")).isBlank();
        assertThat(XhsImageUrlPolicy.sanitize("https://example.test/a.jpg")).isBlank();
    }
}
