# Repository Guidelines

## Project Structure & Module Organization

App Manager is a multi-module Android project, primarily written in Java.
- `app/src/main/java/io/github/muntashirakon/AppManager/`: application code organized by feature.
- `app/src/main/res/`, `assets/`, and `cpp/`: Android resources, bundled assets, and native code under `app/src/main/`.
- `app/src/test/java/` and `app/src/test/resources/`: unit tests and fixtures.
- `libcore/{compat,io,ui}/`, `libserver/`, `server/`, `hiddenapi/`, and `libopenpgp/`: shared libraries, privileged server components, and API support.
- `docs/`: documentation; `scripts/`: maintenance tools; `fastlane/`: store metadata; `arts/`: artwork.

## Build, Test, and Development Commands

Follow `BUILDING.rst` for setup. Use JDK 17+ (CI uses 21), Android SDK/build tools specified in `versions.gradle`, and native build tooling. Documentation generation also needs LaTeX and pandoc.

- `git submodule update --init --recursive`: initialize supporting datasets.
- `./gradlew packageDebugUniversalApk`: build the debug universal APK.
- `./gradlew :app:installDebug`: install the debug app on a connected device or emulator; launch it from the device.
- `./gradlew test`: run the unit tests used by CI.
- `./gradlew :app:testDebugUnitTest --tests '*UadListParserTest'`: run one test class.
- `./gradlew lint`: run Android Lint; inspect reports in `app/build/reports/`.

## Coding Style & Naming Conventions

Match surrounding code: four-space indentation, same-line opening braces, `UpperCamelCase` classes, `lowerCamelCase` methods and fields, and `UPPER_SNAKE_CASE` constants. Use lowercase underscore-separated Android resource names. Java source targets Java 8; preserve Android API 21 compatibility. Follow existing SPDX headers and attribution rules in `CONTRIBUTING.rst`; do not add `@author` tags. Use Android Lint for static checks.

## Testing Guidelines

Tests use JUnit 4 and Robolectric. Name classes `*Test`, mirror production packages, and use descriptive test methods. Add regression tests for behavioral fixes and keep fixtures under `src/test/resources/`. No numeric coverage threshold is configured. Debug test reports appear in `app/build/reports/tests/testDebugUnitTest/`.

## Commit & Pull Request Guidelines

Recent commits use concise imperative subjects, such as “Restore running and usage requirements when loading filter profiles.” Sign off commits with `git commit --signoff`.

Read `CONTRIBUTING.rst` and the PR template. Coordinate feature work and obtain issue assignment first. Work against current sources; link related issues, explain the problem and solution, report validation, and include screenshots when useful. Upstream prohibits AI/LLM-generated contributions and PRs, in whole or part.
