package com.example.spring.task;

import com.example.spring.bailian.BailianChatService;
import com.example.spring.memory.MemoryMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BailianImageTaskPlanner implements ImageTaskPlanner {
    private final BailianChatService chatService;
    private final ObjectMapper objectMapper;

    @Autowired
    public BailianImageTaskPlanner(BailianChatService chatService) {
        this(chatService, new ObjectMapper());
    }

    BailianImageTaskPlanner(BailianChatService chatService, ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.objectMapper = objectMapper;
    }

    @Override
    public ImageTaskPlanningResult plan(
        String userId,
        String userMessage,
        ImageTaskBrief existingBrief,
        boolean activeTask,
        boolean hasSourceImage,
        List<MemoryMessage> history) throws Exception {
        ImageTaskBrief current = existingBrief == null ? ImageTaskBrief.empty() : existingBrief;
        String prompt = """
            你是负责引导用户完成图片生成或图片编辑任务的文本 Agent。
            你的工作是理解用户目标、合并已有需求、判断是否需要追问，并为图片 Agent
            生成明确的最终提示词。不要执行图片生成。

            判断规则：
            1. 简单而明确的娱乐图片应直接 ready=true，不要机械追问。
            2. 只有缺少会明显影响结果的关键信息时才追问，每次最多询问两个问题。
            3. 用户说“随便”“你决定”时，请补充合理默认值并直接执行。
            4. 有活跃图片任务时，短回复通常是在补充原任务；明显无关的普通聊天或天气问题返回 OTHER。
            5. 如果存在参考图片，sourceMode 应填写 EDIT；否则填写 GENERATE。
            6. 如果最近历史中有图片，用户要求去除、添加、替换、调整或改变画面，应判断为 IMAGE_TASK 和 EDIT。
            7. 用户只是询问图片内容时不要编辑；用户表示感谢或切换话题时返回 OTHER。
            8. 用户通过“这张、图中、里面、引用的图片”等方式指代时，结合最近图片历史判断。
            9. 如果历史中没有可用图片，不得声称已经看到引用图片，应通过 question 请用户重新发送。
            10. 只返回一个 JSON 对象，不要使用 Markdown，不要添加解释。

            JSON 格式：
            {
              "intent":"IMAGE_TASK 或 OTHER",
              "ready":true,
              "question":"信息不足时向用户提出的问题，否则为空字符串",
              "finalPrompt":"ready=true 时交给图片 Agent 的完整中文提示词",
              "brief":{
                "subject":"主体",
                "purpose":"用途",
                "style":"风格",
                "scene":"场景或背景",
                "composition":"构图",
                "colors":"色彩",
                "aspectRatio":"比例",
                "visibleText":"画面文字",
                "negativePrompt":"不希望出现的内容",
                "sourceMode":"GENERATE 或 EDIT"
              }
            }

            当前是否有活跃图片任务：%s
            当前是否有参考图片：%s
            最近对话历史：%s
            已收集的需求：%s
            用户最新消息：%s
            """.formatted(
                activeTask,
                hasSourceImage,
                objectMapper.writeValueAsString(history == null ? List.of() : history),
                objectMapper.writeValueAsString(current),
                userMessage == null ? "" : userMessage);

        String reply = chatService.chat("image-task-guide-" + userId, prompt, List.of());
        JsonNode root = objectMapper.readTree(extractJson(reply));
        boolean imageTask = "IMAGE_TASK".equalsIgnoreCase(root.path("intent").asText());
        boolean ready = root.path("ready").asBoolean(false);
        ImageTaskBrief brief = root.has("brief") && root.path("brief").isObject()
            ? objectMapper.treeToValue(root.path("brief"), ImageTaskBrief.class)
            : current;
        return new ImageTaskPlanningResult(
            imageTask,
            ready,
            root.path("question").asText(""),
            root.path("finalPrompt").asText(""),
            brief);
    }

    static String extractJson(String value) {
        if (value == null) {
            throw new IllegalArgumentException("文本 Agent 没有返回任务规划结果");
        }
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("文本 Agent 返回的任务规划不是 JSON");
        }
        return value.substring(start, end + 1);
    }
}
