package com.example.spring.task;

import com.example.spring.memory.MemoryMessage;
import java.util.List;

public interface ImageTaskPlanner {
    ImageTaskPlanningResult plan(
        String userId,
        String userMessage,
        ImageTaskBrief existingBrief,
        boolean activeTask,
        boolean hasSourceImage,
        List<MemoryMessage> history) throws Exception;
}
