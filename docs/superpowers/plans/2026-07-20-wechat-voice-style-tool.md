# 微信音色修改工具 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改变现有微信 Agent 工具调用框架的前提下，新增一个微信端音色修改工具，让用户可以筛选、试听、确认并在内存中保存个人 TTS 音色偏好。

**Architecture:** 新增 `voice_style` 工具处理音色选择流程；新增内存型 `VoicePreferenceService` 保存用户当前音色、候选列表和最近试听音色；扩展 `VoiceSynthesisService` 支持按指定 voice 合成，`VoiceSynthesisWechatTool` 根据用户 ID 读取已保存音色。现有工具注册中心和任务规划流程保持不变。

**Tech Stack:** Java 17、Spring Boot、JUnit 5、Mockito、AssertJ、阿里百炼 `qwen3-tts-flash` TTS。

---

### Task 1: 音色偏好和音色目录

**Files:**
- Create: `src/main/java/com/example/spring/wechat/voice/style/model/VoiceProfile.java`
- Create: `src/main/java/com/example/spring/wechat/voice/style/model/VoiceCandidatePage.java`
- Create: `src/main/java/com/example/spring/wechat/voice/style/service/VoiceCatalog.java`
- Create: `src/main/java/com/example/spring/wechat/voice/style/service/VoicePreferenceService.java`
- Test: `src/test/java/com/example/spring/wechat/voice/style/service/VoicePreferenceServiceTests.java`

- [ ] 写测试：按“温柔女声”筛选时返回 3-5 个候选，默认包含官方 voice 值。
- [ ] 实现官方音色目录，音色数据固定在代码中，后续可替换成 API/配置来源。
- [ ] 写测试：保存用户音色后，按 userId 可以读取；未保存时返回默认 `Cherry`。
- [ ] 写测试：试听音色 5 分钟内有效，过期后不能用“就这个”确认。

### Task 2: 语音合成支持用户音色

**Files:**
- Modify: `src/main/java/com/example/spring/wechat/voice/synthesis/service/VoiceSynthesisService.java`
- Modify: `src/main/java/com/example/spring/wechat/voice/synthesis/service/DefaultVoiceSynthesisService.java`
- Modify: `src/main/java/com/example/spring/wechat/conversation/tools/VoiceSynthesisWechatTool.java`
- Test: `src/test/java/com/example/spring/wechat/voice/synthesis/service/DefaultVoiceSynthesisServiceTests.java`
- Test: `src/test/java/com/example/spring/wechat/conversation/tools/VoiceWechatToolTests.java`

- [ ] 写测试：指定 voice 后，TTS 请求使用指定音色。
- [ ] 增加 `synthesizeForWechat(String text, String voice)`，旧方法继续存在并走默认音色。
- [ ] 在 `VoiceSynthesisWechatTool` 中根据 sessionKey 查询用户保存的音色。

### Task 3: 新增 voice_style 工具

**Files:**
- Create: `src/main/java/com/example/spring/wechat/conversation/tools/VoiceStyleWechatTool.java`
- Modify: `src/main/java/com/example/spring/tool/protocol/ToolCallPlanner.java`
- Test: `src/test/java/com/example/spring/wechat/conversation/tools/VoiceStyleWechatToolTests.java`
- Test: `src/test/java/com/example/spring/tool/protocol/ToolCallPlannerTests.java`

- [ ] 写测试：用户说“修改音色”时，工具追问更具体偏好。
- [ ] 写测试：用户说“换一个温柔的女声”时，展示候选音色。
- [ ] 写测试：用户说“试听第一个”时，返回语音试听片段并记录最近试听音色。
- [ ] 写测试：用户说“选第二个”或 5 分钟内“就用这个”时，保存音色并文本确认。
- [ ] 在任务规划提示词中加入 `voice_style` 调用规则。

### Task 4: 文档与验证

**Files:**
- Modify: `README.md`
- Modify: `docs/PROJECT_STRUCTURE.md`

- [ ] 补充音色修改工具使用方式。
- [ ] 运行 `mvn -q test`。
- [ ] 运行编码健康扫描，确认没有乱码。
