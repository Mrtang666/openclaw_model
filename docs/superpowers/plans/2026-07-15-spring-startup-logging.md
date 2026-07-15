# Spring Boot Startup Logging Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a minimal Java 17 Spring Boot application that prints an INFO log when startup completes.

**Architecture:** A Maven Spring Boot application starts through `DemoApplication`. A focused `StartupLogger` implements `CommandLineRunner` and writes the startup message through SLF4J, making the behavior independently testable with Spring Boot's captured-output test support.

**Tech Stack:** Java 17, Spring Boot 3.4.7, Maven, SLF4J/Logback, JUnit 5

---

## File Structure

- `pom.xml`: Maven coordinates, Java version, Spring Boot dependencies, and build plugin.
- `src/main/java/com/example/spring/DemoApplication.java`: Spring Boot entry point.
- `src/main/java/com/example/spring/StartupLogger.java`: startup callback and INFO log behavior.
- `src/main/resources/application.properties`: application name and logging level.
- `src/test/java/com/example/spring/StartupLoggerTests.java`: captured-output verification for the startup log.

### Task 1: Maven Project Skeleton

**Files:**
- Create: `pom.xml`
- Create: `src/main/java/com/example/spring/DemoApplication.java`
- Create: `src/main/resources/application.properties`

- [ ] **Step 1: Create the Maven build**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.7</version>
        <relativePath/>
    </parent>

    <groupId>com.example</groupId>
    <artifactId>spring-startup-logging</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>spring-startup-logging</name>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Create the application entry point**

```java
package com.example.spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

- [ ] **Step 3: Configure the application name and INFO logging**

```properties
spring.application.name=spring-startup-logging
logging.level.com.example.spring=INFO
```

- [ ] **Step 4: Verify the skeleton compiles**

Run: `mvn compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit the skeleton**

```bash
git add pom.xml src/main
git commit -m "build: create spring boot project skeleton"
```

### Task 2: Startup Log Behavior

**Files:**
- Create: `src/test/java/com/example/spring/StartupLoggerTests.java`
- Create: `src/main/java/com/example/spring/StartupLogger.java`

- [ ] **Step 1: Write the failing captured-output test**

```java
package com.example.spring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class StartupLoggerTests {

    @Test
    void logsSuccessMessageWhenRun(CapturedOutput output) {
        new StartupLogger().run();

        assertThat(output).contains("Spring application started successfully");
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `mvn -Dtest=StartupLoggerTests test`
Expected: compilation failure because `StartupLogger` does not exist.

- [ ] **Step 3: Add the minimal startup logger**

```java
package com.example.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class StartupLogger implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupLogger.class);

    @Override
    public void run(String... args) {
        log.info("Spring application started successfully");
    }
}
```

- [ ] **Step 4: Run the focused test and verify GREEN**

Run: `mvn -Dtest=StartupLoggerTests test`
Expected: one test passes and Maven reports `BUILD SUCCESS`.

- [ ] **Step 5: Commit the tested behavior**

```bash
git add src/main/java/com/example/spring/StartupLogger.java src/test/java/com/example/spring/StartupLoggerTests.java
git commit -m "feat: log when spring application starts"
```

### Task 3: End-to-End Verification

**Files:**
- Verify: `pom.xml`
- Verify: `src/main/java/com/example/spring/DemoApplication.java`
- Verify: `src/main/java/com/example/spring/StartupLogger.java`
- Verify: `src/test/java/com/example/spring/StartupLoggerTests.java`

- [ ] **Step 1: Run the complete test suite**

Run: `mvn test`
Expected: all tests pass with `BUILD SUCCESS`.

- [ ] **Step 2: Package the application**

Run: `mvn package`
Expected: executable JAR created at `target/spring-startup-logging-0.0.1-SNAPSHOT.jar`.

- [ ] **Step 3: Start the packaged application**

Run: `java -jar target/spring-startup-logging-0.0.1-SNAPSHOT.jar`
Expected: console output contains `Spring application started successfully`, then the non-web application exits normally.

- [ ] **Step 4: Confirm the working tree state**

Run: `git status --short`
Expected: only the implementation plan may remain uncommitted.

