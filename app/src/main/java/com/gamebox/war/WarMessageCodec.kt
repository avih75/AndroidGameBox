package com.gamebox.war

import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets

object WarMessageCodec {
    fun lobbyBytes(playerId: String, players: List<String>, capacity: Int): ByteArray =
        JSONObject()
            .put("type", "lobby")
            .put("playerId", playerId)
            .put("players", JSONArray(players))
            .put("capacity", capacity)
            .toBytes()

    fun revealBytes(): ByteArray =
        JSONObject().put("type", "reveal").toBytes()

    fun stateBytes(playerId: String, state: WarMultiplayerState): ByteArray {
        val players = JSONArray()
        state.players.forEach { player ->
            players.put(
                JSONObject()
                    .put("id", player.id)
                    .put("name", player.name)
                    .put("cardCount", player.deck.size)
            )
        }
        val lastCards = JSONObject()
        state.lastCards.forEach { (id, card) ->
            lastCards.put(id, cardJson(card))
        }
        return JSONObject()
            .put("type", "state")
            .put("playerId", playerId)
            .put(
                "public",
                JSONObject()
                    .put("players", players)
                    .put("readyPlayerIds", JSONArray(state.readyPlayerIds))
                    .put("lastCards", lastCards)
                    .put("round", state.round)
                    .put("isGameOver", state.isGameOver)
                    .put("winnerId", state.winnerId ?: JSONObject.NULL)
                    .put("message", state.message)
            )
            .put("canReveal", !state.isGameOver && playerId !in state.readyPlayerIds &&
                state.players.any { it.id == playerId && it.deck.isNotEmpty() })
            .toBytes()
    }

    fun decodeState(message: JSONObject): WarMultiplayerClientState {
        val public = message.getJSONObject("public")
        val playersJson = public.getJSONArray("players")
        val players = (0 until playersJson.length()).map { index ->
            val player = playersJson.getJSONObject(index)
            WarMultiplayerPublicPlayer(
                id = player.getString("id"),
                name = player.getString("name"),
                cardCount = player.getInt("cardCount")
            )
        }
        val readyJson = public.getJSONArray("readyPlayerIds")
        val ready = (0 until readyJson.length()).map { readyJson.getString(it) }.toSet()
        val cardsJson = public.getJSONObject("lastCards")
        val cards = cardsJson.keys().asSequence().associateWith { cardFromJson(cardsJson.getJSONObject(it)) }
        val winnerId = public.optString("winnerId").takeIf { it.isNotEmpty() && it != "null" }
        return WarMultiplayerClientState(
            playerId = message.getString("playerId"),
            players = players,
            readyPlayerIds = ready,
            lastCards = cards,
            round = public.getInt("round"),
            isGameOver = public.getBoolean("isGameOver"),
            winnerId = winnerId,
            message = public.getString("message"),
            canReveal = message.getBoolean("canReveal")
        )
    }

    private fun cardJson(card: Card): JSONObject = JSONObject()
        .put("rank", card.rank)
        .put("suit", card.suit)
        .put("value", card.value)
        .put("isRed", card.isRed)

    private fun cardFromJson(json: JSONObject) = Card(
        rank = json.getString("rank"),
        suit = json.getString("suit"),
        value = json.getInt("value"),
        isRed = json.getBoolean("isRed")
    )

    private fun JSONObject.toBytes(): ByteArray =
        toString().toByteArray(StandardCharsets.UTF_8)
}

data class WarMultiplayerClientState(
    val playerId: String,
    val players: List<WarMultiplayerPublicPlayer>,
    val readyPlayerIds: Set<String>,
    val lastCards: Map<String, Card>,
    val round: Int,
    val isGameOver: Boolean,
    val winnerId: String?,
    val message: String,
    val canReveal: Boolean
)

data class WarMultiplayerPublicPlayer(
    val id: String,
    val name: String,
    val cardCount: Int
)
