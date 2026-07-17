package com.example.spring.agent;

import com.example.spring.bailian.BailianVisionService;
import com.example.spring.media.RemoteImageLoader;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class VisionAgent implements ModuleAgent {
    private final BailianVisionService visionService;
    private final RemoteImageLoader imageLoader;

    public VisionAgent(BailianVisionService visionService, RemoteImageLoader imageLoader) {
        this.visionService = visionService;
        this.imageLoader = imageLoader;
    }

    @Override
    public AgentType type() {
        return AgentType.VISION;
    }

    @Override
    public AgentResponse execute(AgentRequest request) throws Exception {
        List<ImageAsset> images = new ArrayList<>(request.images());
        images.addAll(request.referencedImages());
        images.addAll(imageLoader.loadImagesFromText(request.text()));
        if (images.isEmpty()) {
            return AgentResponse.text("没有找到可识别的图片，请直接发送图片或有效的图片 URL。");
        }
        return AgentResponse.text(visionService.analyze(request.text(), images));
    }
}
