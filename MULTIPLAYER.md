# Game Box – Multiplayer Architecture

## 1. Goals

- Support multiplayer games where one device is the central **Host** and other devices are **Clients**.
- Clients see only their private player view.
- The Host owns the shared public view (including the external display when available).
- Work entirely on the **local network** with **no cloud server**.
- Use the same multiplayer approach for every game that needs more than one player.

## 2. Decisions (current stage)

| Topic | Decision |
|-------|----------|
| Network scope | Local only (same Wi-Fi / nearby devices) |
| Server | None – pure peer-to-peer |
| Discovery | Automatic discovery |
| Host role | Host may be an active player **or** a pure moderator / dealer |
| Client view | Private information only |
| External display | Always controlled by the Host |
| Join mid-game | Not supported in the first version |
| Disconnect of Host | Game ends |
| Disconnect of Client | Game continues or pauses according to the specific game rules |

## 3. Roles

### Host
- Creates the game session.
- Runs the authoritative game logic.
- Renders the public / shared state.
- Owns the external display (Presentation) when one is connected.
- May also play as a regular player, or act only as dealer/moderator.

### Client
- Discovers and joins a Host session.
- Receives private state relevant only to that player.
- Sends player actions (play card, choose option, etc.) to the Host.
- Never runs authoritative game rules.

## 4. Technology

**Primary choice: Google Nearby Connections API**

Reasons:
- No server required.
- Built-in automatic discovery.
- Suitable for same-room / same-network party games.
- Handles connection lifecycle and messaging.

Alternative (if Nearby Connections proves insufficient later):
- Network Service Discovery (NSD) + local WebSocket / TCP on the Host.

For the first implementation we standardize on **Nearby Connections**.

## 5. Session Flow

1. **Host** starts a multiplayer game from the catalog / game setup screen.
2. Host begins advertising the session (game type + optional display name).
3. **Clients** open “Join Game” and see available sessions discovered automatically.
4. Client selects a session and connects.
5. Host sees the list of connected players and can start the game when the minimum number of players is reached (and maximum is not exceeded).
6. During the game:
   - Host sends **public state** (broadcast) and **private state** (per player).
   - Clients send only **actions**.
7. When the game ends, Host can start a new round or return to the lobby.
8. If the Host disconnects, the session is terminated for everyone.

## 6. Data Contract (shared across games)

Every multiplayer game must define:

### Public state
Data that all players (and the external display) may see.
Examples: community cards, current turn, scores, discard pile, board.

### Private state (per player)
Data visible only to one player.
Examples: hole cards, private hand, personal notes.

### Actions
Commands a Client may send to the Host.
Examples: `PlayCard`, `ChooseOption`, `Pass`, `Ready`.

The Host is the single source of truth. Clients never decide game outcomes.

## 7. Game-specific configuration

Each multiplayer-capable game declares:

```kotlin
data class MultiplayerConfig(
    val minPlayers: Int,
    val maxPlayers: Int,
    val hostCanBeSpectator: Boolean,   // true = Host may be dealer/moderator only
    val hostMustPlay: Boolean          // true = Host is always an active player
)
```

Examples:
- War (future multiplayer version): min 2, max 6, hostCanBeSpectator = false
- Taki: min 2, max 6, hostCanBeSpectator = false
- Trivia: min 2, max 10, hostCanBeSpectator = true (Host as quiz master)
- Poker: min 2, max 8, hostCanBeSpectator = true (Host as dealer)

## 8. Responsibilities by layer

### `common/multiplayer` (shared)
- Discovery and advertising
- Connection management
- Sending / receiving typed messages
- Basic lobby (player list, ready state)
- Session lifecycle (start, end, host lost)

### `games/<game>/domain`
- Pure game rules and state transitions
- No Android or networking code
- Accepts actions and produces new public + private state

### `games/<game>/ui`
- Host UI + external Presentation
- Client private UI
- Maps user input to actions and renders state received from the Host

## 9. Message types (high level)

```
// Host → All / specific Client
PublicStateUpdated
PrivateStateUpdated
GameStarted
GameEnded
PlayerJoined
PlayerLeft

// Client → Host
JoinRequest
PlayerAction
PlayerReady
Leave
```

Exact payload shapes will be defined per game, but the envelope and transport stay common.

## 10. Out of scope (current stage)

- Internet / remote play
- Cloud matchmaking or relay servers
- Rejoining a game after disconnect
- Spectators who are not the Host
- Cross-platform clients (only Android for now)
- Persistence of in-progress multiplayer sessions

## 11. Implementation order (recommended)

1. Create `MULTIPLAYER.md` (this document) and place it in the repository root.
2. Add a minimal `common/multiplayer` skeleton (interfaces + Nearby Connections wrapper).
3. Implement a simple lobby (Host advertises, Clients discover and connect).
4. Choose one game to be the first multiplayer title (recommendation: a simple turn-based game or an extended War).
5. Wire public/private state and actions for that game.
6. Only after the first game works end-to-end, extract further shared helpers.

## 12. Open points to revisit later

- Whether to show a short numeric room code as a fallback when automatic discovery fails.
- Exact behavior when a Client disconnects mid-round (auto-fold, pause, replace with AI, etc.).
- Whether the Host external display should show private information of any kind (default: never).
- Adding a lightweight “Kick player” control for the Host.

---

**Status:** Taki and War use Nearby Connections for host advertising, client discovery, authenticated joining, host-authoritative actions, and per-client private state. Each game retains its own lobby and message contract; shared discovery/connection/payload transport lives in `common/multiplayer`.
All future multiplayer games must follow this model unless this document is explicitly updated.
