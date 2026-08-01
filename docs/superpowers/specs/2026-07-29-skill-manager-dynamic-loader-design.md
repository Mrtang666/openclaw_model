# SkillManager 动态加载器设计

## 背景

OpenClaw 当前已经在仓库根目录维护了 `skills/*` 形式的 Skill 资源，例如 `skills/meituan-travel` 和 `skills/wechat-food-ordering`。`pom.xml` 也已经把根目录 `skills` 复制到构建产物的 `skills` classpath 路径下，因此运行时可以沿用现有目录，不需要迁移到 `src/skills`。

项目里已有两套相似的动态注册模式：

- `WechatToolRegistry` 通过 Spring 收集 `List<WechatTool>`，按工具名注册，不需要 if-else。
- `ToolRegistry` 通过 Spring 收集 `List<AgentTool>`，按工具名注册，不需要 if-else。

SkillManager 应该延续这种风格，但它不应该替代工具注册器。`WechatToolRegistry` 负责执行工具，SkillManager 负责加载和提供业务 Skill 指令，让模型知道什么时候、如何、安全地使用这些工具。

## 目标

- 沿用现有根目录 `skills/*` 作为 Skill 源目录。
- 设计明确的 Skill 文件协议和 Java 接口协议。
- 实现运行时 SkillManager，启动时自动扫描 `skills/*/SKILL.md` 并注册。
- 禁止通过 if-else 或 switch 对具体 Skill 名进行硬编码注册。
- 为 Agent Prompt 提供动态 Skill 上下文，逐步替换 `FunctionCallingAgentLoop.SYSTEM_PROMPT` 中的业务规则硬编码。
- 保留现有 `WechatToolRegistry` 和 `WechatTool` 执行体系。
- 加入结构校验、重复检测、错误日志和单元测试。

## 非目标

- 不把 Skill 设计成新的工具执行框架。
- 不在第一版支持热加载。
- 不在第一版执行 `scripts/` 目录中的脚本。
- 不在第一版递归读取所有 `references/` 正文。
- 不迁移现有 Skill 目录。
- 不重写 `FunctionCallingAgentLoop` 的完整模型循环。
- 不把所有 Skill 全量注入每一次模型请求。

## 推荐方案

采用 “SkillManager + Prompt 注入” 方案。

SkillManager 在应用启动时扫描 `skills/*`，解析每个 Skill 的 `SKILL.md` frontmatter 和正文，形成不可变注册表。Agent 运行时根据工具名或显式 Skill 名获取相关 Skill 指令，并拼接进模型上下文。

边界划分：

```text
SkillManager
  负责：扫描、解析、校验、注册、查询、渲染 Skill 上下文

WechatToolRegistry
  负责：注册和执行 Java 工具 Bean

FunctionCallingAgentLoop / ToolCallPlanner
  负责：把 Skill 上下文提供给模型，让模型选择和使用工具
```

新增 Skill 的路径应该是：

```text
skills/new-skill-name/SKILL.md
```

只要文件符合协议，应用重启后自动注册，不需要修改 Java 注册代码。

## Skill 文件协议

每个 Skill 目录至少包含：

```text
skills/<skill-name>/
└─ SKILL.md
```

可选资源：

```text
skills/<skill-name>/
├─ agents/openai.yaml
├─ references/*.md
├─ assets/*
├─ scripts/*
└─ *.json
```

`SKILL.md` 必须以 YAML frontmatter 开头：

```yaml
---
name: meituan-travel
description: 用户询问酒店、机票、火车票、景点门票、度假和行程规划时使用。
---
```

协议规则：

- `name` 必填。
- `description` 必填且不能为空。
- `name` 必须和目录名一致。
- `name` 只能包含小写字母、数字和连字符。
- `SKILL.md` 正文不能为空。
- `SKILL.md` 建议少于 500 行。
- `references/` 文件启动时只记录路径，不默认读取正文。
- `agents/openai.yaml` 不参与运行时触发，只作为 UI 元数据保留。

非法情况：

- 缺少 `SKILL.md` 的目录可以跳过，并记录 debug 日志。
- 缺少 frontmatter、缺少必填字段、重复 `name`、`name` 与目录名不一致，应启动失败。
- 单个 Skill 解析失败不应被静默吞掉，避免模型拿到不完整协议后产生危险行为。

## Java 接口协议

新增包：

```text
src/main/java/com/example/spring/skill
```

核心接口：

```java
public interface SkillManager {
    List<SkillDefinition> list();

    Optional<SkillDefinition> findByName(String name);

    List<SkillDefinition> findByToolNames(Collection<String> toolNames);

    String renderSkillContext(Collection<String> skillNames);
}
```

核心模型：

```java
public record SkillDefinition(
        String name,
        String description,
        String body,
        Path directory,
        List<SkillReference> references
) {
}
```

```java
public record SkillReference(
        String relativePath,
        Path path
) {
}
```

解析结果内部模型：

```java
record ParsedSkillMarkdown(
        Map<String, String> frontmatter,
        String body
) {
}
```

异常：

```java
public class SkillLoadException extends RuntimeException {
}
```

## 动态加载器

实现类：

```java
@Component
public class FileSystemSkillManager implements SkillManager {
}
```

配置：

```properties
agent.skills.enabled=true
agent.skills.path=skills
agent.skills.max-context-skills=3
```

启动流程：

```text
应用启动
-> 解析 agent.skills.path
-> 优先从文件系统路径读取
-> 如果文件系统路径不存在，再尝试 classpath:/skills
-> 遍历 skills/* 一级子目录
-> 读取每个目录的 SKILL.md
-> 解析 frontmatter 和 body
-> 校验协议
-> 收集 references/*
-> 注册到 Map<String, SkillDefinition>
-> 暴露只读查询接口
```

扫描限制：

- 只扫描 `skills` 下一级子目录。
- 不递归把嵌套目录当成 Skill。
- 不根据具体 Skill 名写任何 if-else。
- 注册表使用不可变 Map，避免运行期被外部修改。

路径处理：

- 本地开发时使用 `Path.of("skills")`。
- 打包运行时可读取 classpath 下的 `skills` 资源。
- 第一版如 classpath 资源不能枚举 jar 内目录，可先保证文件系统路径可用，并在后续实现 jar 内资源扫描；测试覆盖当前 Maven 项目运行方式。

## Skill 与工具的关联

第一版需要让 Agent 能根据工具选择相关 Skill。推荐采用显式、可测试的轻量映射策略，但不硬编码具体业务名。

优先顺序：

1. 如果 Skill 目录存在 `skill.json`，读取其中的 `tools` 字段。
2. 如果没有 `skill.json`，从 `SKILL.md` 正文中识别反引号包裹的工具名，例如 `` `meituan_travel` ``、`` `food_delivery` ``。
3. 如果仍没有关联工具，只作为可枚举 Skill 保留，不自动注入到工具上下文。

可选 `skill.json`：

```json
{
  "tools": ["meituan_travel"]
}
```

这个文件不是第一版必须迁移的内容。已有 Skill 可以继续依赖正文中的工具名识别，后续需要更稳定时再补 `skill.json`。

## Prompt 接入

第一阶段接入 `FunctionCallingAgentLoop`。

当前 `FunctionCallingAgentLoop.SYSTEM_PROMPT` 中存在业务规则，例如酒店旅行、外卖下单等专用规则。后续应把这些规则移出 Java 字符串，改由 SkillManager 动态提供。

推荐结构：

```java
String systemPrompt = BASE_SYSTEM_PROMPT
        + System.lineSeparator()
        + skillManager.renderSkillContext(selectedSkillNames)
        + System.lineSeparator()
        + RAG_SYSTEM_RULES;
```

Skill 上下文渲染格式：

```text
[Skill: meituan-travel]
description: ...

instructions:
...
```

选择策略：

- 如果模型已经返回工具调用，执行工具前可根据工具名注入相关 Skill。
- 第一版更简单：在第一轮模型请求前，根据可用工具定义和工具关联表，最多注入 `agent.skills.max-context-skills` 个相关 Skill。
- 对当前已有两个业务 Skill，可以先注入与已注册工具有关的 Skill；没有关联工具的 Skill 不注入完整正文。
- 工具规划阶段可以只使用 Skill 的 `name + description`，完整正文留给 Agent Loop。

上下文控制：

- 默认最多注入 3 个 Skill。
- 单个 Skill body 可按最大字符数裁剪，第一版可以先不裁剪，但测试应覆盖总输出稳定。
- `references/` 只渲染相对路径，不渲染全文。

## 错误处理

- `agent.skills.enabled=false` 时，SkillManager 返回空列表，Agent 正常运行。
- `skills` 目录不存在时，注册表为空，并记录 info 日志。
- 单个非法 Skill 应抛出 `SkillLoadException`，阻止启动。
- 重复 `name` 应抛出 `SkillLoadException`。
- 读取文件失败应抛出 `SkillLoadException`，包含目录和原因。
- Prompt 渲染失败时返回空字符串并记录 warning，避免阻断普通聊天。

## 测试范围

新增测试：

- `SkillMarkdownParserTests`
  - 能解析合法 frontmatter。
  - 缺少 frontmatter 时失败。
  - 缺少 `name` 或 `description` 时失败。
  - body 保留原始 Markdown 内容。

- `FileSystemSkillManagerTests`
  - 能扫描临时 `skills/*` 目录并注册多个 Skill。
  - 新增 Skill 目录无需修改 Java 代码即可出现在 `list()` 中。
  - 重复 `name` 抛出异常。
  - `name` 与目录名不一致抛出异常。
  - 缺少 `SKILL.md` 的目录被跳过。
  - `references/` 文件被记录为相对路径。

- `SkillToolMappingTests`
  - 能从 `skill.json` 读取工具关联。
  - 没有 `skill.json` 时能从正文反引号识别工具名。
  - `findByToolNames(List.of("meituan_travel"))` 返回对应 Skill。

- `FunctionCallingAgentLoopTests`
  - system prompt 包含 SkillManager 渲染出的相关 Skill。
  - 没有 Skill 时保持原行为。
  - 不出现针对具体 Skill 名的注册分支。

- `ApplicationContextTests`
  - Spring 上下文能加载 `FileSystemSkillManager`。
  - 现有 `skills/meituan-travel` 和 `skills/wechat-food-ordering` 能通过真实目录扫描注册。

## 实现顺序

1. 新增 `SkillDefinition`、`SkillReference`、`SkillLoadException`、`SkillManager`。
2. 新增 `SkillMarkdownParser`，只负责解析 frontmatter 和 body。
3. 新增 `FileSystemSkillManager`，实现扫描、校验和注册。
4. 新增工具关联提取逻辑，支持 `skill.json` 和正文反引号工具名。
5. 在 `FunctionCallingAgentLoop` 构造器中注入可选 `SkillManager`。
6. 在构建 system prompt 时加入动态 Skill 上下文。
7. 删除或压缩已迁移到 Skill 的业务硬编码规则。
8. 补充单元测试和 Spring 上下文测试。

## 设计决策

- Skill 是领域指令层，不是工具执行层。
- 动态加载以目录协议为准，不以 Java if-else 注册为准。
- 第一版不做热加载，避免运行时一致性问题。
- 第一版不全量读取 references，避免上下文膨胀。
- 已有 `skills/*` 是权威目录，`src/skills/*` 不作为默认路径。
- 如果后续需要更强协议，可以在每个 Skill 中增加 `skill.json`，但不阻断现有 Skill。
