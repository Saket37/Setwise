# Setwise

A workout tracker for Android that keeps logging fast and remembers what you lifted last time. It uses on-device AI (Gemini Nano) for summaries and suggestions, so your training data never leaves the phone.

> **Status:** early development. The Workout tab (dashboard, start-a-workout sheet, templates list, resume card) works with real data; set logging is next. See the [roadmap](#roadmap).

## Features

**Available now**
- Workout tab: weekly stats, templates, and a card to resume a workout that's still running
- Start a workout empty or from a template, and back-date the start time
- Only one workout runs at a time; starting another asks to discard the running one first
- A library of 128 exercises, seeded on first launch
- Light and dark themes, plus an animated splash screen

**Planned for v1**
- Log sets (weight × reps) with last time's numbers shown next to each set
- Templates: create, edit, or save one from a finished workout
- Workout timer with editable start and end times
- Rest timer that keeps running in the background, with sound
- Cardio entries (time, distance, calories)
- Personal-record markers, workout summary and history
- On-device AI (every feature also works without it):
  - workout summary and calorie estimate
  - quick-log by typing or speaking ("3 sets of bench at 60 for 8")
  - hints on when to add weight, and notes when you plateau
  - questions about your history
  - a weekly recap
  - templates built from a goal

## Tech stack

| Area | Library |
|---|---|
| Language | Kotlin 2.3 |
| UI | Jetpack Compose (Material 3), Navigation Compose with type-safe routes |
| Architecture | One ViewModel per screen; UiState / Action / Event (unidirectional) |
| Data | Room (exported schemas), DataStore Preferences, kotlinx.serialization |
| DI | Koin |
| Background work | WorkManager; a foreground service for the rest timer |
| On-device AI | ML Kit GenAI Prompt API (Gemini Nano) |
| Tests | JUnit 4, kotlinx-coroutines-test, Turbine |

Build setup: AGP 9.1 (built-in Kotlin), KSP, Gradle 9.3, compileSdk / targetSdk 36, minSdk 26, Java 17.

## Getting started

**Requirements:** Android Studio 2026.2 or newer, JDK 17, and an Android device or emulator on API 26 or higher.

```bash
git clone git@github.com:Saket37/Setwise.git
cd Setwise
./gradlew installDebug
```

Run the unit tests:

```bash
./gradlew testDebugUnitTest
```

### Development notes

- **Sample data.** Debug builds fill the database with sample templates and workouts (`data/dev/DevDataSeeder.kt`). To see the first-run screen instead, set `DevDataSeeder.ENABLED = false` and clear the app's data.
- **Exercise library.** Exercises are seeded from `app/src/main/assets/exercises.json`. When you add exercises, **bump its `version`** so existing installs pick up the new entries.
- **Kotlin version.** Keep the Kotlin version in `gradle/libs.versions.toml` in sync with Android Studio's bundled Kotlin. A mismatch causes errors that only show up in the IDE.
- **Pinned libraries.** The Compose BOM and Navigation are held at their last releases that support compileSdk 36. Newer versions need SDK 37.

## Project structure

```
app/src/main/java/dev/saketanand/setwise/
├── data/
│   ├── local/          Room: database, entities, DAOs, relations
│   ├── repository/     repository implementations
│   ├── mapper/         entity ↔ domain mappers
│   ├── seed/           exercise library seeding (versioned)
│   └── dev/            debug-only sample data
├── domain/
│   ├── model/          plain Kotlin models
│   └── repository/     repository interfaces
├── ui/
│   ├── designsystem/
│   │   ├── theme/      colours, type scale, shapes, spacing
│   │   ├── components/ reusable components (SetwiseButton, SetwiseListCard, …)
│   │   └── preview/    preview annotations and wrappers
│   ├── navigation/     routes, NavHost, bottom bar
│   └── home/ workout/ exercises/ templates/ history/ summary/ settings/
│                       one package per feature: Screen + ViewModel (+ UiState/Action/Event)
├── llm/                on-device AI provider (planned)
├── service/            rest timer foreground service (planned)
├── di/                 Koin modules
└── util/               date/duration formatting, injectable clock
```

### Conventions

- **Screens.** Each screen has an `XScreenRoot` and an `XScreen`. `XScreenRoot` gets the ViewModel and collects its state and events. `XScreen` is stateless: it takes `uiState` and `onAction`, and is what previews render.
- **Components.** Any UI piece used in more than one place becomes a component in `ui/designsystem/components`. Components follow Material's API style: slot parameters, `XDefaults.colors()`, and size/style enums.
- **Stability.** UI models are marked `@Immutable`, and `java.time` types are declared stable in `app/compose_stability.conf`, so Compose can skip unchanged parts of the UI when it redraws.
- **Theme only.** UI code takes text styles and colours from `MaterialTheme`, never hard-coded values.

## Roadmap

| # | Milestone | Status |
|---|---|---|
| 1 | Room database, exercise seeding, exercise picker | Picker in progress |
| 2 | Workout logging | Next |
| 3 | Templates (editor, save from a workout) | Partly done (list and start) |
| 4 | "Previous" column | |
| 5 | Workout timer and editable start/end times | |
| 6 | Rest timer service | |
| 7 | Cardio | |
| 8 | Personal records | |
| 9 | Workout summary and history | |
| 10–15 | On-device AI features (with non-AI fallbacks) | |
| 16 | Polish | |

**After v1:** supplement tracker, water reminders, Health Connect sync, food and calorie tracking.

## Design

- **Typography:** Barlow Condensed for display text and numbers, DM Sans for everything else.
- **Colours:** dark-first, with a "Volt" green accent and "Ember" orange reserved for personal records.

## Licenses

Fonts are under the SIL Open Font License; see [`licenses/`](licenses/).
