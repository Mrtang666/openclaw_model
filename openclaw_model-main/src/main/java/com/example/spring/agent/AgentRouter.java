package com.example.spring.agent;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class AgentRouter {
    private static final Pattern IMAGE_URL = Pattern.compile(
        "(?i)https?://\\S+?\\.(?:png|jpe?g|webp|gif)(?:\\?\\S*)?");
    private static final Pattern WEB_URL = Pattern.compile("(?i)https?://\\S+");
    private static final Pattern IMAGE_HINT = Pattern.compile(
        "(?i)(图片|图像|照片|识别|分析|看看|看一下|image|photo|picture)");
    private static final Pattern IMAGE_GENERATION = Pattern.compile(
        "(?i)(画一|绘画|画图|生成.{0,12}(图片|图像|照片|风景照|海报|插画|头像|壁纸|封面)|"
            + "制作.{0,12}(图片|图像|照片|风景照|海报|插画|头像|壁纸|封面)|"
            + "修改|改成|改为|加工|编辑|调整|换成|变成|添加|删除|去掉|保留|"
            + "去除|移除|擦除|消除|删掉|抹掉|"
            + "优化|美化|润色|修复|重绘|重做|"
            + "调亮|调暗|亮一点|暗一点|更亮|更暗|夜景|白天|清晰|模糊|放大|缩小|"
            + "移到|放到|做成|增强|减弱|"
            + "create.{0,4}image|draw.{0,4}image|edit.{0,4}image)");
    private static final Pattern REQUESTED_IMAGE_CREATION = Pattern.compile(
        "(?i)(想要|需要|给我|来一张|来个|做一张|做一个|帮我做|帮我制作)"
            + ".{0,24}(图片|图像|照片|风景照|海报|插画|头像|壁纸|封面)");
    private static final Pattern IMAGE_VIEW_ONLY = Pattern.compile(
        "(?i)(看看|看一下|查看|识别|分析|描述|是什么|有什么|里面|内容)");
    private static final Pattern WEATHER = Pattern.compile(
        "(?i)(天气|气温|温度|多少度|湿度|风速|weather|temperature)");
    private static final Pattern DOCUMENT = Pattern.compile(
        "(?i)(文件|文档|附件|PDF|Word|DOCX|Excel|XLSX|PPT|PPTX|CSV|RTF|"
            + "输出.{0,8}(PDF|Word|DOCX)|导出.{0,8}(PDF|Word|DOCX)|"
            + "(上文|刚才.{0,8}(回答|回复|结果)|生成结果).{0,12}(输出|导出|转成|转换成))");

    public AgentPlan route(AgentRequest request) {
        String text = request.text().toLowerCase(Locale.ROOT);
        boolean generation = IMAGE_GENERATION.matcher(text).find()
            || (REQUESTED_IMAGE_CREATION.matcher(text).find()
                && !IMAGE_VIEW_ONLY.matcher(text).find());
        boolean vision = request.hasImages()
            || IMAGE_URL.matcher(text).find()
            || (WEB_URL.matcher(text).find() && IMAGE_HINT.matcher(text).find());
        boolean historicalImage = request.hasReferencedImages();

        if (request.hasDocuments() || request.hasReferencedDocuments()
            || DOCUMENT.matcher(text).find()) {
            return new AgentPlan(List.of(AgentType.DOCUMENT));
        }

        if (generation && vision && !request.hasImages()) {
            return new AgentPlan(List.of(AgentType.VISION, AgentType.IMAGE_GENERATION));
        }
        if (generation && request.hasImages()) {
            return new AgentPlan(List.of(AgentType.VISION, AgentType.IMAGE_GENERATION));
        }
        if (generation) {
            return new AgentPlan(List.of(AgentType.IMAGE_GENERATION));
        }
        if (vision || historicalImage) {
            return new AgentPlan(List.of(AgentType.VISION));
        }
        if (WEATHER.matcher(text).find()) {
            return new AgentPlan(List.of(AgentType.WEATHER));
        }
        return new AgentPlan(List.of(AgentType.CHAT));
    }

    public String processingMessage(AgentPlan plan) {
        return switch (plan.primaryType()) {
            case WEATHER -> "正在查询该地区的实时天气，请稍等。";
            case VISION -> "图片已收到，正在识别，请稍等。";
            case IMAGE_GENERATION -> "正在生成图片，可能需要一点时间。";
            case DOCUMENT -> "正在理解你的内容与文件输出需求，请稍等。";
            case CHAT -> "我正在思考，请稍等。";
        };
    }
}
