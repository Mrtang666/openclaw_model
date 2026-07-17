package com.example.spring.agent;

import com.example.spring.bailian.BailianImageGenerationService;
import com.example.spring.bailian.BailianImageEditService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ImageGenerationAgent implements ModuleAgent {
    private final BailianImageGenerationService generationService;
    private final BailianImageEditService editService;

    public ImageGenerationAgent(
        BailianImageGenerationService generationService,
        BailianImageEditService editService) {
        this.generationService = generationService;
        this.editService = editService;
    }

    @Override
    public AgentType type() {
        return AgentType.IMAGE_GENERATION;
    }

    @Override
    public AgentResponse execute(AgentRequest request) throws Exception {
        List<ImageAsset> sourceImages = new ArrayList<>(request.images());
        if (sourceImages.isEmpty()) {
            sourceImages.addAll(request.referencedImages());
        }
        if (!sourceImages.isEmpty()) {
            ImageAsset edited = editService.edit(request.text(), sourceImages);
            return AgentResponse.image("图片已根据历史内容完成修改。", edited);
        }
        ImageAsset generated = generationService.generate(request.text());
        return AgentResponse.image("图片已生成。", generated);
    }
}
