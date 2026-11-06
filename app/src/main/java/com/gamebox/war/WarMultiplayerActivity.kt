package com.gamebox.war

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.gamebox.common.audio.GameAudioController
import com.gamebox.common.multiplayer.NearbySession
import com.gamebox.common.ui.PlayingCardView
import org.json.JSONException
import org.json.JSONObject
import java.nio.charset.StandardCharsets

class WarMultiplayerActivity : Activity() {

    private enum class Role { HOST, CLIENT }

    private val engine = WarMultiplayerEngine()
    private val gameAudio = GameAudioController()
    private val preferences by lazy { getSharedPreferences(SETTINGS_PREFERENCES, MODE_PRIVATE) }
    private var role = Role.HOST
    private var nearbySession: NearbySession? = null
    private var presentation: WarMultiplayerPresentation? = null
    private var state: WarMultiplayerState? = null
    private var clientState: WarMultiplayerClientState? = null
    private var hostCapacity = DEFAULT_PLAYER_COUNT
    private var clientEndpointId: String? = null
    private var playerId = "p0"
    private var gameStarted = false
    private var permissionAction: (() -> Unit)? = null

    private val remotePlayerIds = linkedMapOf<String, String>()
    private val remotePlayerNames = linkedMapOf<String, String>()
    private val disconnectedPlayerIds = mutableSetOf<String>()
    private val discoveredEndpoints = linkedMapOf<String, String>()

    private var lobbyMessage: TextView? = null
    private var lobbyPlayers: LinearLayout? = null
    private var lobbyActionButton: Button? = null
    private var lobbyAction: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        role = if (intent.getStringExtra(EXTRA_MODE) == MODE_JOIN) Role.CLIENT else Role.HOST
        withNearbyPermissions {
            if (role == Role.HOST) showHostCapacityDialog() else startJoinLobby()
        }
    }

    private fun showHostCapacityDialog() {
        val picker = NumberPicker(this).apply {
            minValue = WarMultiplayerEngine.MIN_PLAYERS
            maxValue = WarMultiplayerEngine.MAX_PLAYERS
            value = hostCapacity
            wrapSelectorWheel = false
            setFormatter { "$it שחקנים" }
        }
        AlertDialog.Builder(this)
            .setTitle("הגדרת משחק מלחמה")
            .setMessage("בחר מספר שחקנים כולל המארח")
            .setView(picker)
            .setPositiveButton("פתח חדר") { _, _ ->
                hostCapacity = picker.value
                startHostLobby()
            }
            .setNegativeButton("ביטול") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun startHostLobby() {
        remotePlayerIds.clear()
        remotePlayerNames.clear()
        disconnectedPlayerIds.clear()
        nearbySession = createNearbySession()
        showLobby(
            title = "מארחים משחק מלחמה",
            message = "מחפש שחקנים להצטרפות. ניתן להתחיל אחרי ששחקן נוסף יתחבר.",
            actionText = "התחל משחק",
            actionEnabled = false,
            action = ::startHostGame
        )
        nearbySession?.startAdvertising(deviceDisplayName())
    }

    private fun startJoinLobby() {
        discoveredEndpoints.clear()
        clientEndpointId = null
        nearbySession = createNearbySession()
        showLobby(
            title = "הצטרפות למשחק מלחמה",
            message = "מחפש משחקים בקרבת מקום...",
            actionText = "סרוק שוב",
            action = {
                discoveredEndpoints.clear()
                nearbySession?.startDiscovery()
                showDiscoveredEndpoints()
            }
        )
        nearbySession?.startDiscovery()
    }

    private fun createNearbySession() = NearbySession(
        this,
        SERVICE_ID,
        object : NearbySession.Listener {
            override fun onConnectionInitiated(
                endpointId: String,
                endpointName: String,
                authenticationDigits: String
            ) = runOnUiThread {
                if (role == Role.HOST &&
                    (gameStarted || remotePlayerIds.size >= hostCapacity - 1)
                ) {
                    nearbySession?.rejectConnection(endpointId)
                    return@runOnUiThread
                }
                AlertDialog.Builder(this@WarMultiplayerActivity)
                    .setTitle("אימות חיבור")
                    .setMessage(
                        "אשרו שקוד האימות $authenticationDigits זהה בשני המכשירים כדי להתחבר אל $endpointName."
                    )
                    .setPositiveButton("הקוד תואם, חבר") { _, _ ->
                        nearbySession?.acceptConnection(endpointId)
                    }
                    .setNegativeButton("דחה") { _, _ -> nearbySession?.rejectConnection(endpointId) }
                    .setOnCancelListener { nearbySession?.rejectConnection(endpointId) }
                    .show()
            }

            override fun onConnected(endpointId: String, endpointName: String) = runOnUiThread {
                if (role == Role.CLIENT) {
                    clientEndpointId = endpointId
                    nearbySession?.stopDiscovery()
                    updateLobby("התחברת אל $endpointName. ממתין למארח...", false)
                    return@runOnUiThread
                }
                if (gameStarted || remotePlayerIds.size >= hostCapacity - 1) {
                    nearbySession?.disconnect(endpointId)
                    return@runOnUiThread
                }
                val nextId = (1 until WarMultiplayerEngine.MAX_PLAYERS)
                    .map { "p$it" }
                    .first { it !in remotePlayerIds.values }
                remotePlayerIds[endpointId] = nextId
                remotePlayerNames[endpointId] = endpointName
                sendLobbyUpdates()
                refreshHostLobby()
            }

            override fun onDisconnected(endpointId: String) = runOnUiThread {
                if (role == Role.CLIENT) {
                    gameAudio.stopGame()
                    showLobby(
                        "החיבור נותק",
                        "החיבור למארח אבד. המשחק הסתיים.",
                        "חזרה",
                        action = { finish() }
                    )
                    return@runOnUiThread
                }
                val id = remotePlayerIds.remove(endpointId) ?: return@runOnUiThread
                val name = remotePlayerNames.remove(endpointId) ?: id
                if (gameStarted) {
                    disconnectedPlayerIds += id
                    val current = state ?: return@runOnUiThread
                    state = current.copy(message = "$name התנתק — המחשב ימשיך במקומו")
                    revealForDisconnectedPlayers()
                    renderHostState()
                } else {
                    remotePlayerIds.keys.toList().forEachIndexed { index, connectedEndpoint ->
                        remotePlayerIds[connectedEndpoint] = "p${index + 1}"
                    }
                    sendLobbyUpdates()
                    refreshHostLobby()
                }
            }

            override fun onEndpointFound(endpointId: String, endpointName: String) = runOnUiThread {
                if (role != Role.CLIENT || clientEndpointId != null) return@runOnUiThread
                discoveredEndpoints[endpointId] = endpointName
                showDiscoveredEndpoints()
            }

            override fun onEndpointLost(endpointId: String) = runOnUiThread {
                discoveredEndpoints.remove(endpointId)
                showDiscoveredEndpoints()
            }

            override fun onPayload(endpointId: String, bytes: ByteArray) = runOnUiThread {
                handlePayload(endpointId, bytes)
            }

            override fun onError(error: Exception) = runOnUiThread {
                Log.e(TAG, "Nearby connection failed", error)
                Toast.makeText(
                    this@WarMultiplayerActivity,
                    "שגיאת חיבור: ${error.localizedMessage ?: "נסה שוב"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    )

    private fun startHostGame() {
        if (role != Role.HOST || remotePlayerIds.isEmpty()) return
        nearbySession?.stopAdvertising()
        val names = mutableListOf(deviceDisplayName())
        remotePlayerIds.entries
            .sortedBy { it.value.drop(1).toInt() }
            .forEach { names += remotePlayerNames[it.key] ?: "שחקן" }
        state = engine.newGame(names)
        gameStarted = true
        showGameTable()
        showExternalPresentation()
        startAudio()
        renderHostState()
    }

    private fun sendReveal() {
        if (!gameStarted) return
        if (role == Role.CLIENT) {
            if (clientState?.canReveal != true) return
            clientEndpointId?.let { nearbySession?.send(it, WarMessageCodec.revealBytes()) }
            gameAudio.playCardDraw()
        } else {
            val current = state ?: return
            val updated = engine.reveal(current, playerId)
            if (updated == current) return
            state = updated
            gameAudio.playCardDraw()
            revealForDisconnectedPlayers()
            renderHostState()
        }
    }

    private fun revealForDisconnectedPlayers() {
        if (role != Role.HOST) return
        var current = state ?: return
        var updated: WarMultiplayerState
        do {
            updated = current
            disconnectedPlayerIds.forEach { id ->
                current = engine.reveal(current, id)
            }
        } while (current != updated && disconnectedPlayerIds.any { it !in current.readyPlayerIds } &&
            !current.isGameOver)
        state = current
    }

    private fun renderHostState() {
        val current = state ?: return
        showState(current.players.map { player ->
            WarMultiplayerPublicPlayer(player.id, player.name, player.deck.size)
        }, current.readyPlayerIds, current.lastCards, current.round, current.isGameOver,
            current.winnerId, current.message, current.players.any { it.id == playerId &&
                it.deck.isNotEmpty() && it.id !in current.readyPlayerIds } && !current.isGameOver)
        remotePlayerIds.forEach { (endpoint, id) ->
            nearbySession?.send(endpoint, WarMessageCodec.stateBytes(id, current))
        }
    }

    private fun renderClientState(current: WarMultiplayerClientState) {
        showState(
            current.players,
            current.readyPlayerIds,
            current.lastCards,
            current.round,
            current.isGameOver,
            current.winnerId,
            current.message,
            current.canReveal
        )
    }

    private fun showGameTable() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(20), dp(16), dp(16))
            setBackgroundColor(0xFF0F172A.toInt())
        }
        root.addView(TextView(this).apply {
            text = "מלחמה"
            textSize = 28f
            setTextColor(0xFFF1F5F9.toInt())
            gravity = Gravity.CENTER
        })
        val status = TextView(this).apply {
            textSize = 17f
            setTextColor(0xFFFDE68A.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(8))
            tag = STATUS_TAG
        }
        val roundStatus = TextView(this).apply {
            textSize = 15f
            setTextColor(0xFFCBD5E1.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
            tag = ROUND_TAG
        }
        val players = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            tag = PLAYER_LIST_TAG
        }
        val scroll = ScrollView(this).apply {
            addView(players)
        }
        root.addView(status)
        root.addView(roundStatus)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
        root.addView(Button(this).apply {
            text = "חשוף קלף"
            textSize = 18f
            setOnClickListener { sendReveal() }
            tag = REVEAL_BUTTON_TAG
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(56)
        ).apply {
            topMargin = dp(12)
        })
        root.addView(Button(this).apply {
            text = "חזרה למשחקים"
            setOnClickListener { finish() }
        })
        setContentView(root)
    }

    private fun showState(
        players: List<WarMultiplayerPublicPlayer>,
        readyIds: Set<String>,
        cards: Map<String, Card>,
        round: Int,
        gameOver: Boolean,
        winnerId: String?,
        message: String,
        canReveal: Boolean
    ) {
        if (!gameStarted) return
        val root = window.decorView
        root.findViewWithTag<TextView>(STATUS_TAG)?.text = message
        val activeCount = players.count { it.cardCount > 0 }
        val readyCount = readyIds.count { id -> players.any { it.id == id && it.cardCount > 0 } }
        root.findViewWithTag<TextView>(ROUND_TAG)?.text = when {
            gameOver -> "המשחק הסתיים"
            readyCount > 0 -> "סיבוב ${round + 1} · $readyCount מתוך $activeCount מוכנים"
            else -> "סיבוב ${round + 1} · $activeCount שחקנים"
        }
        val list = root.findViewWithTag<LinearLayout>(PLAYER_LIST_TAG) ?: return
        list.removeAllViews()
        players.chunked(2).forEach { rowPlayers ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            rowPlayers.forEach { player ->
                val column = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(10), dp(8), dp(10), dp(8))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val name = TextView(this).apply {
                    val isDisconnected = player.id in disconnectedPlayerIds
                    text = player.name + if (isDisconnected) " · מחשב" else ""
                    textSize = 15f
                    setTextColor(if (player.id == playerId) 0xFF60A5FA.toInt() else 0xFFF1F5F9.toInt())
                    gravity = Gravity.CENTER
                }
                val cardView = PlayingCardView(this).apply {
                    contentDescription = "קלף של ${player.name}"
                    if (cards[player.id] != null) {
                        cards.getValue(player.id).let { showCard(it.rank, it.suit, it.isRed) }
                    } else if (gameOver) {
                        clearCard()
                    } else {
                        showCardBack()
                    }
                }
                val count = TextView(this).apply {
                    text = "קלפים: ${player.cardCount}" +
                        if (player.id in readyIds && !gameOver) " · מוכן" else ""
                    textSize = 13f
                    setTextColor(0xFFCBD5E1.toInt())
                    gravity = Gravity.CENTER
                }
                column.addView(name)
                column.addView(cardView, LinearLayout.LayoutParams(dp(82), dp(114)).apply {
                    topMargin = dp(6)
                    bottomMargin = dp(6)
                })
                column.addView(count)
                row.addView(column)
            }
            if (rowPlayers.size == 1) {
                row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            }
            list.addView(row)
        }
        val button = root.findViewWithTag<Button>(REVEAL_BUTTON_TAG)
        button?.isEnabled = canReveal
        button?.text = when {
            gameOver -> if (winnerId == playerId) "ניצחת!" else "המשחק הסתיים"
            canReveal -> "חשוף קלף"
            playerId in readyIds -> "ממתין לשחקנים..."
            else -> "אין קלפים"
        }
        if (role == Role.HOST) {
            presentation?.update(
                players,
                readyIds,
                cards,
                round,
                gameOver,
                message
            )
        }
    }

    private fun handlePayload(endpointId: String, bytes: ByteArray) {
        val message = try {
            JSONObject(String(bytes, StandardCharsets.UTF_8))
        } catch (error: JSONException) {
            showProtocolError(error)
            return
        }
        when (message.optString("type")) {
            "reveal" -> {
                if (role != Role.HOST || !gameStarted) return
                val id = remotePlayerIds[endpointId] ?: return
                val current = state ?: return
                val updated = engine.reveal(current, id)
                if (updated != current) {
                    state = updated
                    gameAudio.playCardDraw()
                    if (updated.round > current.round) gameAudio.playCardsCollected()
                    revealForDisconnectedPlayers()
                    renderHostState()
                }
            }
            "lobby" -> {
                if (role != Role.CLIENT) return
                playerId = message.optString("playerId", playerId)
                val players = message.optJSONArray("players")
                val names = players?.let { array ->
                    (0 until array.length()).map { array.optString(it) }
                }.orEmpty()
                val capacity = message.optInt("capacity", WarMultiplayerEngine.MAX_PLAYERS)
                updateLobby(
                    "שחקנים בחדר (${names.size}/$capacity): ${names.joinToString(" · ")}. ממתין להתחלה...",
                    false
                )
            }
            "state" -> {
                if (role != Role.CLIENT) return
                val previousRound = clientState?.round
                try {
                    clientState = WarMessageCodec.decodeState(message)
                    playerId = clientState?.playerId ?: playerId
                } catch (error: JSONException) {
                    showProtocolError(error)
                    return
                } catch (error: IllegalArgumentException) {
                    showProtocolError(error)
                    return
                }
                if (!gameStarted) {
                    gameStarted = true
                    showGameTable()
                    startAudio()
                }
                val received = clientState ?: return
                if (previousRound != null && received.round > previousRound) {
                    gameAudio.playCardsCollected()
                }
                renderClientState(received)
            }
        }
    }

    private fun showProtocolError(error: Exception) {
        Log.e(TAG, "Invalid War multiplayer message", error)
        Toast.makeText(this, "התקבלה הודעה לא תקינה מהמשחק", Toast.LENGTH_LONG).show()
    }

    private fun sendLobbyUpdates() {
        remotePlayerIds.keys.forEach { endpointId ->
            val player = remotePlayerIds[endpointId] ?: return@forEach
            val names = listOf(deviceDisplayName()) + remotePlayerIds.entries
                .sortedBy { it.value.drop(1).toInt() }
                .map { remotePlayerNames[it.key] ?: "שחקן" }
            nearbySession?.send(endpointId, WarMessageCodec.lobbyBytes(player, names, hostCapacity))
        }
    }

    private fun refreshHostLobby() {
        val players = listOf(deviceDisplayName()) + remotePlayerNames.values
        updateLobby(
            "שחקנים (${players.size}/$hostCapacity): ${players.joinToString(" · ")}",
            remotePlayerIds.isNotEmpty()
        )
    }

    private fun showDiscoveredEndpoints() {
        if (role != Role.CLIENT) return
        lobbyPlayers?.removeAllViews()
        if (discoveredEndpoints.isEmpty()) {
            updateLobby("מחפש משחקים בקרבת מקום...", true)
            return
        }
        updateLobby("בחר חדר להצטרפות:", false)
        discoveredEndpoints.forEach { (endpointId, name) ->
            lobbyPlayers?.addView(Button(this).apply {
                text = "הצטרף אל $name"
                setOnClickListener {
                    lobbyActionButton?.isEnabled = false
                    updateLobby("מתחבר אל $name...", false)
                    nearbySession?.requestConnection(deviceDisplayName(), endpointId)
                }
            })
        }
    }

    private fun showLobby(
        title: String,
        message: String,
        actionText: String,
        actionEnabled: Boolean = true,
        action: () -> Unit
    ) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(40), dp(28), dp(28))
            setBackgroundColor(0xFF0F172A.toInt())
        }
        content.addView(TextView(this).apply {
            text = title
            textSize = 26f
            setTextColor(0xFFF1F5F9.toInt())
            gravity = Gravity.CENTER
        })
        lobbyMessage = TextView(this).apply {
            textSize = 16f
            setTextColor(0xFFCBD5E1.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, dp(16))
        }
        lobbyPlayers = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        lobbyActionButton = Button(this).apply {
            text = actionText
            isEnabled = actionEnabled
            setOnClickListener { lobbyAction?.invoke() }
        }
        content.addView(lobbyMessage)
        content.addView(lobbyPlayers)
        content.addView(lobbyActionButton)
        content.addView(Button(this).apply {
            text = "ביטול"
            setOnClickListener { finish() }
        })
        setContentView(content)
        lobbyAction = action
        updateLobby(message, actionEnabled)
    }

    private fun updateLobby(message: String, actionEnabled: Boolean) {
        lobbyMessage?.text = message
        lobbyActionButton?.isEnabled = actionEnabled
    }

    private fun startAudio() {
        gameAudio.startGame(
            music = preferences.getBoolean(KEY_BACKGROUND_MUSIC, true),
            effects = preferences.getBoolean(KEY_SOUND_EFFECTS, true),
            musicVolume = MULTIPLAYER_MUSIC_VOLUME
        )
    }

    private fun showExternalPresentation() {
        val displays = (getSystemService(DISPLAY_SERVICE) as DisplayManager)
            .getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        val display = displays.firstOrNull() ?: return
        try {
            presentation?.dismiss()
            presentation = WarMultiplayerPresentation(this, display).also { it.show() }
        } catch (error: RuntimeException) {
            Log.e(TAG, "Unable to show War on the external display", error)
        }
    }

    private fun requiredPermissions(): List<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.NEARBY_WIFI_DEVICES
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT
        )
        else -> listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun withNearbyPermissions(action: () -> Unit) {
        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            action()
        } else {
            permissionAction = action
            requestPermissions(missing.toTypedArray(), PERMISSIONS_REQUEST)
        }
    }

    @Deprecated("Deprecated in Android, retained for compatibility with the min SDK")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PERMISSIONS_REQUEST) return
        val action = permissionAction
        permissionAction = null
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            action?.invoke()
        } else {
            Toast.makeText(this, "נדרשות הרשאות בקרבת מקום למשחק ברשת", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun deviceDisplayName(): String =
        "${Build.MANUFACTURER} ${Build.MODEL}".take(32)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onResume() {
        super.onResume()
        if (gameStarted) startAudio()
    }

    override fun onPause() {
        gameAudio.stopGame()
        super.onPause()
    }

    override fun onDestroy() {
        nearbySession?.stop()
        gameAudio.stopGame()
        try {
            presentation?.dismiss()
        } catch (error: RuntimeException) {
            Log.e(TAG, "Unable to dismiss War presentation", error)
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_MODE = "war_multiplayer_mode"
        const val MODE_HOST = "host"
        const val MODE_JOIN = "join"

        private const val TAG = "WarMultiplayer"
        private const val SERVICE_ID = "com.gamebox.war.multiplayer"
        private const val SETTINGS_PREFERENCES = "game_box_settings"
        private const val KEY_BACKGROUND_MUSIC = "background_music_enabled"
        private const val KEY_SOUND_EFFECTS = "sound_effects_enabled"
        private const val PERMISSIONS_REQUEST = 702
        private const val DEFAULT_PLAYER_COUNT = 4
        private const val MULTIPLAYER_MUSIC_VOLUME = 0.075
        private const val STATUS_TAG = "war_status"
        private const val ROUND_TAG = "war_round"
        private const val PLAYER_LIST_TAG = "war_players"
        private const val REVEAL_BUTTON_TAG = "war_reveal"
    }
}
