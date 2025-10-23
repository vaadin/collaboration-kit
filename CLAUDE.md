# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

This is the Vaadin Collaboration Kit, which provides real-time collaboration features for Vaadin Flow applications. The main component is Collaboration Engine, a Java library that enables features like simultaneous editing, presence indicators, avatar groups, and chat functionality.

## Project Structure

The project is organized as a multi-module Maven project:

- **collaboration-engine/**: Core library containing the collaboration engine implementation
- **collaboration-engine-demo/**: Demo application showcasing collaboration features
- **collaboration-engine-test/**: Integration tests with multiple tech stack variants:
  - **collaboration-engine-integration-test/**: Base integration tests
  - **collaboration-engine-test-cdi/**: CDI-specific tests
  - **collaboration-engine-test-spring/**: Spring-specific tests
  - **collaboration-engine-test-common/**: Shared test utilities

## Development Commands

### Building and Running

Build the entire project:
```bash
mvn clean install
```

Build without running tests:
```bash
mvn install -DskipTests
```

### Demo Application

Run the demo application in development mode:
```bash
cd collaboration-engine-demo
mvn jetty:run
```

Run in production mode:
```bash
mvn jetty:run -Pproduction
```

The demo will be available at http://localhost:8080

### Testing

Run unit tests:
```bash
mvn test
```

Run integration tests with local Chrome:
```bash
mvn verify -DuseLocalWebDriver
```

Run integration tests in headless mode:
```bash
mvn verify -DuseLocalWebDriver -Dheadless
```

Run integration tests for a specific module:
```bash
mvn verify -DuseLocalWebDriver -pl collaboration-engine-test/collaboration-engine-test-cdi
```

### Code Quality

Format code according to project conventions:
```bash
mvn spotless:apply
```

Check code formatting:
```bash
mvn spotless:check
```

## Core Architecture

### CollaborationEngine
The main entry point (`collaboration-engine/src/main/java/com/vaadin/collaborationengine/CollaborationEngine.java`) manages:
- Topic connections for real-time collaboration
- User management and color assignment
- Backend configuration (Local or distributed via Hazelcast)
- Service lifecycle management

### Key Components

1. **TopicConnection**: Manages connections to collaboration topics
2. **CollaborationBinder**: Extends Vaadin Binder for collaborative form editing with real-time synchronization
3. **CollaborationAvatarGroup**: Shows avatars of active users in a topic
4. **CollaborationMessageList**: Real-time chat/messaging component
5. **FieldHighlighter**: Provides visual indicators when users are editing fields

### Backend Configuration
- **LocalBackend**: In-memory backend for single-server deployments
- **External Backend**: Distributed backend using Hazelcast for multi-server setups

### Data Synchronization
- Uses JSON-based serialization for field values
- Supports basic types (String, Boolean, Integer, etc.) and collections
- Custom serializers can be configured for complex types
- Real-time updates via topic subscriptions

## Technology Stack

- **Java 17**: Minimum required version
- **Vaadin Flow 25.0**: UI framework
- **Maven**: Build tool
- **Jetty**: Development server
- **JUnit 4**: Testing framework
- **TestBench**: Browser automation testing
- **Jackson**: JSON processing
- **SLF4J**: Logging

## Important Notes

- All collaboration features require a topic ID to synchronize between users
- Components automatically handle user presence and connection management
- The project uses Spotless for code formatting with Eclipse Java conventions
- Integration tests support multiple browser configurations (local, Sauce Labs, remote Selenium)
- The demo application includes examples of all major collaboration features