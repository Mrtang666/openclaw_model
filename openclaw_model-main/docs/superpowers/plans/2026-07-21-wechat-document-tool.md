# 微信端文档解析与文档生成工具实现计划

> **供执行使用：** 本计划按“小步实现 + 测试验证”的方式推进；本次不提交 Git。

**目标：** 在不改变微信端 Agent 主流程的前提下，新增可作为工具调用的文件解析与文档生成功能。

**架构：** iLink 适配层把普通文件转成 `WechatIncomingFile`，会话层把文件写入上下文并交给工具中心。文档模块负责类型识别、解析、切块、生成文件，回复层负责把生成文件发回微信。

**技术栈：** Java 17、Spring Boot、JDBC/MySQL、Apache PDFBox、Apache POI、OpenPDF、JUnit 5。

---

## 任务 1：补齐文件消息模型和回复模型

- 新增 `WechatIncomingFile`，包含文件名、MIME、字节、大小、哈希、来源。
- 扩展 `WechatIncomingMessage`，支持 `files` 和 `hasFiles()`。
- 扩展 `WechatReply.Part`，支持文件型回复。
- 扩展 `WechatBotService`，文件型回复走 `WechatClient.sendFile()`。

## 任务 2：实现文档识别与解析模块

- 新增 `wechat/document` 包。
- 实现 `DocumentTypeDetector`。
- 实现 PDF、DOCX、TXT、MD、XLSX、PPTX 解析器。
- 实现分块服务，避免大文件直接进入模型。

## 任务 3：实现文档上下文与本地存储

- 原始文件保存到 `data/wechat/documents`。
- MySQL 新增文件元数据、分块、生成记录表。
- 内存兜底里保存最近文件摘要，保证当前进程可连续追问。

## 任务 4：实现文档分析工具和文档生成工具

- 新增 `DocumentAnalysisWechatTool`。
- 新增 `DocumentGenerationWechatTool`。
- 生成 DOCX、PDF、TXT、MD。
- 工具参数由大模型结构化拆解结果传入。

## 任务 5：接入微信会话编排

- 文件单独发送时，回复“已收到文件 + 可选处理建议”。
- 文件 + 明确需求时，直接进入任务拆解。
- 用户后续说“总结刚才的文件”“生成 Word”等时，读取最近文件上下文。
- 工具中心新增文档工具定义。

## 任务 6：验证

- 单元测试覆盖文件类型识别、TXT/MD 解析、分块、文档生成、文件型回复。
- 编译验证：`mvn test`。
- 不提交仓库。

