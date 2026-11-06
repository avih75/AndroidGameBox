# Game Box

Game Box is an Android app for a growing collection of games. The current
playable game is **War**, against the computer. The phone is the control
surface; when enabled and supported, a connected external display shows the
shared game table.

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

## Current implementation

- Kotlin Android app, minimum SDK 26, compile/target SDK 34.
- War game against the computer.
- Optional Android secondary-display presentation.
- Local settings to enable or disable use of the external display.
- A setting to show shared game information on both screens or only on the
  external display (phone controls stay available).
- Game catalog grouped into card, board, word/number, and strategy games.
- Reusable category illustrations on the catalog game cards.
- Only War is currently playable; other catalog entries are planned.

War tie handling in the existing POC is simplified. Confirm the final rule
set before treating tie or insufficient-card outcomes as finalized.
