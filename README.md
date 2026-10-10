# Setwise

A workout tracker for Android that keeps logging fast and remembers what you lifted last time. It uses on-device AI (Gemini Nano) for summaries, suggestions and quick logging, so your training data never leaves the phone. Every AI feature also works without the model, using rules in code.

> **Status:** the v1 features are built and in testing. What's left before a first release: the open bugs, screenshot tests in light and dark themes, body-composition progress, and release prep (R8, a release build). See the [roadmap](#roadmap).

## Features

**Workouts**
- Start a workout empty or from a template, and back-date the start time. Only one workout runs at a time.
- Log sets (weight × reps, or time for timed exercises), with last time's numbers shown next to each set
- Hints on when to add weight, based on your own last sessions, plus notes when an exercise plateaus
- **Quick log** by typing or speaking: "3 sets of bench at 60 for 8", "last one was 6"
- Rest timer that keeps running in the background, with sound or vibration, and a workout notification
- Cardio entries: time, distance, speed, incline, level
- Personal-record markers (weight, estimated 1RM, reps, time), live while you train
- Edit or delete sets, rename the workout, and correct start and end times, even after it's finished

**After a workout**
- Summary with volume, records, a calorie estimate (and where it came from), and a short AI insight
- Save a workout as a template

**History and progress**
- History by day, with a scrollable strip and a month calendar
- Ask questions about your history: "best bench set", "how many workouts this month"
- A weekly recap on the Workout tab
- Exercise detail: a progress chart, past sessions, and a suggestion for next time

**Templates and exercises**
- Template editor (exercises, set counts, order), or build one from a goal
- A library of 204 exercises, named in Strong's "Name (Equipment)" style, and your own custom exercises

**Body and profile**
- Profile (name, age, sex, height) and training days, set up in a skippable onboarding
- Body composition: type it in, or take a photo of a report (for example an InBody slip). It's read on the phone, and you check it before it's saved.
- BMR from your report, or worked out from body fat or your profile. Calorie estimates use it.
- A daily check-in for rest days, missed days, and workouts logged later

**Import**
- Bring in workouts from other apps: share text or a CSV (Strong, Hevy, FitNotes), screenshots, or a hand-written log

**Everywhere**
- Light and dark themes, an animated splash screen, and screen-reader support (checked by automated accessibility tests)

## On-device AI

The AI features use Gemini Nano through the ML Kit GenAI APIs, on devices that support it (for example Pixel 9 and newer). The model is downloaded by the system (AICore); Settings shows its status.

The rule throughout: **code decides, the model fills gaps.** Numbers, records, progressions and calorie limits come from code. The model is asked only for what code can't do, such as wording an insight, matching a free-text exercise name, or reading a line code didn't understand. Its answers use structured output, and they're checked: an answer that brings in numbers that weren't in the input is thrown away. Without the model, each feature falls back to its code-only version.

| Feature | Without the model |
|---|---|
| Workout insight, weekly recap, plateau notes | Short text built from the same facts |
| Calorie estimate | MET formula, corrected with your BMR |
| Quick log (typed or spoken) | The code parser, which handles most lines |
| Questions about your history | Questions code can read on its own |
| Custom-exercise details, template from a goal, report and import reading | Code-only matching and parsing |

## Tech stack

| Area | Library |
|---|---|
| Language | Kotlin 2.4 |
| UI | Jetpack Compose (Material 3), Navigation Compose with type-safe routes |
| Architecture | One ViewModel per screen; UiState / Action / Event (unidirectional) |
| Data | Room (exported schemas, migrations), DataStore Preferences, kotlinx.serialization |
| DI | Koin |
| Background | A foreground service for the workout and rest timer |
| On-device AI | ML Kit GenAI: Prompt API (Gemini Nano), Speech Recognition; ML Kit Text Recognition (OCR) |
| Tests | JUnit 4, Robolectric, Compose UI tests, kotlinx-coroutines-test, Turbine, Room MigrationTestHelper, Accessibility Test Framework |
| Code quality | Android Lint, detekt (with Compose rules and ktlint), Compose compiler reports, Kover |

Build setup: AGP 9.4 (built-in Kotlin), KSP, Gradle 9.8, compileSdk / targetSdk 36, minSdk 26, Java 17.

## Getting started

**Requirements:** Android Studio 2026.2 or newer, JDK 17, and an Android device or emulator on API 26 or higher. The AI features need a device with Gemini Nano; everything else works on an emulator.

```bash
git clone git@github.com:Saket37/Setwise.git
cd Setwise
./gradlew installDebug
```

### Checks

These are the checks CI runs on every pull request:

```bash
# Build, unit tests (JVM and Robolectric), screenshots, coverage, lint, detekt, Compose stability
./gradlew assembleDebug verifyRoborazziDebug koverVerifyDebug lintDebug \
  detektDebug detektDebugUnitTest detektDebugAndroidTest debugComposeCompilerCheck

# Instrumented tests (Room, migrations, accessibility) on a device or emulator.
# With more than one connected, pick one, or they install on all of them.
ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest
```

CI also runs the instrumented tests on an API 34 emulator, plus CodeQL and a dependency vulnerability scan.

### Screenshot tests

Every `@Preview` is a screenshot test ([Roborazzi](https://github.com/takahirom/roborazzi) on Robolectric): screens in light and dark at the design size (390×844 dp) and at 130% font, components in light and dark. They render through the app's real theme, on a fixed preview clock (`PREVIEW_NOW_MILLIS`), and are compared with the goldens in `app/src/test/screenshots`.

- **A screen looks different:** `verifyRoborazziDebug` fails. On CI, the run's summary lists the changed screens, and the **screenshot-diffs** artifact has the old and new images side by side.
- **The change is intended:** run the **Record screenshots** workflow (Actions tab) on your branch, download its **screenshots** artifact into `app/src/test/screenshots`, and commit. Record on CI rather than locally (`./gradlew recordRoborazziDebug`): CI's Linux renders text slightly differently from macOS.
- **A new screen or state:** add a `@PreviewScreens` preview with a sample state; it gets screenshots automatically.

### Development notes

- **Sample data.** Debug builds fill an empty database with sample templates and workouts (`data/dev/DevDataSeeder.kt`). To see the first-run screens instead, set `DevDataSeeder.ENABLED = false` and clear the app's data.
- **Database changes.** The schema is at version 3, and installs carry real data. Every schema change bumps the version and ships a migration (an `AutoMigration` where possible) with a test in `MigrationTest`. Never edit an exported `app/schemas/.../N.json`.
- **Exercise library.** Exercises are seeded from `app/src/main/assets/exercises.json`. When you add or rename exercises, **bump its `version`** so existing installs pick up the change. Renames are applied in place, so history is kept.
- **Checking the AI on a device.** A debug build runs its AI checks and logs the results: `adb shell am start -S -n dev.saketanand.setwise/.MainActivity --ez ai_check true`, then read them with `adb logcat --pid=$(adb shell pidof dev.saketanand.setwise)`. Add `--es ai_check_only <part>` (for example `ask` or `speech`) to run one part.
- **Kotlin version.** Keep the Kotlin version in `gradle/libs.versions.toml` in sync with Android Studio's bundled Kotlin. A mismatch causes errors that only show up in the IDE.
- **Pinned libraries.** The Compose BOM and Navigation are held at their last releases that support compileSdk 36. Newer versions need SDK 37.
- **Compose stability.** If a change makes a composable unstable, `debugComposeCompilerCheck` fails. Fix the cause, or regenerate the reports in `app/compose_reports` with `./gradlew debugComposeCompilerGenerate` when the change is intended.

## Project structure

```
app/src/main/java/dev/saketanand/setwise/
├── data/
│   ├── local/          Room: database, entities, DAOs, relations
│   ├── repository/     repository implementations
│   ├── mapper/         entity ↔ domain mappers
│   ├── prefs/          DataStore: settings and profile
│   ├── files/          reading shared files and photos
│   ├── seed/           exercise library seeding (versioned)
│   └── dev/            debug-only sample data
├── domain/
│   ├── model/          plain Kotlin models and rules (records, progression, BMR, parsers)
│   ├── repository/     repository interfaces
│   └── ai/             AI features: what code works out, and what the model is asked
├── llm/                ML Kit GenAI and OCR implementations
├── timer/              rest timer, notifications
├── service/            workout timer foreground service
├── ui/
│   ├── designsystem/
│   │   ├── theme/      colours, type scale, shapes, spacing
│   │   ├── components/ reusable components (SetwiseButton, SetwiseListCard, …)
│   │   └── preview/    preview annotations and wrappers
│   ├── navigation/     routes, NavHost, bottom bar
│   └── home/ workout/ history/ summary/ exercises/ templates/ body/ importing/ onboarding/ settings/
│                       one package per feature: Screen + ViewModel (+ UiState/Action/Event)
├── di/                 Koin modules
└── util/               date, duration and weight formatting, injectable clock
```

Tests live in `app/src/test` (JVM and Robolectric), `app/src/androidTest` (device only), and `app/src/sharedTest` (run in both).

### Conventions

- **Screens.** Each screen has an `XScreenRoot` and an `XScreen`. `XScreenRoot` gets the ViewModel and collects its state and events. `XScreen` is stateless: it takes `uiState` and `onAction`, and is what previews and UI tests render.
- **Components.** Any UI piece used in more than one place becomes a component in `ui/designsystem/components`. Components follow Material's API style: slot parameters, `XDefaults.colors()`, and size/style enums.
- **Stability.** UI models are marked `@Immutable` and use immutable collections, and `java.time` types are declared stable in `app/compose_stability.conf`, so Compose can skip unchanged parts of the UI when it redraws.
- **Theme only.** UI code takes text styles and colours from `MaterialTheme`, never hard-coded values.
- **Touch targets.** Anything tappable is at least 48 dp. The accessibility tests check it.

## Roadmap

| # | Milestone | Status |
|---|---|---|
| 1 | Room database, exercise seeding, exercise picker | Done |
| 2 | Workout logging | Done |
| 3 | Templates (editor, save from a workout) | Done |
| 4 | "Previous" column | Done |
| 5 | Workout timer and editable start/end times | Done |
| 6 | Rest timer service | Done |
| 7 | Cardio | Done |
| 8 | Personal records | Done |
| 9 | Workout summary and history | Done |
| 10 | AI foundation and calorie estimates | Done |
| 11 | Workout insight, custom-exercise matching | Done |
| 12 | Quick log, typed and spoken | Done |
| 13 | Progression hints and plateau notes | Done |
| 14 | Questions about your history, weekly recap | Done |
| 15 | Template from a goal | Done |
| 16 | Polish: open bugs, screenshot tests, body progress | In progress |
| | Release prep (R8, release build) | Not started |

**After v1:** a coach chat for longer goals (for example losing 12 kg of fat, with a workout plan and later a diet plan), supplement tracker, water reminders, Health Connect sync, and food and calorie tracking.

Bugs and features are tracked as [GitHub issues](https://github.com/Saket37/Setwise/issues), labelled P1 (most urgent) to P4.

## Design

- **Typography:** Barlow Condensed for display text and numbers, DM Sans for everything else.
- **Colours:** dark-first, with a "Volt" green accent and "Ember" orange reserved for personal records.

## Licenses

Fonts are under the SIL Open Font License; see [`licenses/`](licenses/).
