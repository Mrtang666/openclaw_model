# 微信 iLink 多 Agent 助手

Spring Boot 应用通过微信 iLink SDK 接收消息，根据内容自动调用对话、天气、
图片识别或图片生成模块。项目只允许启动一个微信客户端和一个机器人，收到消息后
会先回复处理状态，再由同一个机器人线程严格按照接收顺序逐条处理。

## 功能

- 普通文本：调用阿里云百炼 `qwen-plus` 多轮对话
- 微信图片：下载原图并调用百炼 `qwen3-vl-plus` 识别
- 图片 URL：安全下载图片后调用视觉模型识别
- 实时天气：通过和风天气查询城市、区、县
- 图片生成：调用百炼图像生成任务，完成后直接发送微信图片
- 历史图片编辑：使用 `qwen-image-2.0` 修改上一张或之前生成的图片
- 引导式图片任务：文本 Agent 主动补充关键需求，再把整理后的提示词交给图片 Agent
- 任务恢复：未完成的图片需求保存在 SQLite 中，应用重启后 24 小时内可以继续
- 微信输入状态：处理和回复期间顶部显示微信标准的“对方正在输入中...”
- 持久化记忆：SQLite 保存受限文本历史，图片文件使用数量和容量限制
- 其他消息：文件、视频和无法转写的语音也会收到明确反馈

## 外部配置

复制配置模板：

```powershell
Copy-Item .evn.example .evn
```

在本地 `.evn` 中填写：

```properties
BAILIAN_API_KEY=你的百炼APIKey
BAILIAN_COMPATIBLE_BASE_URL=https://你的WorkspaceId.cn-beijing.maas.aliyuncs.com/compatible-mode/v1
BAILIAN_CHAT_MODEL=qwen-plus
BAILIAN_VISION_MODEL=qwen3-vl-plus
BAILIAN_IMAGE_MODEL=wanx-v1
BAILIAN_IMAGE_EDIT_MODEL=qwen-image-2.0

WEATHER_API_KEY=你的和风天气APIKey
WEATHER_API_HOST=https://你的和风天气专属APIHost
```

百炼的 API Key 与 Base URL 必须属于同一地域。北京地域的新接口通常使用带
`WorkspaceId` 的工作空间专属地址，请以百炼控制台展示的地址为准。

和风天气的 API Key 与专属 API Host 也必须配套使用，并开通 GeoAPI 与实时天气权限。

真实 `.evn` 已加入 `.gitignore`。日志只输出模型名称、接口主机和 Key 是否存在，
不会输出 Key 内容。

## 记忆与存储限制

默认配置针对本地快速运行：

- SQLite 只保存最近 40 条结构化消息和图片索引
- 发送给模型时只读取最近 12 条
- 每个用户最多保存 12 张图片
- 每个用户图片总量最多 50MB
- 图片保存在 `runtime-data/images`，数据库位于 `runtime-data/memory.db`
- `runtime-data` 已被 Git 忽略

可以通过 `MEMORY_MAX_ENTRIES_PER_USER`、`MEMORY_PROMPT_ENTRIES`、
`MEMORY_MAX_IMAGES_PER_USER` 和 `MEMORY_MAX_IMAGE_BYTES_PER_USER` 调整。

收到“上一张图片是什么”时会读取历史图片进行识别；收到“把上一张改成夜景”时
会调用千问图像编辑模型，并把编辑结果保存为新的历史图片。

图片需求不完整时，机器人每次只询问一到两个关键问题。用户后续回复“方形”、
“科技感”或“用于宣传图”等内容会继续补充同一任务；需求完整后，文本 Agent 会
生成结构化提示词并交给图片生成 Agent。回复“取消”可以结束当前图片任务。
图片生成完成后，用户可以直接说“去除里面的人群”“画面干净一点”或“换成暖色调”；
系统会结合最近的图片记录和对话历史，由文本 Agent 判断是否继续编辑上一张图片。
图片结果先发送完成说明，再发送图片；明确的图片发送异常会自动重试一次。微信 iLink
SDK 当前不提供被引用消息的 ID 或原始内容，因此引用回复
会结合最近文本和最近图片推断；需要处理较早内容时，应在文字中说明“第几张”或重新发送。

机器人发送状态消息和最终文字回复前会使用 iLink SDK 的输入状态接口，微信顶部会
显示客户端固定文案“对方正在输入中...”。图片生成期间仍保留原有状态消息；微信
协议不支持把顶部文案自定义为“图片出炉中...”。可通过以下外部配置调整：

```properties
ILINK_BOT_TYPING_INDICATOR_ENABLED=true
ILINK_BOT_TYPING_PREVIEW_DELAY=600ms
```

## IDEA 运行

使用项目中的共享运行配置 `DemoApplication`。程序会输出微信扫码登录入口，并调用
系统默认浏览器打开入口。扫码成功后即可在微信中发送消息。

共享运行配置已包含 `--enable-native-access=ALL-UNNAMED`，用于允许 SQLite JDBC
在 Java 25 中加载本地库。请使用该配置启动，避免出现 restricted method WARNING。

机器人使用用户目录下的单实例文件锁。不要同时运行 IDEA 中的 `DemoApplication`
和命令行 JAR；第二个实例会检测到已有机器人并停止微信连接，避免两个 iLink 客户端
互相挤掉登录会话。锁文件默认位于：

```text
%USERPROFILE%\.openclaw-model\wechat-ilink.lock
```

如果从 `DemoApplication.java` 左侧绿色三角直接运行，配置也会从以下位置自动查找：

- 当前工作目录的 `.evn`
- 上级目录的 `.evn`
- 外层项目中的 `openclaw_model-main/.evn`

## 命令行运行

在仓库根目录执行：

```powershell
mvn clean package
java -jar openclaw_model-main\target\spring-startup-logging-0.0.1-SNAPSHOT.jar
```

只验证配置和 Spring 上下文，不连接微信：

```powershell
java -jar openclaw_model-main\target\spring-startup-logging-0.0.1-SNAPSHOT.jar --ilink.bot.enabled=false
```

## 对话示例

```text
你好，请介绍一下你自己
查询江苏无锡滨湖区的实时天气
帮我识别这张图片：https://example.com/photo.png
画一张雨夜江南古镇的水彩插画
按照我发的图片内容生成一张插画
帮我做一张产品宣传图
取消
```

## Voice recognition

Voice messages with a transcript supplied by iLink are used directly. If the
transcript is blank, the bot downloads the temporary audio and sends it to the
Alibaba Cloud Bailian compatible speech endpoint. The recognized text then
enters the same AgentRouter path as a normal text message, so it can trigger
chat, weather, image understanding, image generation, or image editing.

Add these values to the local `.evn` file when voice fallback recognition is
needed (the file is ignored by Git):

```properties
SPEECH_ENABLED=true
SPEECH_API_KEY=your-bailian-api-key
SPEECH_COMPATIBLE_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
SPEECH_MODEL=qwen-audio-turbo
SPEECH_MAX_DURATION_SECONDS=60
SPEECH_MAX_BYTES=10485760
```

WeChat commonly sends SILK audio. If the downloaded payload starts with a SILK
header, configure an executable decoder whose command shape is
`decoder input.silk output.wav`:

```properties
SPEECH_SILK_DECODER_PATH=C:/tools/silk-decoder.exe
```

Audio is held in temporary files only during decoding and is not written to
conversation memory. Only the resulting transcript is persisted. The model
name and endpoint must match the models enabled in the Bailian account.

## Voice reply mode

Send `开启语音对话` or `以后请用语音回复` to enable voice replies for the
current WeChat user. Send `关闭语音对话` or `恢复文字回复` to switch back.
The setting is stored per user in SQLite and survives application restarts.

When voice mode is enabled, the final text answer is synthesized with Bailian
TTS and sent through iLink `sendVoice`. The existing status messages remain,
and `对方正在说话中...` is sent immediately before synthesis/send. Generated
or edited images are still sent as image messages after the voice explanation.
If TTS is unavailable or fails, the bot automatically falls back to text.

Configure the TTS model and endpoint in the local `.evn` file:

```properties
SPEECH_TTS_ENABLED=true
SPEECH_TTS_API_KEY=your-bailian-api-key
SPEECH_TTS_ENDPOINT=
SPEECH_TTS_MODEL=qwen3-tts-flash
SPEECH_TTS_VOICE=
SPEECH_TTS_FORMAT=wav
SPEECH_TTS_SAMPLE_RATE=16000
SPEECH_SILK_ENCODER_PATH=C:/tools/silk-encoder.exe
```

The encoder must accept `encoder input.wav output.silk` and produce a valid
SILK V3 file. iLink marks voice uploads as `encode_type=6`, so uploading the
raw WAV returned by TTS will not create a playable WeChat voice bubble. Voice
answers are split at sentence boundaries when a generated segment exceeds 60
seconds. If encoding, validation, or upload fails, the original answer is
sent as text with the failure reason.

The current iLink Bot protocol accepts `sendVoice` without an error but WeChat
does not render Bot voice bubbles. This behavior is also confirmed in upstream
SDK issues 9 and 11. The application therefore keeps the voice-bubble request
for forward compatibility and also sends each synthesized segment as a compact
MP3 file so the user always receives playable audio without large WAV uploads.

The default endpoint targets Bailian native multimodal generation. If the
account exposes a different TTS endpoint or model, set `SPEECH_TTS_ENDPOINT`
and `SPEECH_TTS_MODEL` accordingly.

## Voice selection

Voice selection is opt-in. Ordinary conversation never opens the selection
questions and uses the configured default voice. Send an explicit request such
as `修改音色`, `换声音`, `换成温柔女声`, or `想用男声` to start the guided flow.

The bot asks only for missing criteria, recommends up to 10 compatible Bailian
Qwen3-TTS voices by local recommendation weight, and accepts a list number,
Chinese display name, or Bailian voice ID. Send `试听3`, `试听苏瑶`, or a
corresponding voice ID to generate a temporary audio preview without changing
the saved voice. The selected voice is stored per
WeChat user in SQLite and remains active after reconnects and application
restarts. Send `查看当前音色` to inspect it or `恢复默认音色` to remove the
user override. Selecting a voice does not automatically enable voice reply
mode; it is applied whenever voice reply mode is active.

## Read-aloud requests and ordered voice delivery

Requests such as `朗读下面的文字：...`, `帮我念一下这段内容`, or
`朗读上面的回复` produce a one-time voice response even when voice reply mode
is disabled. After a successful one-time reading, the bot asks whether voice
reply mode should be enabled. A substantive new question dismisses that offer
and is processed normally.

Arabic and Chinese ordinal forms are accepted throughout voice selection,
including `3`, `第3个`, `第三个`, `选择第3项`, and `试听第三个`.

Multiple voice segments are delivered strictly in order. The bot retries a
failed voice bubble or MP3 packet before moving to the next segment, with a
configurable delay between successful segments. When immediate retries are
exhausted, the failed and remaining audio files are stored under
`runtime-data/pending-voice-replies` and retried after the iLink connection is
restored. Successfully delivered packet types are not intentionally resent.
After every segment and its MP3 fallback have succeeded, the bot sends a final
text confirmation containing the number of completed segments. Text messages
also retry with plain `sendText`, avoiding the extra prepare calls made by
`sendTextWithTyping` during congested voice delivery.

```properties
SPEECH_VOICE_SEND_INTERVAL=2500ms
SPEECH_VOICE_RETRY_MAX_ATTEMPTS=5
SPEECH_VOICE_RETRY_BASE_DELAY=1500ms
SPEECH_VOICE_RETRY_MAX_DELAY=10s
```

## 测试

测试使用本地模拟服务，不调用真实百炼或和风天气 API：

```powershell
mvn test
```

配置好本地 `.evn` 后，可手动执行真实接口冒烟测试。该命令会实际调用一次对话、
天气、视觉、图片生成和图片编辑接口，可能产生少量 API 费用：

```powershell
mvn "-Dlive.api.tests=true" "-Dtest=LiveApiIntegrationTests" test
```
