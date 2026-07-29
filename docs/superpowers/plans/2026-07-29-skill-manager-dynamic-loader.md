# SkillManager Dynamic Loader Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a runtime SkillManager that scans `skills/*`, registers Skill definitions automatically, and injects the relevant Skill instructions into the function-calling agent prompt without any if-else registration.

**Architecture:** Keep `WechatToolRegistry` as the tool execution registry and add a separate `SkillManager` in `com.example.spring.skill` for loading and rendering Skill metadata. Parse `SKILL.md` frontmatter at startup, build an immutable registry, and select relevant skills by tool mapping or explicit name before the agent loop builds its prompt.

**Tech Stack:** Java 17, Spring Boot 3.4, JUnit 5, AssertJ, existing Spring component scanning, existing Maven resource packaging for `skills/`.

---

## File Structure

- Create `src/main/java/com/example/spring/skill/SkillDefinition.java`: immutable Skill model.
- Create `src/main/java/com/example/spring/skill/SkillReference.java`: record resolved reference entries.
- Create `src/main/java/com/example/spring/skill/SkillLoadException.java`: fail-fast loader exception.
- Create `src/main/java/com/example/spring/skill/SkillManager.java`: query and render contract.
- Create `src/main/java/com/example/spring/skill/SkillMarkdownParser.java`: parse YAML frontmatter and body from `SKILL.md`.
- Create `src/main/java/com/example/spring/skill/FileSystemSkillManager.java`: scan `skills/*`, validate, index, and render.
- Create `src/main/java/com/example/spring/skill/SkillToolMapping.java`: read optional `skill.json` or infer tool names from Skill body.
- Modify `src/main/java/com/example/spring/wechat/conversation/agent/FunctionCallingAgentLoop.java`: inject Skill context into the prompt.
- Modify `src/main/java/com/example/spring/wechat/conversation/agent/FunctionCallingAgentRequest.java`: add optional `skillContext` only if needed by the prompt path.
- Modify `src/main/java/com/example/spring/wechat/conversation/WechatConversationService.java`: pass skill context or skill names into the agent request path.
- Modify `src/main/java/com/example/spring/AgentClawApplication.java` only if a new `@ConfigurationProperties` class is introduced.
- Test `src/test/java/com/example/spring/skill/SkillMarkdownParserTests.java`.
- Test `src/test/java/com/example/spring/skill/FileSystemSkillManagerTests.java`.
- Test `src/test/java/com/example/spring/skill/SkillToolMappingTests.java`.
- Modify `src/test/java/com/example/spring/wechat/conversation/agent/FunctionCallingAgentLoopTests.java`.
- Modify `src/test/java/com/example/spring/wechat/conversation/WechatConversationServiceTests.java`.
- Add or modify context-loading tests if Spring wiring needs confirmation.

## Baseline

- [ ] **Step 1: Run focused baseline tests**

Run:

```powershell
mvn -q "-Dtest=FunctionCallingAgentLoopTests,WechatConversationServiceTests,ApplicationContextTests" test
```

Expected: build succeeds before feature edits. If this fails, inspect whether failures are unrelated baseline issues before proceeding.

## Task 1: Skill Markdown Parsing

**Files:**
- Create: `src/main/java/com/example/spring/skill/SkillMarkdownParser.java`
- Test: `src/test/java/com/example/spring/skill/SkillMarkdownParserTests.java`

- [ ] **Step 1: Write the failing parser test**

Test one valid file and one invalid file:

```java
String markdown = """
---
name: meituan-travel
description: Travel planning skill
---

# Travel

Use `meituan_travel`.
""";

SkillMarkdownParser.ParsedSkillMarkdown parsed = parser.parse(markdown);
assertThat(parsed.frontmatter()).containsEntry("name", "meituan-travel");
assertThat(parsed.frontmatter()).containsEntry("description", "Travel planning skill");
assertThat(parsed.body()).contains("Use `meituan_travel`.");
```

Also assert a markdown string without frontmatter throws `SkillLoadException`.

- [ ] **Step 2: Run the red test**

Run:

```powershell
mvn -q "-Dtest=SkillMarkdownParserTests" test
```

Expected: FAIL because the parser does not exist yet.

- [ ] **Step 3: Implement the minimal parser**

Add a small parser that:

- requires a leading `---` frontmatter block;
- reads `name` and `description`;
- preserves the remaining Markdown body verbatim;
- throws `SkillLoadException` for malformed or missing frontmatter.

- [ ] **Step 4: Run the green test**

Run:

```powershell
mvn -q "-Dtest=SkillMarkdownParserTests" test
```

Expected: PASS.

## Task 2: FileSystemSkillManager

**Files:**
- Create: `src/main/java/com/example/spring/skill/SkillDefinition.java`
- Create: `src/main/java/com/example/spring/skill/SkillReference.java`
- Create: `src/main/java/com/example/spring/skill/SkillLoadException.java`
- Create: `src/main/java/com/example/spring/skill/SkillManager.java`
- Create: `src/main/java/com/example/spring/skill/FileSystemSkillManager.java`
- Test: `src/test/java/com/example/spring/skill/FileSystemSkillManagerTests.java`

- [ ] **Step 1: Write the failing manager tests**

Create a temporary `skills` tree in the test and assert:

```java
assertThat(manager.list()).extracting(SkillDefinition::name)
        .containsExactly("meituan-travel", "wechat-food-ordering");
assertThat(manager.findByName("meituan-travel")).isPresent();
assertThat(manager.findByName("missing")).isEmpty();
assertThat(manager.renderSkillContext(List.of("meituan-travel"))).contains("[Skill: meituan-travel]");
```

Also assert:

```java
assertThatThrownBy(() -> manager.list())
        .isInstanceOf(SkillLoadException.class)
        .hasMessageContaining("duplicate");
```

and that a directory without `SKILL.md` is ignored.

- [ ] **Step 2: Run the red test**

Run:

```powershell
mvn -q "-Dtest=FileSystemSkillManagerTests" test
```

Expected: FAIL because the manager does not exist yet.

- [ ] **Step 3: Implement the loader**

Implement:

- `SkillManager.list()`
- `findByName(String)`
- `findByToolNames(Collection<String>)`
- `renderSkillContext(Collection<String>)`

Loader rules:

- scan only the top-level `skills/*` directories;
- parse each `SKILL.md` with `SkillMarkdownParser`;
- validate `name` equals directory name;
- reject duplicate names;
- collect `references/*` as relative paths;
- keep an immutable registry map;
- do not use if-else registration for concrete skills.

- [ ] **Step 4: Run the green test**

Run:

```powershell
mvn -q "-Dtest=FileSystemSkillManagerTests" test
```

Expected: PASS.

## Task 3: Skill-to-Tool Mapping

**Files:**
- Create: `src/main/java/com/example/spring/skill/SkillToolMapping.java`
- Test: `src/test/java/com/example/spring/skill/SkillToolMappingTests.java`

- [ ] **Step 1: Write the failing mapping test**

Assert both mapping sources:

```java
assertThat(mapping.toolNamesFor(skill)).contains("meituan_travel");
assertThat(mapping.toolNamesFor(skillWithoutJson)).contains("food_delivery");
```

One case should come from `skill.json`, one from backticked tool names in the markdown body.

- [ ] **Step 2: Run the red test**

Run:

```powershell
mvn -q "-Dtest=SkillToolMappingTests" test
```

Expected: FAIL because the mapping helper does not exist yet.

- [ ] **Step 3: Implement the mapping helper**

Support:

- optional `skill.json` with `tools: [...]`;
- fallback extraction from backticked tool identifiers in `SKILL.md`.

- [ ] **Step 4: Run the green test**

Run:

```powershell
mvn -q "-Dtest=SkillToolMappingTests" test
```

Expected: PASS.

## Task 4: Agent Prompt Integration

**Files:**
- Modify: `src/main/java/com/example/spring/wechat/conversation/agent/FunctionCallingAgentLoop.java`
- Modify: `src/main/java/com/example/spring/wechat/conversation/agent/FunctionCallingAgentRequest.java` only if the prompt path needs an extra field
- Test: `src/test/java/com/example/spring/wechat/conversation/agent/FunctionCallingAgentLoopTests.java`

- [ ] **Step 1: Write the failing prompt test**

Add a test that verifies the first model request contains Skill context:

```java
assertThat(firstRoundMessages)
        .anySatisfy(message -> {
            assertThat(message.role()).isEqualTo("system");
            assertThat(message.content()).contains("[Skill: meituan-travel]");
        });
```

Also verify that the user prompt still contains the original conversation text and no if-else registration is introduced.

- [ ] **Step 2: Run the red test**

Run:

```powershell
mvn -q "-Dtest=FunctionCallingAgentLoopTests" test
```

Expected: FAIL because the prompt does not yet include dynamic Skill context.

- [ ] **Step 3: Implement prompt injection**

Inject `SkillManager` into the agent loop and render at most the relevant skills for the current run.

Recommended shape:

```java
String systemPrompt = BASE_SYSTEM_PROMPT
        + System.lineSeparator()
        + skillManager.renderSkillContext(selectedSkillNames)
        + System.lineSeparator()
        + RAG_SYSTEM_RULES;
```

Keep the tool execution flow unchanged.

- [ ] **Step 4: Run the green test**

Run:

```powershell
mvn -q "-Dtest=FunctionCallingAgentLoopTests" test
```

Expected: PASS.

## Task 5: Conversation Wiring

**Files:**
- Modify: `src/main/java/com/example/spring/wechat/conversation/WechatConversationService.java`
- Test: `src/test/java/com/example/spring/wechat/conversation/WechatConversationServiceTests.java`

- [ ] **Step 1: Write the failing wiring test**

Verify that the service can reach the dynamic Skill layer without changing non-agent flows:

```java
verifyNoInteractions(skillManager);
```

for non-agent paths, and for function-calling paths verify the service forwards the request through the skill-aware agent loop.

- [ ] **Step 2: Run the red test**

Run:

```powershell
mvn -q "-Dtest=WechatConversationServiceTests" test
```

Expected: FAIL because the service has not been wired to the new skill-aware prompt path yet.

- [ ] **Step 3: Wire the dependency**

Inject `SkillManager` where the function-calling path builds its prompt inputs, while leaving normal chat, weather, and image paths unchanged.

- [ ] **Step 4: Run the green test**

Run:

```powershell
mvn -q "-Dtest=WechatConversationServiceTests" test
```

Expected: PASS.

## Task 6: Final Verification

- [ ] **Step 1: Run the focused regression suite**

Run:

```powershell
mvn -q "-Dtest=SkillMarkdownParserTests,FileSystemSkillManagerTests,SkillToolMappingTests,FunctionCallingAgentLoopTests,WechatConversationServiceTests,ApplicationContextTests" test
```

Expected: PASS.

- [ ] **Step 2: Inspect the diff**

Run:

```powershell
git diff --check
git status --short
```

Expected: no whitespace errors and only intended files changed.

- [ ] **Step 3: Commit the implementation**

Run:

```powershell
git add src/main/java/com/example/spring/skill src/main/java/com/example/spring/wechat/conversation src/test/java/com/example/spring/skill src/test/java/com/example/spring/wechat/conversation docs/superpowers/plans/2026-07-29-skill-manager-dynamic-loader.md
git commit -m "feat: add dynamic skill manager"
```

Expected: commit succeeds.
