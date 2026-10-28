# AI Agent Instructions for NetMeter Development

This document serves as the absolute source of truth for any AI agent or LLM assisting with the development of the **NetMeter** project. You must strictly adhere to these rules, architectural guidelines, security constraints, and developer preferences before generating or modifying any code.

## 1. Core Development Philosophy

- **Goal:** NetMeter is a high-performance, battery-efficient, native Android network traffic monitor and utility application built with Kotlin, Jetpack Compose, Material Design 3, Navigation Compose, and lifecycle-aware ViewModels.
- **Greeting & Verification:** To verify you are not hallucinating and are actively reading these project instructions, start every conversation or reply with the words **'Hey Devson'**.
- **No Hallucinations:** Only use existing APIs, classes, models, assets, and packages configured in `gradle/libs.versions.toml`. Do not invent dependency versions or use deprecated APIs. If unsure about an existing implementation or backend schema, ask the developer to fetch the file contents.
- **One Line Explanation:** Do not explain things in long phrases and paragraphs. Be short, direct, and precise. Avoid unnecessary filler or unsolicited architecture essays.
- **Zero Crash Tolerance:** Ensure the app never crashes under any network or data conditions. Always prioritize graceful degradation (e.g., error banners, retry buttons, empty states) over throwing unhandled exceptions.
- **Production Realism (No Demo Artifacts):** The application is designed for production use. Never display "demo", "demo session", or mock labels in user-facing UI screens, snackbars, or buttons.

## 2. Strict Security: Keystore, Credentials & Secrets Protection

- **Zero Hardcoded Secrets:** NEVER hardcode keystore passwords, aliases, real API keys, secrets, access tokens, or sensitive credentials in `app/build.gradle.kts` or source code (`app/src/`).
- **No Secrets Behind Fallback Operators:** Never use production passwords or secret values as fallback values after the `?:` operator in Gradle build scripts or Kotlin source files.
- **Credential Isolation:** All release signing credentials must be loaded dynamically from `keystore.properties` (or environment variables).
- **Git Protection:** Never commit `keystore.properties`, `*.jks`, or credentials to source control. The `.gitignore` must keep these files strictly ignored.

## 3. UI / Android & Material 3 Guidelines

- **Mobile-First Design:** Prioritize touch targets (minimum 48x48 dp), responsive edge-to-edge screens (`enableEdgeToEdge()`), and safe area padding across all Android screen sizes and aspect ratios.
- **Material Design 3:** Exclusively utilize Material 3 components and design tokens from `com.devson.netmeter.core.theme` (`androidx.compose.material3`).
- **Responsive & Keyboard Resilient:** All forms and scrollable views must handle keyboard popups, orientation, and different viewport heights without clipping or UI overlapping.
- **State Hoisting & Clean Architecture:** Keep Composables modular and stateless where possible. Never perform heavy transformations, disk I/O, or network operations inside `@Composable` functions. Manage UI state using lifecycle-aware ViewModels and `StateFlow` (`collectAsStateWithLifecycle`).

## 4. Code Quality & Performance Optimization

- **Language:** Kotlin with strict null safety and modern coroutines.
- **Null Safety Enforcement:** Handle nullable types safely and exhaustively. **Never** use the not-null assertion operator (`!!`) blindly.
- **Asynchronous Architecture:** Use Kotlin Coroutines and StateFlow (`MutableStateFlow` / `StateFlow`) for all state mutations. Offload heavy computation or I/O to background dispatchers (`Dispatchers.IO` / `Dispatchers.Default`).
- **Eliminate Bottlenecks:**
  - Never block the UI thread with synchronous file operations or intensive computation.
  - Optimize Compose recompositions using stable parameters, `remember`, and lazy layouts (`LazyColumn`, `LazyRow`).
- **Code Writing Format:** Do NOT add `─` anywhere in code files.

## 5. Documentation & Update Tracking

You must actively maintain the project's changelog. After every completed task, error resolution, or feature addition, append an entry to the `update_details.md` file.

**Format and Rules for `update_details.md`:**
- Do NOT rewrite or overwrite the whole file. Simply append the new entry at the very end of the document.
- Include a Date and Time stamp for the update.
- Document using the following template:

```markdown
### [YYYY-MM-DD HH:MM] <Feature or Fix Title>
- **Issue:** (Briefly describe the exact issue, bottleneck, or feature addressed)
- **Type:** (Error, Bug, UI, Performance, Security, Architecture, Feature)
- **Solution:** (Explain how the issue was solved. Maximum 10 lines.)
---
```
- Append exactly `---` on a new line after each entry.
- Do not include conversational filler in `update_details.md`.

## 6. Version Control (Git) Protocol

- **Do not commit or push** any changes to the repository until explicitly requested to do so by the developer.
- **Do not Change .gitignore:** Never modify `.gitignore` automatically. Leave all `.gitignore` files as they are.
