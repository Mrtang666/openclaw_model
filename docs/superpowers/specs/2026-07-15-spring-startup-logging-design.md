# Spring Boot Startup Logging Design

## Goal

Create a minimal Maven-based Spring Boot application that runs on Java 17 and writes an INFO-level log message during application startup.

## Architecture

- Spring Boot 3.x application managed with Maven.
- One application entry point containing a `CommandLineRunner` bean.
- SLF4J logging through Spring Boot's default Logback configuration.
- No web endpoint or database dependency is required.

## Components

- `pom.xml`: project metadata, Java 17 configuration, Spring Boot starter, and test dependencies.
- `SpringApplication.java`: application entry point and startup logging runner.
- `application.properties`: application name and INFO logging configuration.
- `SpringApplicationTests.java`: verifies that the startup runner invokes the logger-facing startup action.

## Startup Flow

1. Maven launches the Spring Boot application.
2. Spring creates the application context.
3. Spring invokes the `CommandLineRunner` after context initialization.
4. The runner writes `Spring application started successfully` at INFO level.

## Error Handling

No custom recovery is needed. If context initialization fails, Spring Boot reports the failure through its standard logging and the startup runner is not invoked.

## Verification

- Run the focused test first and confirm it fails before implementation.
- Add the minimal implementation and confirm the test passes.
- Run the complete Maven test suite.
- Start the application and confirm the expected INFO message appears in console output.
