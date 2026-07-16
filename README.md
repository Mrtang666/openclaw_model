# OpenClaw CLI

一个基于 Java 17 和 Spring Boot 的最小 CLI Agent 项目骨架。

## 项目结构

```text
src/main/java/com/example/spring
|-- DemoApplication.java
|-- agent
|   `-- AgentService.java
|-- cli
|   `-- ConsoleRunner.java
|-- command
|   |-- Command.java
|   |-- CommandDispatcher.java
|   |-- CommandRegistry.java
|   |-- HelpCommand.java
|   |-- StatusCommand.java
|   |-- VersionCommand.java
|   |-- WeatherCommand.java
|   `-- WechatCommand.java
|-- exception
|   |-- CommandException.java
|   `-- WeatherServiceException.java
|-- tool
|   |-- AgentTool.java
|   |-- ToolRegistry.java
|   `-- WeatherTool.java
`-- weather
    |-- AmapWeatherClient.java
    |-- WeatherClient.java
    |-- WeatherResult.java
    `-- WeatherService.java
`-- wechat
    |-- IlinkWechatClient.java
    |-- IlinkWechatClientFactory.java
    |-- WechatBotService.java
    |-- WechatClient.java
    |-- WechatClientFactory.java
    |-- WechatIncomingMessage.java
    |-- WechatLoginInfo.java
    |-- WechatBotState.java
    |-- WechatBotStatus.java
    `-- WechatStartResult.java
```

## 运行

如果本机还没有安装微信 iLink SDK，先执行：

```powershell
cd C:\Users\Lenovo\Desktop\wechat-ilink-sdk-java
mvn clean install -DskipTests "-Dmaven.compiler.source=8" "-Dmaven.compiler.target=8" "-Dmaven.compiler.release=8"
cd C:\Users\Lenovo\Desktop\openclaw_model
```

```powershell
mvn spring-boot:run
```

启动后可以输入：

```text
help
version
status
weather 北京
wechat start
wechat status
wechat stop
exit
```

天气查询使用高德开放平台天气查询 API。运行前配置环境变量：

```powershell
$env:AMAP_WEATHER_KEY="你的高德 Web 服务 API KEY"
mvn spring-boot:run
```

API Key 不要直接写入 `application.properties`，也不要提交到 Git。项目也支持在根目录创建本地 `.env`：

```properties
AMAP_WEATHER_KEY=你的高德 Web 服务 API KEY
```

## 微信接入

微信入口复用当前 CLI 命令系统。启动后输入：

```text
wechat start
```

程序会输出二维码内容。使用微信扫码登录后，输入：

```text
wechat status
```

如果状态是 `RUNNING`，微信用户给 bot 发送文本命令，例如：

```text
weather 南京
help
status
```

程序会调用现有 `AgentService` 处理这段文本，并把结果回复给该用户。

注意：iLink 发送消息依赖 `contextToken`。目标用户必须先给 bot 发过消息，并且程序已经拉取到该消息，之后才能向这个用户回复。

也可以将命令作为启动参数执行：

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=status"
```

## 测试与构建

```powershell
mvn test
mvn clean package
```
