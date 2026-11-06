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
- +2, +3, Stop, Reverse, Change Color, Taki
- No Super Taki
- +2 / +3 stack
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

## Next steps

1. Copy these files into the Android project under  
   `app/src/main/java/com/gamebox/games/taki/domain/`
2. Add unit tests for legal plays, stacking, Taki sequence, win condition.
3. Build phone UI + external Presentation that only render `TakiPublicState` / `TakiPrivateState`.
4. Later: wire to `common/multiplayer`.
