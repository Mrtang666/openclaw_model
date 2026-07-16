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
|   `-- WeatherCommand.java
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
```

## 运行

```powershell
mvn spring-boot:run
```

启动后可以输入：

```text
help
version
status
weather 北京
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

也可以将命令作为启动参数执行：

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=status"
```

## 测试与构建

```powershell
mvn test
mvn clean package
```
