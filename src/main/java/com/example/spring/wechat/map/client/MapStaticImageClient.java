package com.example.spring.wechat.map.client;

import com.example.spring.wechat.image.generation.model.ImageGenerationResult;
import com.example.spring.wechat.map.model.MapPlace;
import com.example.spring.wechat.map.model.MapRouteLeg;

import java.util.List;
import java.util.Optional;

public interface MapStaticImageClient {

    Optional<ImageGenerationResult> renderRoute(
            String title,
            List<MapPlace> orderedPlaces,
            List<MapRouteLeg> legs);
}
