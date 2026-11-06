package com.gamebox.taki.ui.nearby

import com.gamebox.taki.domain.Direction
import com.gamebox.taki.domain.TakiAction
import com.gamebox.taki.domain.TakiCard
import com.gamebox.taki.domain.TakiColor
import com.gamebox.taki.domain.TakiPrivateState
import com.gamebox.taki.domain.TakiPublicPlayer
import com.gamebox.taki.domain.TakiPublicState
import com.gamebox.taki.domain.TakiRank
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets

object TakiMessageCodec {
    fun lobbyBytes(playerId: String, players: List<String>, capacity: Int): ByteArray =
        JSONObject()
            .put("type", "lobby")
            .put("playerId", playerId)
            .put("players", JSONArray(players))
            .put("capacity", capacity)
            .toBytes()

    fun actionBytes(action: TakiAction): ByteArray {
        val message = JSONObject().put("type", "action")
        when (action) {
            is TakiAction.PlayCard -> {
                message.put("action", "play")
                message.put("card", cardJson(action.card))
                message.put("chosenColor", action.chosenColor?.name ?: JSONObject.NULL)
            }
            TakiAction.Draw -> message.put("action", "draw")
            TakiAction.CloseTaki -> message.put("action", "close")
            TakiAction.DeclareLastCard -> message.put("action", "declare")
        }
        return message.toBytes()
    }

    fun decodeAction(message: JSONObject): TakiAction? = when (message.optString("action")) {
        "play" -> {
            val chosenColor = message.optString("chosenColor")
                .takeIf { it.isNotEmpty() && it != "null" }
                ?.let(TakiColor::valueOf)
            TakiAction.PlayCard(cardFromJson(message.getJSONObject("card")), chosenColor)
        }
        "draw" -> TakiAction.Draw
        "close" -> TakiAction.CloseTaki
        "declare" -> TakiAction.DeclareLastCard
        else -> null
    }

    fun stateBytes(
        playerId: String,
        public: TakiPublicState,
        private: TakiPrivateState,
        wins: Map<String, Int>
    ): ByteArray {
        val publicPlayers = JSONArray()
        public.players.forEach { player ->
            publicPlayers.put(
                JSONObject()
                    .put("id", player.id)
                    .put("name", player.name)
                    .put("cardCount", player.cardCount)
                    .put("isHost", player.isHost)
            )
        }
        val winObject = JSONObject()
        wins.forEach { (id, count) -> winObject.put(id, count) }

        return JSONObject()
            .put("type", "state")
            .put("playerId", playerId)
            .put(
                "public",
                JSONObject()
                    .put("players", publicPlayers)
                    .put("currentPlayerId", public.currentPlayerId)
                    .put("direction", public.direction.name)
                    .put("topCard", cardJson(public.topCard))
                    .put("currentColor", public.currentColor.name)
                    .put("pendingDraw", public.pendingDraw)
                    .put("isTakiOpen", public.isTakiOpen)
                    .put("winnerId", public.winnerId ?: JSONObject.NULL)
                    .put("isGameOver", public.isGameOver)
                    .put("lastActionMessage", public.lastActionMessage)
            )
            .put(
                "private",
                JSONObject()
                    .put("hand", JSONArray().apply {
                        private.hand.forEach { put(cardJson(it)) }
                    })
                    .put("canDraw", private.canDraw)
                    .put("mustDraw", private.mustDraw)
                    .put("isMyTurn", private.isMyTurn)
            )
            .put("wins", winObject)
            .toBytes()
    }

    fun decodePublicState(message: JSONObject): TakiPublicState {
        val json = message.getJSONObject("public")
        val playersJson = json.getJSONArray("players")
        val players = (0 until playersJson.length()).map { index ->
            val player = playersJson.getJSONObject(index)
            TakiPublicPlayer(
                id = player.getString("id"),
                name = player.getString("name"),
                cardCount = player.getInt("cardCount"),
                isHost = player.getBoolean("isHost")
            )
        }
        val winner = json.optString("winnerId").takeIf { it.isNotEmpty() && it != "null" }
        return TakiPublicState(
            players = players,
            currentPlayerId = json.getString("currentPlayerId"),
            direction = Direction.valueOf(json.getString("direction")),
            topCard = cardFromJson(json.getJSONObject("topCard")),
            currentColor = TakiColor.valueOf(json.getString("currentColor")),
            pendingDraw = json.getInt("pendingDraw"),
            isTakiOpen = json.getBoolean("isTakiOpen"),
            winnerId = winner,
            isGameOver = json.getBoolean("isGameOver"),
            lastActionMessage = json.getString("lastActionMessage")
        )
    }

    fun decodePrivateState(message: JSONObject): TakiPrivateState {
        val json = message.getJSONObject("private")
        val hand = json.getJSONArray("hand")
        return TakiPrivateState(
            hand = (0 until hand.length()).map { cardFromJson(hand.getJSONObject(it)) },
            canDraw = json.getBoolean("canDraw"),
            mustDraw = json.getBoolean("mustDraw"),
            isMyTurn = json.getBoolean("isMyTurn")
        )
    }

    fun decodeWins(message: JSONObject): Map<String, Int> {
        val json = message.getJSONObject("wins")
        return json.keys().asSequence().associateWith { json.getInt(it) }
    }

    private fun cardJson(card: TakiCard): JSONObject = JSONObject()
        .put("color", card.color?.name ?: JSONObject.NULL)
        .put("rank", card.rank.name)

    private fun cardFromJson(json: JSONObject): TakiCard {
        val color = json.optString("color")
            .takeIf { it.isNotEmpty() && it != "null" }
            ?.let(TakiColor::valueOf)
        return TakiCard(color, TakiRank.valueOf(json.getString("rank")))
    }

    private fun JSONObject.toBytes(): ByteArray = toString().toByteArray(StandardCharsets.UTF_8)
}
