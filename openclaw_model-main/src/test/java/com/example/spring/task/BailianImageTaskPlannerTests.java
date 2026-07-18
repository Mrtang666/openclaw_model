package com.example.spring.task;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BailianImageTaskPlannerTests {
    @Test
    void extractsJsonFromModelCodeFenceOrPlainText() {
        assertThat(BailianImageTaskPlanner.extractJson(
            "```json\n{\"intent\":\"IMAGE_TASK\"}\n```"))
            .isEqualTo("{\"intent\":\"IMAGE_TASK\"}");
        assertThat(BailianImageTaskPlanner.extractJson(
            "结果如下：{\"ready\":true}"))
            .isEqualTo("{\"ready\":true}");
    }
}
