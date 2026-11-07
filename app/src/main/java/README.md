# Taki Domain (v1)

Pure Kotlin game logic for Taki – no Android dependencies.

## Files

| File | Responsibility |
|------|----------------|
| `TakiCard.kt` | Card model (color + rank) |
| `TakiPlayer.kt` | Player model |
| `TakiState.kt` | Full state + Public / Private snapshots |
| `TakiAction.kt` | Actions a player may send |
| `TakiEngine.kt` | Rules engine (Host only) |
| `TakiAi.kt` | Simple computer opponent |
| `TakiMultiplayerConfig.kt` | Min/max players & host role |

## Rules included in v1

- Numbers 1–9 (two of each color)
- +2, + (play another same-color card), Stop, Reverse, Change Color, Taki
- No Super Taki
- +2 stacks and passes the draw penalty to the next player
- Open Taki sequence (same color) → player closes it
- 8 cards dealt to each player

## How to use (Host)

```kotlin
val engine = TakiEngine()
var state = engine.newGame(
    playerNames = listOf("אני", "מחשב 1", "מחשב 2"),
    humanPlayerIds = setOf("p0"),
    hostPlayerId = "p0"
)

// Player plays
state = engine.apply(state, "p0", TakiAction.PlayCard(someCard))

// AI turn
val ai = TakiAi()
val action = ai.chooseAction(state, state.currentPlayer.id)
state = engine.apply(state, state.currentPlayer.id, action)

// Send to clients
val public = state.publicState()
val private = state.privateStateFor("p1")
```

## Android UI and multiplayer

- `com/gamebox/taki/ui/phone/TakiHostActivity.kt` offers practice, host, and join modes.
- A host advertises a Taki lobby over Nearby Connections, sets the maximum player count (2–6), and starts once another player has joined.
- Clients discover nearby lobbies and authenticate the connection code on both devices.
- The host remains authoritative: it applies all actions and sends public state plus only the receiving client's private hand.
- If a client disconnects mid-game, the host takes over that seat with the computer player. The host can view and reset persistent win totals.
- `com/gamebox/common/multiplayer/NearbySession.kt` contains the reusable Nearby advertising, discovery, connection, and payload transport.
- `com/gamebox/taki/ui/nearby/TakiMessageCodec.kt` encodes Taki lobby, action, public-state, and per-player private-state messages.
- War offers local play against the computer plus Nearby host/join modes for 2–6 players. The host resolves simultaneous reveals and broadcasts only public round state.
- `com/gamebox/common/ui/PlayingCardView.kt` draws reusable standard playing-card faces and backs for War phone, multiplayer, and external-display tables.
