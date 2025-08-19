# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Java-based implementation following the "Growing Object-Oriented Software Guided by Tests" book. It's an auction sniper application that uses XMPP for communication with auction servers. The project demonstrates test-driven development principles with end-to-end testing using a custom XMPP server implementation.

## Development Commands

### Build and Test
- `./gradlew build` - Builds the project and runs all tests
- `./gradlew test` - Runs only the test suite
- `./gradlew clean` - Cleans the build directory
- `./gradlew app:test` - Runs tests for the app module specifically

### Application
- `./gradlew app:run` - Runs the main application (currently prints "Hello, World!")
- `./gradlew app:jar` - Creates application JAR file
- `./gradlew app:installDist` - Installs the application as a distribution

### Single Test Execution
- `./gradlew test --tests "AuctionSniperEndToEndTest"` - Runs a specific test class
- `./gradlew test --tests "*.sniperJoinsAuctionUntilAuctionCloses"` - Runs a specific test method

## Architecture

### Core Components

**Main Application (`me.forketyfork.growing.Main`)**
- Entry point for the auction sniper application
- Currently minimal implementation (Hello World)

**UI Components (`me.forketyfork.growing.auctionsniper.ui`)**
- `MainWindow` - Defines UI constants and main window name
- Contains status constants: `STATUS_JOINING`, `STATUS_LOST`, `SNIPER_STATUS_NAME`

**Test Infrastructure**
- `AuctionSniperEndToEndTest` - Main end-to-end test using JUnit 5
- `FakeAuctionServer` - Test double that simulates an auction server using XMPP
- `ApplicationRunner` - Test utility to run the application in a separate thread
- `SimpleXmppServer` - Custom minimal XMPP server implementation for testing

### XMPP Architecture

The project includes a sophisticated `SimpleXmppServer` that implements:
- SASL PLAIN authentication flow compatible with Smack 4.5+
- Stream negotiation and restart handling
- IQ stanza processing (auth, roster, bind, session)
- Concurrent client handling with proper resource cleanup
- XML parsing using DOM for reliable stanza processing

### Key Dependencies

- **Smack 4.5.0-beta6** - XMPP client library for Java
- **WindowLicker r268** - UI testing framework for Swing applications  
- **JUnit Jupiter 5.10.3** - Testing framework
- **Java 21** - Target language version

## Development Environment

### Nix Setup
The project includes Nix flake configuration with direnv integration:
- Run `direnv allow` after cloning to set up the development environment
- The Nix shell provides Java and other necessary tools

### Project Structure
- `app/src/main/java/` - Production source code
- `app/src/test/java/` - Test source code  
- `docs/` - Project documentation including XMPP improvement plans
- Single-module Gradle project with `app` submodule

## Testing Strategy

The project follows the book's approach of outside-in TDD:
1. End-to-end tests drive the overall system behavior
2. Tests use real XMPP communication via the embedded server
3. UI tests (currently commented out) will use WindowLicker for Swing testing
4. The `SimpleXmppServer` enables deterministic testing without external dependencies

## Key Files for Understanding

- `AuctionSniperEndToEndTest.java:16` - Main test that demonstrates the auction sniper workflow
- `SimpleXmppServer.java:26` - Custom XMPP server with detailed authentication flow
- `FakeAuctionServer.java:43` - Test fixture that starts XMPP server and manages connections
- `docs/XMPP_PLAN.md` - Comprehensive improvement plan for the XMPP server implementation