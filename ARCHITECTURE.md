# Game Box - Architecture and Folder Structure

## 1. Goals

- Keep the Android project straightforward to build and understand.
- Separate app-level navigation and device integration from game rules.
- Make game rules independently testable without Android views.
- Reuse genuinely shared behavior, but avoid a generic game framework before
  more than one game needs it.
- Keep the POC runnable during incremental restructuring.

## 2. Current project baseline

The repository is currently a single Android application module (`:app`).
Kotlin source and layouts are under `app/src/main/`, and the War implementation
is in `com.gamebox.war`. There is no Gradle wrapper or test source set in the
current project listing. The app's Gradle `namespace` and `applicationId` are
both `com.gamebox.war`, with min SDK 26 and compile/target SDK 34.

Keep the current package and application ID while stabilizing the first
playable War release. Do not move files or change identifiers merely to make
the tree look more layered. The target layout below is a gradual destination,
not a prerequisite for implementing or testing the game rules.

## 3. Proposed source structure

Use feature-oriented packages inside the existing app module at first:

```text
app/
  src/
    main/
      java/com/gamebox/
        app/
          MainActivity.kt
          navigation/
        common/
          display/
          audio/
          ui/
        games/
          war/
            domain/
              Card.kt
              WarGame.kt
              WarRules.kt
            ui/
              phone/
                WarGameFragment.kt
                WarViewModel.kt
              external/
                WarTablePresentation.kt
      res/
        layout/
        values/
    test/
      java/com/gamebox/games/war/domain/
```

The exact Android UI pattern (Activity-only, Fragments, or another pattern)
should follow the project's needs. Do not add a navigation library, dependency
injection framework, or multi-module setup solely to match this example.

### Package responsibilities

- `app`: app entry point, game selection, and app-level navigation.
- `common`: small capabilities genuinely shared across games, such as
  external-display lifecycle coordination, shared game audio, and common UI
  conventions.
- `games.<game>.domain`: rules and state for one game; no Android imports.
- `games.<game>.ui.phone`: phone UI and interaction for that game.
- `games.<game>.ui.external`: that game's public secondary-display rendering.
- `res`: shared Android resources, with localized text in `values/strings.xml`
  and game-specific layout resources grouped/named clearly.

Do not let `common` depend on a specific game's rules or view. A game may use
shared app capabilities, but one game's domain must not depend on another
game's implementation.

## 4. Reuse guidelines

1. **Reuse proven behavior, not names or assumptions.** The current external
   display listener and `Presentation` lifecycle are useful starting points.
   Extract a shared display coordinator only when the second game needs the
   same behavior and the common responsibilities are clear.
2. **Keep rules game-specific.** The War engine should own shuffling, dealing,
   round resolution, and War-specific edge cases. A future chess or word game
   should not have to conform to card-game concepts.
3. **Share stable primitives when they really match.** A playing-card model
   may be reused by other card games. Do not make it the universal model for
   board pieces, letters, tiles, or game state.
4. **Use Android resources for shared presentation concerns.** Reuse colors,
   typography, dimensions, and localized strings where they express the same
   design meaning. Keep game-specific layouts separate when the experiences
   differ.
5. **Prefer composition and small interfaces over a base-game class.** Add a
   shared interface only when multiple real games need the same contract, for
   example a catalog entry describing a game's name and supported modes.
6. **Avoid duplicate copies of common behavior.** Before adding a helper,
   search the project for existing implementations. If two features need the
   same behavior, extract one well-named helper and test it rather than copying
   it.
7. **Do not generalize speculative requirements.** Private player views,
   online synchronization, persistence, animations, and game plug-ins should
   be designed when a concrete game or release requires them.

## 5. Migration and build sequence

1. **Establish a repeatable build.** Add and commit the Gradle wrapper using a
   Gradle version compatible with the Android Gradle Plugin in
   `build.gradle.kts`; document the required JDK and build commands.
2. **Stabilize War rules.** Agree on tie and insufficient-card behavior before
   making the engine the source of truth for game outcomes.
3. **Separate rules from UI.** Keep or move the War model and engine into a
   domain package, and add JVM unit tests for deterministic rule cases. Inject
   or pass a shuffle source as needed for deterministic tests; production
   games should still shuffle normally.
4. **Make rendering state-driven.** Have phone and external-display rendering
   consume the same game state or snapshot instead of implementing game rules
   in either view.
5. **Grow the app shell deliberately.** The current app has a home catalog,
   War setup, settings, and the War game screen. Keep future catalog entries
   visibly unavailable until implemented. Register each playable game through
   a small explicit catalog list; add common abstractions when a second game
   demonstrates the need.
6. **Verify after each structural change.** Run the wrapper's debug build and
   focused unit tests before moving or renaming more code.

### Build commands once the wrapper exists

From the project root:

```text
gradlew.bat assembleDebug
gradlew.bat testDebugUnitTest
```

The current checkout does not include `gradlew.bat` or `gradle/wrapper/`, so
these commands are the intended repeatable workflow but are not yet runnable
from the repository as-is. Do not silently substitute an unpinned locally
installed Gradle version for a committed wrapper.

## 6. Dependency direction

```text
app/navigation -> games/<id>/ui -> games/<id>/domain
games/<id>/ui -> common
common -X-> games/<id>
games/<id>/domain -X-> Android UI / resources / display APIs
```

The game's external renderer is part of `games/<id>/ui/external` and reads
that game's state. Shared display lifecycle helpers, if needed, belong in
`common/display`; they must not contain game-specific rendering or rules.

Game domain code must not depend on Android, resources, activities, or display
APIs. Phone and external-display UI may depend on Android and read the game's
state. Shared app infrastructure must not decide game outcomes.

## 7. Test organization

- Put game-rule tests in `app/src/test/` alongside package-matched test
  packages. These should run on the JVM and not require an emulator.
- Test starting deck invariants, round outcomes, ties, insufficient-card cases,
  terminal states, and reset behavior for War.
- Keep Android display and lifecycle checks as instrumented/manual tests where
  an actual secondary display or Android framework behavior is required.
- A UI test should verify interaction and rendered state, not duplicate the
  domain test suite's rule coverage.

## 8. Decisions to revisit

- Split into multiple Gradle modules only when build time, team boundaries, or
  dependency isolation make the cost worthwhile.
- Change the Kotlin `namespace` only as part of an intentional package
  migration. Preserve the existing `applicationId` for app update
  compatibility unless a deliberate product decision says otherwise.
- Add persistence only after deciding whether games resume after process death.
- Add a shared external-display abstraction after at least two games use the
  same lifecycle behavior.
