package com.example.spring.wechat.conversation.tools;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SkillBackedToolMetadataTests {

    @Test
    void skillBackedToolsKeepOnlyCompactRuntimeMetadata() {
        List<Class<? extends WechatTool>> toolTypes = List.of(
                BrowserClickWechatTool.class,
                BrowserOpenWechatTool.class,
                BrowserReadPageWechatTool.class,
                BrowserScreenshotWechatTool.class,
                BrowserTypeWechatTool.class,
                ChatWechatTool.class,
                DocumentAnalysisWechatTool.class,
                DocumentGenerationWechatTool.class,
                EmailWechatTool.class,
                FoodDeliveryWechatTool.class,
                ImageGenerationWechatTool.class,
                ImageUnderstandingWechatTool.class,
                KnowledgeAddWechatTool.class,
                KnowledgeManageWechatTool.class,
                KnowledgeQueryWechatTool.class,
                LogisticsTrackWechatTool.class,
                MapWechatTool.class,
                MeituanTravelWechatTool.class,
                NetdiskAuthWechatTool.class,
                NetdiskListWechatTool.class,
                NetdiskSaveWechatTool.class,
                NetdiskSearchWechatTool.class,
                NetdiskShareWechatTool.class,
                NewsWechatTool.class,
                ShoppingAdviceWechatTool.class,
                TaxiWechatTool.class,
                VideoUnderstandWechatTool.class,
                VoiceRecognitionWechatTool.class,
                VoiceStyleWechatTool.class,
                VoiceSynthesisWechatTool.class,
                WeatherWechatTool.class,
                WebReadWechatTool.class,
                WebSearchWechatTool.class,
                XhsAlertAcknowledgeWechatTool.class,
                XhsAlertSubscribeWechatTool.class,
                XhsCollectWechatTool.class,
                XhsDailyReportWechatTool.class,
                XhsIncidentListWechatTool.class,
                XhsIncidentTransitionWechatTool.class,
                XhsOpinionSearchWechatTool.class);

        List<WechatTool> tools = toolTypes.stream()
                .map(SkillBackedToolMetadataTests::instantiate)
                .toList();

        assertThat(tools).allSatisfy(tool -> {
            WechatToolCapability capability = tool.capability();

            assertThat(tool.description())
                    .as(tool.name() + " description")
                    .isNotBlank()
                    .hasSizeLessThanOrEqualTo(120);
            if (!capability.isEmpty()) {
                assertThat(capability.summary())
                        .as(tool.name() + " summary")
                        .isNotBlank();
            }
            assertThat(capability.boundaries())
                    .as(tool.name() + " boundaries")
                    .isEmpty();
            assertThat(capability.requiredInformation())
                    .as(tool.name() + " required information")
                    .isEmpty();
            assertThat(capability.outputs())
                    .as(tool.name() + " outputs")
                    .isEmpty();
        });
    }

    private static WechatTool instantiate(Class<? extends WechatTool> type) {
        try {
            Constructor<?> constructor = List.of(type.getConstructors()).stream()
                    .min(Comparator.comparingInt(Constructor::getParameterCount))
                    .orElseThrow();
            Object[] arguments = List.of(constructor.getParameterTypes()).stream()
                    .map(SkillBackedToolMetadataTests::mockArgument)
                    .toArray();
            return (WechatTool) constructor.newInstance(arguments);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Failed to instantiate " + type.getSimpleName(), exception);
        }
    }

    private static Object mockArgument(Class<?> type) {
        return mock(type);
    }
}
