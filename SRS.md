# Game Box - Software Requirements Specification

## 1. Introduction

### 1.1 Purpose

This specification defines the initial software requirements for Game Box and
its first game, War. It is intended to guide implementation and verification.
It complements [REQUIREMENTS.md](./REQUIREMENTS.md), which captures the product
vision and open product decisions. Package boundaries, reuse guidance, and the
incremental build approach are documented in [ARCHITECTURE.md](./ARCHITECTURE.md).

### 1.2 Scope

Game Box is an Android application intended to host multiple independent games.
The first increment is a playable War game against a computer opponent. An
external display can optionally show the shared game table while the Android
device shows controls and any device-local private information.

The first increment does not require online multiplayer, a full catalog of
games, or game categories beyond War.

### 1.3 Terms

- **Game Box**: The Android app and shared app-level experience.
- **Game**: A selectable playable experience, such as War.
- **Phone view**: The user interface on the Android device.
- **External view**: The optional public/shared interface shown on a connected
  secondary display.
- **POC**: The existing proof-of-concept implementation in this repository.
- **War**: A card game in which players compare cards and collect won cards.
  Exact tie-resolution rules remain to be confirmed.

### 1.4 References

- Product requirements: [REQUIREMENTS.md](./REQUIREMENTS.md)
- Existing implementation: `app/src/main/java/com/gamebox/war/`
- Existing layouts: `app/src/main/res/layout/`

## 2. Overall description

### 2.1 Product perspective

The current POC is a single-game Android app. The target product will add a
game-selection experience and a reusable app shell while allowing each game
to own its rules and game-specific screens. The initial implementation may
remain simple; abstractions should be introduced when needed by a second game
or a clearly agreed extension.

### 2.2 User classes

- **Solo player**: Starts and plays a supported game without another human.
- **Computer-opponent player**: Plays against an in-app automated opponent.
- **Local multiplayer participants**: Share a game using the interaction
  method supported by that game. The initial War increment is not required to
  support human multiplayer.

### 2.3 Operating environment

- Android application written in Kotlin.
- Current POC configuration: minimum Android API 26, compile/target SDK 34.
- External display availability depends on the device, Android version,
  connection method, and display hardware.
- Current UI strings and labels are primarily Hebrew.

### 2.4 Constraints and dependencies

- The app must work without an external display.
- An external `Presentation` is a separate public view; the phone remains the
  primary control surface in the initial increment.
- The app cannot guarantee that a given device can connect to or independently
  drive a second display.
- The game engine must not depend on Android UI classes so its rules can be
  tested independently.

### 2.5 Assumptions and unresolved decisions

- The first playable game is War against a computer.
- "Scholly" and "astrateg" are provisional category labels; their intended
  meaning must be confirmed.
- War tie rules, multiple-player interaction (same device versus network),
  launch languages, and exact supported display configurations are open
  decisions. Requirements that depend on them must not be silently treated as
  finalized.

## 3. System features and functional requirements

Requirements are mandatory for the initial increment unless marked **Future**.

### 3.1 App launch and game selection

- **FR-001** The system shall launch into a usable Game Box experience.
- **FR-002** The system shall provide a way to start the available War game.
- **FR-003** The system shall make the selected game and supported play mode
  visible before the game begins.
- **FR-004 (Future)** The system shall display multiple available games in a
  catalog and allow a user to launch a selected game.
- **FR-005** The home catalog may list planned games by category, but shall
  clearly distinguish them from playable games and prevent launching a game
  that is not implemented.

### 3.2 War game session

- **FR-010** The system shall initialize a standard 52-card deck with four
  suits and thirteen ranks per suit.
- **FR-011** The system shall shuffle the deck and distribute all cards between
  the human player and computer opponent, with 26 cards each at game start.
- **FR-012** The system shall allow the human player to request the next round
  while the game is active.
- **FR-013** Each round shall draw one card for each participant and resolve
  the outcome using the agreed War rules.
- **FR-014** The system shall display the drawn cards, each participant's
  remaining card count, and the round/game result.
- **FR-015** Cards won in a round shall be added to the winner's deck in the
  order specified by the agreed rules.
- **FR-016** The system shall detect and display the end of the game when a
  participant can no longer continue under the agreed rules.
- **FR-017** The system shall prevent a round action after the game ends.
- **FR-018** The system shall allow the user to start a new game, reset the
  session, and re-enable round actions.
- **FR-019** The game engine shall expose enough state for the phone and
  external view to render the same current public game state.
- **FR-020** The system shall handle ties and insufficient cards according to
  a documented rule set. This requirement is blocked from final acceptance
  until the rule set is agreed.

### 3.3 External display

- **FR-030** The system shall detect a suitable connected secondary display
  when the app is active and show the game's external view on it.
- **FR-031** The external view shall show the public War table state, including
  current cards, remaining card counts, and round/game status.
- **FR-032** The external view shall update after each game-state change.
- **FR-033** The system shall remove or dismiss the external view when its
  display is disconnected or no longer available.
- **FR-034** The phone game shall remain usable when no suitable secondary
  display exists or when display setup fails.
- **FR-035** The system shall not render private player information on the
  public external view. The initial War game has no private card hand.
- **FR-036 (Future)** A game that supports private information shall keep that
  information on the intended player's device/view and provide a separately
  defined shared view.
- **FR-037** When an external display is active, the user shall be able to
  configure whether shared game information (cards, counts, and round result)
  also appears on the phone; game controls shall remain available there.
- **FR-038** Each catalog game shall display an illustrative image. Games in
  the same category may reuse a category-level illustration.
- **FR-039** Games shall provide optional, calm background music and relevant
  sound effects, with separate user settings to enable or disable each.
- **FR-043** Background music and sound effects shall stop when leaving the
  active game and resume only when a game is started.
- **FR-044** The War game shall award one series point to the winner when a
  complete match ends. Starting the next match shall preserve the series
  score; starting a new series shall reset it.
- **FR-045** The phone shall always show the human player's remaining card
  count, even when shared game information is configured to appear only on the
  external display.
- **FR-046** When an external display is active, the phone shall let the
  player enter or select a short message and send it to the public display.
  The public display shall show the message without hiding the game result.

### 3.4 Play modes and multiplayer

- **FR-040** The initial War game shall support a human player versus the
  computer.
- **FR-041 (Future)** A game may declare support for solo, computer-opponent,
  and/or human multiplayer modes; the user interface shall offer only modes
  implemented for that game.
- **FR-042 (Future)** Before implementation of human multiplayer, the product
  shall specify whether play is same-device pass-and-play, multiple local
  devices, online, or a combination.

## 4. External interface requirements

### 4.1 User interface

- The phone UI shall provide visible game status, current round information,
  and controls appropriate to the active game.
- The War phone UI shall provide a draw/continue action and a new-game action.
- Controls that are invalid after game completion shall be disabled or
  otherwise prevented.
- The UI shall clearly indicate whether an external display is connected when
  the platform can determine that status.
- Text direction, language switching, and supported locales require product
  confirmation; the current POC is Hebrew-oriented.

### 4.2 Hardware and platform interfaces

- The app shall use Android-supported display APIs to present a public view on
  a suitable secondary display.
- The app shall handle display addition and removal during an active session.
- No extra permissions are required by the current POC for its basic display
  behavior; any future feature requiring permissions must explain and request
  them explicitly.

## 5. Non-functional requirements

- **NFR-001 Reliability:** Each valid round action shall produce one consistent
  game-state transition; the phone and external view shall represent that same
  state.
- **NFR-002 Correctness:** Game rules shall be testable independently of the UI,
  including deck distribution, card comparison, ties, deck exhaustion, and
  game completion.
- **NFR-003 Resilience:** Loss of the external display shall not crash or
  prevent play on the phone.
- **NFR-004 Privacy:** Public output shall not reveal information identified
  as private by a game.
- **NFR-005 Compatibility:** The first increment shall retain the configured
  Android minimum API level unless a product decision changes it.
- **NFR-006 Maintainability:** Game-specific rules and state shall be separated
  from Android view and external-display lifecycle code.
- **NFR-007 Usability:** The active state, available action, and outcome of a
  round shall be understandable without relying solely on color.
- **NFR-008 Localization:** User-facing strings shall be kept in Android
  resources so supported languages can be added without changing game logic.

## 6. Data and state requirements

- A War session shall track both participants' decks, the most recently played
  cards, round outcome, and whether the session has ended.
- Starting a new game shall create a fresh shuffled deck and clear prior-round
  display state.
- Deck counts shall never be negative.
- Each card shall belong to exactly one participant's deck or the active
  round/war pile, according to the agreed rules.
- Saving a game across process death is not required for the initial
  increment; decide this before adding persistence.

## 7. Verification and acceptance criteria

The initial increment is acceptable when all of the following are true:

1. The Android app builds and launches on a supported device/emulator.
2. A new War game starts with 26 cards per participant from a 52-card deck.
3. Automated tests verify ordinary round wins, ties, insufficient-card cases,
   deck counts, game completion, and new-game reset using the agreed rules.
4. The user can play a complete human-versus-computer game without an external
   display.
5. When a supported secondary display is connected, it shows the public table
   and stays synchronized with game state, including series score and any
   message sent by the player.
6. Disconnecting the secondary display leaves the phone game usable.
7. Unsupported play modes are not offered as if they were implemented.
8. Each completed War match increments the winner's series score once; the
   next match preserves it, and resetting the series clears it.
9. With shared information hidden from the phone, the player's remaining card
   count and all game controls remain available there.

## 8. Items to resolve before final acceptance

- Confirm War tie/war rules, including the number and visibility of cards placed
  during a war, chained ties, and what happens when a participant has too few
  cards.
- Confirm what "scholly" and "astrateg" refer to and choose user-facing
  category names.
- Confirm whether multiplayer means same-device play, multiple devices, online
  play, or some combination.
- Confirm launch languages and expected right-to-left/left-to-right behavior.
- Confirm target devices and external-display setups for manual testing.
