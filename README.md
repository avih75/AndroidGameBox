# Game Box

Game Box is an Android app for a growing collection of games. Playable games
include **War**, **Taki**, **Guess the first letter**, and **Write the letter**.
The letter games support English and Hebrew, solo or teacher-guided play, and
can show their shared picture and prompt on an external display.

The home screen also lists proposed follow-on games by category. Games marked
as planned are catalog suggestions only; they cannot be launched yet.

## Project documentation

- [Product requirements](./REQUIREMENTS.md)
- [Software requirements specification](./SRS.md)
- [Architecture and folder structure](./ARCHITECTURE.md)

## Build and run

Open the project root (`Game-Box`) in Android Studio and allow Gradle sync to
complete. Then use **Run** to launch on an Android device or emulator, or use
**Build > Build APK(s)** to create a debug APK.

The current checkout does not include a Gradle wrapper. Android Studio must
therefore provide the Gradle sync/build environment. Adding a checked-in,
version-pinned wrapper is a recommended next build-reliability task.

CI debug APKs use a version derived from the GitHub Actions run number and
attempt, so each uploaded artifact has a distinct version. Local builds default
to version `1.0` (version code `1`).

## Current implementation

- Kotlin Android app, minimum SDK 26, compile/target SDK 34.
- War game against the computer.
- Optional Android secondary-display presentation.
- Local settings to enable or disable use of the external display.
- A setting to show shared game information on both screens or only on the
  external display (phone controls stay available).
- Procedurally generated ambient background music and card sound effects,
  each independently configurable. The audio is synthesized locally, so the
  app does not bundle third-party music or sound files.
- Game catalog grouped into card, board, word/number, and strategy games.
- Reusable category illustrations on the catalog game cards.
- War series scoring, a phone-only player card count, and selectable messages
  that can be shown on the public display.
- The letter game includes emoji illustrations for its picture cards; animated
  image assets can be added later without changing its question and scoring flow.

War tie handling in the existing POC is simplified. Confirm the final rule
set before treating tie or insufficient-card outcomes as finalized.
