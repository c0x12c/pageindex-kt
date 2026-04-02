# Contributing

Thanks for your interest in PageIndex!

## Getting Started

1. Fork the repo
2. Clone your fork
3. Make sure JDK 21+ is installed
4. Run `./gradlew build` to check everything works

## Making Changes

1. Create a branch: `git checkout -b my-feature`
2. Make your changes
3. Add tests for new code
4. Run `./gradlew test` and make sure everything passes
5. Open a pull request

## Code Style

- Kotlin 2.0+ idioms
- Use `Either` for error handling (no thrown exceptions in business logic)
- Interfaces in `com.c0x12c.pageindex.api`, implementations in `com.c0x12c.pageindex.core`

## Reporting Issues

Use the GitHub issue templates for bugs and feature requests.
