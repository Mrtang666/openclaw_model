from pathlib import Path
from PIL import Image, ImageDraw, ImageFont


OUT = Path(__file__).resolve().parent
FONT_PATH = Path(r"C:\Windows\Fonts\simhei.ttf")

BG = "#F6F8FB"
INK = "#172033"
MUTED = "#5E6A7D"
LINE = "#8190A5"
WHITE = "#FFFFFF"
BLUE = "#2563EB"
BLUE_SOFT = "#E8F0FF"
TEAL = "#0F766E"
TEAL_SOFT = "#DDF6F1"
AMBER = "#B45309"
AMBER_SOFT = "#FFF1D6"
RED = "#B42318"
RED_SOFT = "#FDE8E7"
PURPLE = "#6D3CC7"
PURPLE_SOFT = "#F0E8FF"
GRAY_SOFT = "#E9EDF3"


def font(size):
    return ImageFont.truetype(str(FONT_PATH), size=size)


F_TITLE = font(54)
F_SUBTITLE = font(26)
F_LAYER = font(30)
F_NODE = font(26)
F_SMALL = font(21)
F_TINY = font(18)


def rounded(draw, box, fill, outline=None, radius=18, width=2):
    draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)


def centered_text(draw, box, text, text_font=F_NODE, fill=INK, spacing=7):
    x1, y1, x2, y2 = box
    bounds = draw.multiline_textbbox((0, 0), text, font=text_font, spacing=spacing, align="center")
    tw, th = bounds[2] - bounds[0], bounds[3] - bounds[1]
    draw.multiline_text(((x1 + x2 - tw) / 2, (y1 + y2 - th) / 2), text,
                        font=text_font, fill=fill, spacing=spacing, align="center")


def node(draw, box, title, subtitle="", fill=WHITE, outline=LINE, accent=None):
    rounded(draw, box, fill, outline, 18, 2)
    x1, y1, x2, y2 = box
    if accent:
        draw.rounded_rectangle((x1, y1, x1 + 12, y2), radius=6, fill=accent)
    if subtitle:
        draw.text((x1 + 26, y1 + 18), title, font=F_NODE, fill=INK)
        draw.multiline_text((x1 + 26, y1 + 58), subtitle, font=F_SMALL, fill=MUTED, spacing=5)
    else:
        centered_text(draw, box, title)


def pill(draw, box, text, fill=GRAY_SOFT, color=INK):
    rounded(draw, box, fill, None, 22, 0)
    centered_text(draw, box, text, F_SMALL, color)


def arrow(draw, start, end, color=LINE, width=4, label=None, label_pos=None):
    sx, sy = start
    ex, ey = end
    draw.line((sx, sy, ex, ey), fill=color, width=width)
    import math
    angle = math.atan2(ey - sy, ex - sx)
    length = 16
    spread = 0.55
    p1 = (ex - length * math.cos(angle - spread), ey - length * math.sin(angle - spread))
    p2 = (ex - length * math.cos(angle + spread), ey - length * math.sin(angle + spread))
    draw.polygon([(ex, ey), p1, p2], fill=color)
    if label:
        lx, ly = label_pos or ((sx + ex) / 2, (sy + ey) / 2)
        bbox = draw.textbbox((0, 0), label, font=F_TINY)
        w, h = bbox[2] - bbox[0], bbox[3] - bbox[1]
        rounded(draw, (lx - w / 2 - 8, ly - h / 2 - 5, lx + w / 2 + 8, ly + h / 2 + 5), BG, None, 8, 0)
        draw.text((lx - w / 2, ly - h / 2), label, font=F_TINY, fill=MUTED)


def layer_band(draw, y1, y2, label, fill):
    rounded(draw, (55, y1, 2345, y2), fill, None, 22, 0)
    draw.text((82, y1 + 20), label, font=F_LAYER, fill=INK)


def save_structure():
    img = Image.new("RGB", (2400, 1800), BG)
    d = ImageDraw.Draw(img)
    d.text((70, 48), "OpenClaw 项目结构图", font=F_TITLE, fill=INK)
    d.text((72, 116), "Java 17 · Spring Boot 3.4.7 · 微信 iLink + CLI · Function Calling Agent", font=F_SUBTITLE, fill=MUTED)

    layer_band(d, 180, 390, "1  用户与入口", "#EEF3FA")
    node(d, (290, 250, 750, 350), "CLI 终端", "ConsoleRunner · 命令输入", WHITE, BLUE, BLUE)
    node(d, (970, 250, 1430, 350), "微信用户", "文本 · 图片 · 语音 · 文件", WHITE, TEAL, TEAL)
    node(d, (1650, 250, 2110, 350), "登录/管理页面", "wechat-login 静态页 · REST 控制器", WHITE, PURPLE, PURPLE)

    layer_band(d, 420, 680, "2  接入与调度", "#F1F7F6")
    node(d, (210, 500, 700, 625), "CommandDispatcher", "命令注册与分发\n/help · /weather · /wechat", WHITE, BLUE, BLUE)
    node(d, (810, 500, 1280, 625), "IlinkWechatClient", "iLink SDK 适配\n收取/转换/发送消息", WHITE, TEAL, TEAL)
    node(d, (1370, 500, 1830, 625), "WechatBotService", "Bot 生命周期 · 轮询\n等待提示 · 分段/媒体发送", WHITE, TEAL, TEAL)
    node(d, (1910, 500, 2300, 625), "消息调度器", "跨会话并行\n单会话顺序执行", WHITE, TEAL, TEAL)

    layer_band(d, 710, 990, "3  会话编排与 Agent 核心", "#F5F1FB")
    node(d, (150, 795, 570, 920), "AgentService", "CLI 普通对话\n流式输出", WHITE, BLUE, BLUE)
    node(d, (650, 770, 1200, 945), "WechatConversationService", "消息预处理 · 输入路由 · 上下文读取\n结果归档 · 记忆写入 · 回复组装", WHITE, PURPLE, PURPLE)
    node(d, (1280, 770, 1770, 945), "FunctionCallingAgentLoop", "模型调用 → tool_calls → 工具结果回传\n最多 5 轮，直到生成最终回复", WHITE, PURPLE, PURPLE)
    node(d, (1850, 770, 2280, 945), "协议与注册中心", "Schema 转换 · 参数校验\nWechatToolRegistry · legacy 回退", WHITE, PURPLE, PURPLE)

    layer_band(d, 1020, 1370, "4  业务能力模块", "#FBF6EC")
    tool_boxes = [
        ((120, 1110, 470, 1240), "聊天 / 天气", "ChatService\nAmapWeatherClient"),
        ((520, 1110, 870, 1240), "地图", "地点 · 路线 · 周边\nAmapMapClient"),
        ((920, 1110, 1270, 1240), "图片", "理解 · 生成 · 归档\n引用解析"),
        ((1320, 1110, 1670, 1240), "语音", "ASR · TTS · 音色\n格式检测/转码"),
        ((1720, 1110, 2070, 1240), "文档", "解析 · 分块 · 归档\nWord/PDF 等生成"),
        ((2120, 1110, 2320, 1240), "记忆", "MySQL\n摘要/偏好/日志"),
    ]
    for box, title, sub in tool_boxes:
        node(d, box, title, sub, WHITE, AMBER, AMBER)
    pill(d, (620, 1280, 1780, 1335), "工具实现统一遵循 WechatTool 定义，由注册中心自动收集并执行", AMBER_SOFT, AMBER)

    layer_band(d, 1400, 1725, "5  外部系统与持久化", "#F8F0F0")
    externals = [
        ((130, 1500, 500, 1645), "阿里云百炼", "文本大模型\n视觉 · 图像生成 · ASR/TTS"),
        ((570, 1500, 920, 1645), "高德开放平台", "天气 API\n地图 Web 服务"),
        ((990, 1500, 1340, 1645), "MySQL + Flyway", "会话记忆 · 工具日志\n图片/文档归档"),
        ((1410, 1500, 1760, 1645), "微信 iLink SDK", "登录 · 拉取消息\n发送文本与媒体"),
        ((1830, 1500, 2220, 1645), "本地运行环境", "文件系统 · ffmpeg\nPDFBox · Apache POI"),
    ]
    for box, title, sub in externals:
        node(d, box, title, sub, WHITE, RED, RED)

    arrow(d, (520, 350), (455, 500), BLUE)
    arrow(d, (1200, 350), (1045, 500), TEAL)
    arrow(d, (1880, 350), (1600, 500), PURPLE)
    arrow(d, (700, 562), (650, 850), BLUE)
    arrow(d, (1280, 562), (1370, 562), TEAL)
    arrow(d, (1830, 562), (1910, 562), TEAL)
    arrow(d, (2100, 625), (970, 770), TEAL, label="按会话投递", label_pos=(1660, 695))
    arrow(d, (1200, 855), (1280, 855), PURPLE)
    arrow(d, (1770, 855), (1850, 855), PURPLE)
    arrow(d, (2065, 945), (1200, 1110), AMBER, label="选择并执行工具", label_pos=(1740, 1035))
    arrow(d, (1120, 1370), (1165, 1500), RED, label="模型/数据/文件调用", label_pos=(1250, 1435))
    arrow(d, (1170, 1500), (900, 1370), LINE, label="结果返回", label_pos=(1010, 1435))

    img.save(OUT / "project-structure.png", quality=96)


def diamond(draw, center, size, text, fill=WHITE, outline=PURPLE):
    cx, cy = center
    w, h = size
    points = [(cx, cy - h / 2), (cx + w / 2, cy), (cx, cy + h / 2), (cx - w / 2, cy)]
    draw.polygon(points, fill=fill, outline=outline)
    centered_text(draw, (cx - w * .32, cy - h * .22, cx + w * .32, cy + h * .22), text, F_SMALL)


def save_flow():
    img = Image.new("RGB", (2400, 2300), BG)
    d = ImageDraw.Draw(img)
    d.text((70, 48), "OpenClaw 项目流程图", font=F_TITLE, fill=INK)
    d.text((72, 116), "从应用启动、消息接入到 Agent 工具循环与多媒体回复", font=F_SUBTITLE, fill=MUTED)

    node(d, (900, 180, 1500, 285), "AgentClawApplication 启动", "Spring 容器加载配置、Bean 与 Flyway 迁移", BLUE_SOFT, BLUE, BLUE)
    diamond(d, (1200, 410), (560, 170), "入口类型？", WHITE, BLUE)
    arrow(d, (1200, 285), (1200, 325), BLUE)

    node(d, (120, 540, 700, 670), "CLI 路径", "ConsoleRunner 读取输入\nCommandDispatcher 识别命令或普通对话", WHITE, BLUE, BLUE)
    node(d, (1700, 540, 2280, 670), "微信路径", "WechatBotService 启动 iLink 客户端\n轮询并转换 WechatIncomingMessage", WHITE, TEAL, TEAL)
    arrow(d, (920, 410), (410, 540), BLUE, label="CLI")
    arrow(d, (1480, 410), (1990, 540), TEAL, label="微信")

    diamond(d, (410, 810), (500, 160), "CLI 命令？", WHITE, BLUE)
    arrow(d, (410, 670), (410, 730), BLUE)
    node(d, (80, 950, 550, 1080), "命令处理", "/help · /version · /status\n/weather · /wechat", WHITE, BLUE, BLUE)
    node(d, (600, 950, 1070, 1080), "普通对话", "AgentService → ChatService\nDashScopeChatClient 流式输出", WHITE, BLUE, BLUE)
    arrow(d, (250, 865), (250, 950), BLUE, label="是")
    arrow(d, (570, 865), (820, 950), BLUE, label="否")
    node(d, (330, 1180, 820, 1285), "终端展示结果", "文本分片实时打印", BLUE_SOFT, BLUE, BLUE)
    arrow(d, (315, 1080), (510, 1180), BLUE)
    arrow(d, (835, 1080), (650, 1180), BLUE)

    node(d, (1650, 750, 2330, 895), "消息调度", "ConversationKey 建立会话邮箱\n不同会话并行，同一会话严格按顺序执行", TEAL_SOFT, TEAL, TEAL)
    arrow(d, (1990, 670), (1990, 750), TEAL)
    node(d, (1650, 990, 2330, 1140), "输入预处理", "语音：iLink 文本或 ASR/ffmpeg\n图片：解析/理解/归档 · 文件：检测/解析/分块\n文本：标准化并关联已有图片/文件上下文", WHITE, TEAL, TEAL)
    arrow(d, (1990, 895), (1990, 990), TEAL)

    node(d, (1360, 1240, 2040, 1385), "读取并构建会话上下文", "MySQL 记忆 + 会话摘要 + 用户偏好\n最近消息、图片引用、文档元数据", WHITE, PURPLE, PURPLE)
    arrow(d, (1990, 1140), (1700, 1240), PURPLE)
    node(d, (1360, 1480, 2040, 1630), "Function Calling 请求", "用户当前消息 + 历史上下文 + 工具 Schema\n发送给 DashScopeFunctionCallingClient", PURPLE_SOFT, PURPLE, PURPLE)
    arrow(d, (1700, 1385), (1700, 1480), PURPLE)
    diamond(d, (1700, 1780), (600, 185), "模型返回 tool_calls？", WHITE, PURPLE)
    arrow(d, (1700, 1630), (1700, 1688), PURPLE)

    node(d, (760, 1720, 1250, 1840), "校验并执行工具", "ToolCallValidator\nWechatToolRegistry.execute", WHITE, AMBER, AMBER)
    arrow(d, (1400, 1780), (1250, 1780), AMBER, label="是")
    node(d, (230, 1685, 670, 1875), "业务工具/外部服务", "聊天 · 天气 · 地图\n图片理解/生成 · 语音 ASR/TTS/音色\n文档解析/生成 · 记忆服务", AMBER_SOFT, AMBER, AMBER)
    arrow(d, (760, 1780), (670, 1780), AMBER)
    arrow(d, (450, 1685), (620, 1535), RED, label="百炼 / 高德 / MySQL / iLink / 文件系统", label_pos=(435, 1570))
    node(d, (430, 1400, 1040, 1535), "工具结果转为 tool message", "记录成功/失败日志，收集图片、语音、文件等可见结果", WHITE, AMBER, AMBER)
    arrow(d, (450, 1875), (650, 1535), AMBER)
    arrow(d, (1040, 1467), (1360, 1555), PURPLE, label="回传模型，进入下一轮", label_pos=(1190, 1485))

    node(d, (1430, 1960, 1970, 2080), "组装最终 WechatReply", "文本 + 图片 + 语音 + 文件\n去重媒体并保持输出顺序", WHITE, TEAL, TEAL)
    arrow(d, (1700, 1872), (1700, 1960), TEAL, label="否 / 达到轮次上限")
    node(d, (1430, 2160, 1970, 2260), "持久化会话结果", "用户消息 · 助手回复 · 状态 · 偏好 · 工具日志", WHITE, RED, RED)
    node(d, (2020, 1960, 2350, 2260), "发送给微信用户", "按顺序发送\n文本分段\n图片重试\n语音/文件降级", TEAL_SOFT, TEAL, TEAL)
    arrow(d, (1700, 2080), (1700, 2160), RED)
    arrow(d, (1970, 2020), (2020, 2020), TEAL)

    pill(d, (870, 2180, 1320, 2240), "默认最多 5 轮工具调用", PURPLE_SOFT, PURPLE)

    img.save(OUT / "project-flow.png", quality=96)


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    save_structure()
    save_flow()
    print(OUT / "project-structure.png")
    print(OUT / "project-flow.png")
