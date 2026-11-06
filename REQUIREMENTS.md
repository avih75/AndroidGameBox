# Game Box - Product Requirements

## 1. Purpose

Game Box is an Android app that brings multiple kinds of games together in one
place. It will start with the card game War and grow into a reusable game
platform for board, word/number, and strategy games.

This document records the product goals and user-facing requirements. The
implementation-level requirements for the first release are in [SRS.md](./SRS.md).

## 2. Product vision

Make it easy to choose and play a game on an Android device, with the option to
use a connected external display as a shared table or game board. The phone
remains the primary control surface and can show information intended only for
its current user. Games that need multiple players should be able to provide
appropriate local multiplayer options.

## 3. Users and use cases

- A person playing a game alone.
- A person playing against a computer opponent.
- Two or more people playing a supported local multiplayer game.
- Players sharing a public game view on a TV or other external display while
  using the phone for controls or private information.

## 4. Product requirements

### 4.1 Game Box

- **PR-01** The app shall provide a home/catalog experience from which users can
  select an available game.
- **PR-02** The app shall be structured so additional games can be added without
  replacing the app's overall navigation and game-launch experience.
- **PR-03** Each game shall declare which player modes and options it supports;
  the app shall not offer unsupported modes as playable choices.
- **PR-04** The initial game shall be War, based on the existing proof of
  concept (POC).

### 4.2 Play modes

- **PR-05** Where a game supports it, a user shall be able to play solo, against
  the computer, or with multiple human players.
- **PR-06** The app shall make the selected mode and relevant game options clear
  before a game starts.
- **PR-07** The initial War release shall support a human player against a
  computer opponent. Other War modes are out of scope until their rules and
  interaction model are agreed.

### 4.3 Phone and external display

- **PR-08** A game may present a shared/public view on an attached external
  display and a separate control or private-information view on the phone.
- **PR-09** A game's public view shall not reveal information designated as
  private to a player.
- **PR-10** Games shall remain playable on the phone when an external display
  is unavailable, disconnected, or unsupported.
- **PR-11** The app shall communicate external-display availability to the
  user and update or dismiss the external view when display availability
  changes.
- **PR-12** External-display support shall be capability-dependent; the app
  shall not promise that every Android device, adapter, or wireless-display
  method supports a second independent display.

### 4.4 Initial game categories

The planned categories are:

1. Board games.
2. Word and number games (the original description says "scholly"; exact
   category name and intended games need confirmation).
3. Strategy games (the original description says "astrateg"; exact meaning
   needs confirmation).

These are roadmap categories, not a commitment that all categories ship in the
first release.

### 4.5 Starter catalog

The home catalog may show planned games as unavailable entries, clearly
separate from games that can actually be launched. The initial proposed list
includes:

- **Cards:** War (available), Memory, Higher or Lower, Snap, Go Fish, Solitaire.
- **Board:** Tic-tac-toe, Connect Four, Dots and Boxes, Checkers, Battleship.
- **Words and numbers:** Guess the Number, Hangman, 2048, Sudoku, Word Search.
- **Strategy:** Nim, Reversi.

These are candidates selected for familiar rules and a reasonable path to a
small offline implementation. "Easy" and "medium" are rough relative effort
labels, not estimates or commitments. Validate each candidate's rules,
accessibility, multiplayer needs, and expected scope before scheduling it.

## 5. Initial release proposal

The recommended first playable release is a single-player War game against the
computer with:

- A clear start/restart flow and visible game status.
- Correct, testable card dealing, round resolution, tie/war handling, and
  win/loss detection.
- A phone-first interface that works without extra hardware.
- An optional public table view on an external display, kept in sync with the
  phone.
- Automated tests for the game rules, including edge cases.

The current POC is a useful starting point, not yet a specification of the
final rules or architecture. It currently displays a single extra card for a
tie, awards tied extra cards to the human player, and does not continue a chain
of tied face-up cards. Those rules should be confirmed before treating the POC
behavior as final.

## 6. Suggestions and product decisions

- **Settle War rules first.** Agree on how many face-down and face-up cards a
  war uses, what happens when a player cannot complete a war, and whether a tie
  can trigger another war. Write examples for each edge case.
- **Start with one complete game.** Finish and test War end-to-end before
  investing in a generalized framework or adding a catalog of unfinished games.
- **Treat public/private display boundaries as a core feature.** Define what
  may appear on the TV and what must remain on the phone for every multiplayer
  game.
- **Keep games independent behind a small shared app shell.** Share navigation,
  settings, display lifecycle, and common UI conventions; keep game rules
  owned by each game rather than building a large framework prematurely.
- **Design for accessibility and localization early.** The current POC UI is
  Hebrew. Confirm whether Hebrew is the launch language and whether English or
  right-to-left/left-to-right switching is required.
- **Do not assume network play.** Decide whether "multiple players" initially
  means pass-and-play on one device, separate devices over a network, or both.

## 7. Open decisions

| ID | Decision needed | Recommended starting point |
|---|---|---|
| OD-01 | What exactly does "scholly" mean? | Rename the category to "Word and number games" unless a different intent is confirmed. |
| OD-02 | What exactly does "astrateg" mean? | Treat it as strategy games, pending confirmation. |
| OD-03 | Which War rules should be used? | Agree on a written rule set before changing the engine. |
| OD-04 | What does multiple-player mode mean for the first release? | Start with one-device local play only if a multiplayer game is selected; defer online play. |
| OD-05 | Which languages are required at launch? | Confirm Hebrew-only versus Hebrew and English. |
| OD-06 | What devices and external-display paths must be supported? | Define a short test matrix of Android devices and wired/wireless display setups. |

## 8. POC baseline

The repository currently contains an Android/Kotlin app with a War game engine,
a phone screen, and an Android `Presentation` for an external display. The
prototype targets Android API 26 as its minimum and uses a 34 compile/target
SDK configuration. The external view currently mirrors public card, count, and
message information; the phone remains the control surface. These facts
describe the inspected POC, not guarantees about the eventual product.
