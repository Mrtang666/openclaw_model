package com.example.spring.wechat.conversation.tools;

import com.example.spring.wechat.bot.WechatReply;
import com.example.spring.xhs.ingestion.XhsCollectionCoordinator;
import com.example.spring.xhs.source.XhsCollectionRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "xhs.collector", name = "enabled", havingValue = "true")
public class XhsCollectWechatTool implements WechatTool {

    private final XhsCollectionCoordinator coordinator;

    public XhsCollectWechatTool(XhsCollectionCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Override
    public String name() {
        return "xhs_monitor_collect";
    }

    @Override
    public String description() {
        return "为已有小红书舆情项目提交一次关键词采集任务，只采集和分析，不发布内容。";
    }

    @Override
    public List<String> arguments() {
        return List.of("project_key", "project_name", "query", "limit");
    }

    @Override
    public List<WechatToolParameter> parameters() {
        return List.of(
                WechatToolParameter.requiredString("project_key", "舆情项目唯一标识", "brand-a"),
                WechatToolParameter.optionalString("project_name", "项目显示名称", "品牌 A"),
                WechatToolParameter.requiredString("query", "小红书搜索关键词", "品牌 A"),
                WechatToolParameter.optionalString("limit", "采集数量，1-100，默认 20", "20"));
    }

    @Override
    public WechatToolCapability capability() {
        return new WechatToolCapability(
                "提交合规数据侧车的小红书关键词采集任务。",
                List.of("不发布、删除、私信或操作小红书账号；只返回任务 ID，不假装采集已经完成。"),
                List.of("需要 project_key 和 query。"),
                List.of("返回本地采集任务 ID，结果由后台轮询后入库。"));
    }

    @Override
    public WechatReply execute(WechatToolRequest request) {
        try {
            String projectKey = request.argument("project_key");
            String projectName = request.argument("project_name");
            String jobId = coordinator.start(new XhsCollectionRequest(
                    projectKey, projectName, request.argument("query"), parseInt(request.argument("limit"), 20), ""));
            return WechatReply.text("小红书舆情采集任务已提交：job_id=" + jobId + "。任务将在后台采集并分析。");
        } catch (RuntimeException exception) {
            return WechatReply.text("小红书舆情采集任务提交失败：" + safeMessage(exception));
        }
    }

    private int parseInt(String value, int fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value.strip());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String safeMessage(Throwable throwable) {
        String message = throwable == null ? "" : throwable.getMessage();
        return message == null || message.isBlank() ? "未知错误" : message;
    }
}
