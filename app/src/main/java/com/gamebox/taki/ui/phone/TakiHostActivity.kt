package com.gamebox.taki.ui.phone

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.text.InputFilter
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.gamebox.common.audio.GameAudioController
import com.gamebox.common.multiplayer.NearbySession
import com.gamebox.taki.domain.TakiAction
import com.gamebox.taki.domain.TakiAi
import com.gamebox.taki.domain.TakiEngine
import com.gamebox.taki.domain.TakiMultiplayerConfig
import com.gamebox.taki.domain.TakiPrivateState
import com.gamebox.taki.domain.TakiPublicState
import com.gamebox.taki.domain.TakiState
import com.gamebox.taki.ui.external.TakiTablePresentation
import com.gamebox.taki.ui.nearby.TakiMessageCodec
import org.json.JSONException
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import kotlin.random.Random

class TakiHostActivity : Activity() {

    private enum class SessionRole { LOCAL, HOST, CLIENT }

    private val engine = TakiEngine()
    private val ai = TakiAi()
    private val handler = Handler(Looper.getMainLooper())
    private val gameAudio = GameAudioController()
    private val scorePreferences by lazy { getSharedPreferences(SCORE_PREFERENCES, MODE_PRIVATE) }
    private val settingsPreferences by lazy { getSharedPreferences(SETTINGS_PREFERENCES, MODE_PRIVATE) }

    private var state: TakiState? = null
    private var phoneController: TakiPhoneController? = null
    private var presentation: TakiTablePresentation? = null
    private var nearbySession: NearbySession? = null
    private var role = SessionRole.LOCAL
    private var myPlayerId = "p0"
    private var selectedPlayerCount = DEFAULT_PLAYER_COUNT
    private var hostCapacity = DEFAULT_PLAYER_COUNT
    private var localPlayerName = "אני"
    private var winnerRecorded = false
    private var gameStarted = false
    private var clientEndpointId: String? = null
    private var clientPublicState: TakiPublicState? = null
    private var clientPrivateState: TakiPrivateState? = null
    private var clientWinCounts: Map<String, Int> = emptyMap()
    private var permissionAction: (() -> Unit)? = null

    private val remotePlayerIds = linkedMapOf<String, String>()
    private val remotePlayerNames = linkedMapOf<String, String>()
    private val discoveredEndpoints = linkedMapOf<String, String>()
    private var lobbyMessage: TextView? = null
    private var lobbyOptions: LinearLayout? = null
    private var lobbyActionButton: Button? = null
    private var lobbyAction: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedPlayerCount = scorePreferences.getInt(KEY_PLAYER_COUNT, DEFAULT_PLAYER_COUNT)
            .coerceIn(TakiMultiplayerConfig.MIN_PLAYERS, TakiMultiplayerConfig.MAX_PLAYERS)
        showGameUi()
        showModeDialog()
        checkExternalDisplay()
    }

    private fun showModeDialog() {
        AlertDialog.Builder(this)
            .setTitle("טאקי")
            .setItems(arrayOf("אירוח משחק ברשת", "הצטרפות למשחק", "משחק נגד המחשב")) { _, choice ->
                when (choice) {
                    0 -> showHostCapacityDialog()
                    1 -> showPlayerNamesDialog(
                        playerCount = 1,
                        title = "הצטרפות למשחק",
                        description = "כתוב את השם שיוצג לשחקנים האחרים"
                    ) { names ->
                        localPlayerName = names.first()
                        withNearbyPermissions { startJoinLobby() }
                    }
                    else -> showPracticeSetupDialog()
                }
            }
            .setOnCancelListener { if (state == null && !gameStarted) finish() }
            .show()
    }

    private fun showPracticeSetupDialog() {
        showPlayerCountPicker(
            title = "תרגול מול המחשב",
            description = "בחר כמה שחקנים ישתתפו (כולל אותך)"
        ) { count ->
            showPlayerNamesDialog(
                playerCount = count,
                title = "שמות השחקנים",
                description = "בחר שם לכל שחקן ולמחשב"
            ) { names -> startLocalGame(names) }
        }
    }

    private fun showHostCapacityDialog() {
        showPlayerCountPicker(
            title = "הגדרת משחק ברשת",
            description = "בחר את מספר המקומות במשחק (כולל המארח)"
        ) { capacity ->
            hostCapacity = capacity
            scorePreferences.edit().putInt(KEY_PLAYER_COUNT, capacity).apply()
            showPlayerNamesDialog(
                playerCount = 1,
                title = "שם המארח",
                description = "בחר את השם שיוצג במשחק"
            ) { names ->
                localPlayerName = names.first()
                withNearbyPermissions { startHostLobby() }
            }
        }
    }

    private fun showPlayerCountPicker(
        title: String,
        description: String,
        onSelected: (Int) -> Unit
    ) {
        val picker = NumberPicker(this).apply {
            minValue = TakiMultiplayerConfig.MIN_PLAYERS
            maxValue = TakiMultiplayerConfig.MAX_PLAYERS
            value = selectedPlayerCount
            wrapSelectorWheel = false
            setFormatter { "$it שחקנים" }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(12), dp(24), 0)
            addView(TextView(this@TakiHostActivity).apply {
                text = description
                textSize = 16f
                setPadding(0, 0, 0, dp(8))
            })
            addView(picker)
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(content)
            .setPositiveButton("המשך") { _, _ ->
                selectedPlayerCount = picker.value
                onSelected(picker.value)
            }
            .setNegativeButton("ביטול") { _, _ -> showModeDialog() }
            .show()
    }

    private fun showPlayerNamesDialog(
        playerCount: Int,
        title: String,
        description: String,
        onSelected: (List<String>) -> Unit
    ) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(12), dp(24), 0)
            addView(TextView(this@TakiHostActivity).apply {
                text = description
                textSize = 16f
                setPadding(0, 0, 0, dp(8))
            })
        }
        val fields = (0 until playerCount).map { index ->
            val defaultName = if (index == 0) "אני" else "מחשב ${index}"
            EditText(this).apply {
                setSingleLine(true)
                filters = arrayOf(InputFilter.LengthFilter(24))
                hint = if (index == 0) "שם השחקן" else "שם שחקן ${index + 1}"
                setText(scorePreferences.getString(KEY_PLAYER_NAME_PREFIX + index, defaultName))
                contentDescription = hint
                content.addView(this)
            }
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(content)
            .setPositiveButton("המשך") { _, _ ->
                val names = fields.mapIndexed { index, field ->
                    val defaultName = if (index == 0) "אני" else "מחשב ${index}"
                    field.text.toString().trim().ifEmpty { defaultName }
                }
                val editor = scorePreferences.edit()
                names.forEachIndexed { index, name ->
                    editor.putString(KEY_PLAYER_NAME_PREFIX + index, name)
                }
                editor.apply()
                onSelected(names)
            }
            .setNegativeButton("ביטול") { _, _ -> showModeDialog() }
            .show()
    }

    private fun startLocalGame(playerNames: List<String>) {
        handler.removeCallbacksAndMessages(null)
        role = SessionRole.LOCAL
        gameStarted = true
        selectedPlayerCount = playerNames.size
        scorePreferences.edit().putInt(KEY_PLAYER_COUNT, playerNames.size).apply()
        startGame(playerNames, setOf("p0"))
    }

    private fun startHostLobby() {
        role = SessionRole.HOST
        gameStarted = false
        remotePlayerIds.clear()
        remotePlayerNames.clear()
        nearbySession = createNearbySession()
        showLobbyScreen(
            title = "מארחים משחק טאקי",
            message = "מחפש שחקנים. הצטרפו מהמכשירים האחרים; ניתן להתחיל כשיש לפחות 2 שחקנים.",
            actionLabel = "התחל משחק",
            actionEnabled = false,
            onAction = ::startNetworkHostGame,
            onCancel = ::leaveNetworkLobby
        )
        nearbySession?.startAdvertising(localPlayerName)
    }

    private fun startJoinLobby() {
        role = SessionRole.CLIENT
        gameStarted = false
        discoveredEndpoints.clear()
        clientEndpointId = null
        clientPublicState = null
        clientPrivateState = null
        nearbySession = createNearbySession()
        showLobbyScreen(
            title = "הצטרפות למשחק",
            message = "מחפש משחקי טאקי בקרבת מקום...",
            actionLabel = "סרוק שוב",
            onAction = {
                discoveredEndpoints.clear()
                refreshDiscoveredDevices()
                nearbySession?.startDiscovery()
            },
            onCancel = ::leaveNetworkLobby
        )
        nearbySession?.startDiscovery()
    }

    private fun createNearbySession() = NearbySession(this, TAKI_SERVICE_ID, object : NearbySession.Listener {
        override fun onConnectionInitiated(
            endpointId: String,
            endpointName: String,
            authenticationDigits: String
        ) = runOnUiThread {
            if (role == SessionRole.HOST &&
                (gameStarted || remotePlayerIds.size >= hostCapacity - 1)
            ) {
                nearbySession?.rejectConnection(endpointId)
                return@runOnUiThread
            }
            AlertDialog.Builder(this@TakiHostActivity)
                .setTitle("אימות חיבור")
                .setMessage("האם קוד האימות $authenticationDigits זהה בשני המכשירים?\\nחיבור אל $endpointName")
                .setPositiveButton("הקוד תואם, חבר") { _, _ ->
                    nearbySession?.acceptConnection(endpointId)
                }
                .setNegativeButton("דחה") { _, _ ->
                    nearbySession?.rejectConnection(endpointId)
                }
                .setOnCancelListener { nearbySession?.rejectConnection(endpointId) }
                .show()
        }

        override fun onConnected(endpointId: String, endpointName: String) = runOnUiThread {
            if (role == SessionRole.CLIENT) {
                clientEndpointId = endpointId
                nearbySession?.stopDiscovery()
                updateLobby(
                    "התחברת אל $endpointName. ממתין למארח שיתחיל את המשחק...",
                    actionEnabled = false
                )
                return@runOnUiThread
            }
            if (role != SessionRole.HOST || gameStarted) {
                nearbySession?.disconnect(endpointId)
                return@runOnUiThread
            }
            if (remotePlayerIds.size >= hostCapacity - 1) {
                nearbySession?.disconnect(endpointId)
                return@runOnUiThread
            }
            val playerId = (1..TakiMultiplayerConfig.MAX_PLAYERS)
                .map { "p$it" }
                .first { it !in remotePlayerIds.values }
            remotePlayerIds[endpointId] = playerId
            remotePlayerNames[endpointId] = endpointName
            sendLobbyUpdates()
            refreshHostLobby()
        }

        override fun onDisconnected(endpointId: String) = runOnUiThread {
            if (role == SessionRole.CLIENT) {
                gameAudio.stopGame()
                showLobbyScreen(
                    "המשחק נותק",
                    "החיבור למארח אבד. משחק זה הסתיים.",
                    actionLabel = "חזרה",
                    onAction = { finish() },
                    onCancel = { finish() }
                )
                return@runOnUiThread
            }
            val playerId = remotePlayerIds.remove(endpointId) ?: return@runOnUiThread
            val name = remotePlayerNames.remove(endpointId) ?: playerId
            val current = state
            if (current != null && gameStarted) {
                state = current.copy(
                    players = current.players.map {
                        if (it.id == playerId) it.copy(isHuman = false) else it
                    },
                    lastActionMessage = "$name התנתק — המחשב ימשיך במקומו"
                )
                render()
                maybePlayAi()
            } else {
                remotePlayerIds.keys.toList().forEachIndexed { index, id ->
                    remotePlayerIds[id] = "p${index + 1}"
                }
                sendLobbyUpdates()
                refreshHostLobby()
            }
        }

        override fun onEndpointFound(endpointId: String, endpointName: String) = runOnUiThread {
            if (role != SessionRole.CLIENT || clientEndpointId != null) return@runOnUiThread
            discoveredEndpoints[endpointId] = endpointName
            refreshDiscoveredDevices()
        }

        override fun onEndpointLost(endpointId: String) = runOnUiThread {
            discoveredEndpoints.remove(endpointId)
            refreshDiscoveredDevices()
        }

        override fun onPayload(endpointId: String, bytes: ByteArray) = runOnUiThread {
            handleNetworkPayload(endpointId, bytes)
        }

        override fun onError(error: Exception) = runOnUiThread {
            Log.e(TAG, "Nearby Connections operation failed", error)
            Toast.makeText(
                this@TakiHostActivity,
                "שגיאת חיבור: ${error.localizedMessage ?: "נסה שוב"}",
                Toast.LENGTH_LONG
            ).show()
        }
    })

    private fun startNetworkHostGame() {
        if (role != SessionRole.HOST) return
        if (remotePlayerIds.isEmpty()) {
            Toast.makeText(this, "יש צורך בשחקן נוסף כדי להתחיל", Toast.LENGTH_SHORT).show()
            return
        }
        handler.removeCallbacksAndMessages(null)
        nearbySession?.stopAdvertising()
        remotePlayerIds.keys.toList().forEachIndexed { index, endpointId ->
            remotePlayerIds[endpointId] = "p${index + 1}"
        }
        sendLobbyUpdates()
        val players = mutableListOf(localPlayerName)
        players += remotePlayerIds.entries
            .sortedBy { it.value.drop(1).toInt() }
            .map { remotePlayerNames[it.key] ?: "שחקן" }
        val humanIds = remotePlayerIds.values.toSet() + "p0"
        gameStarted = true
        startGame(players, humanIds)
    }

    private fun startGame(playerNames: List<String>, humanPlayerIds: Set<String>) {
        winnerRecorded = false
        state = engine.newGame(
            playerNames = playerNames,
            humanPlayerIds = humanPlayerIds,
            hostPlayerId = "p0"
        )
        myPlayerId = "p0"
        clientPublicState = null
        clientPrivateState = null
        showGameUi()
        startAudio()
        render()
        maybePlayAi()
    }

    private fun onPlayerAction(action: TakiAction) {
        if (role == SessionRole.CLIENT) {
            val endpointId = clientEndpointId ?: return
            nearbySession?.send(endpointId, TakiMessageCodec.actionBytes(action))
            return
        }
        applyAction(myPlayerId, action)
    }

    private fun applyAction(playerId: String, action: TakiAction) {
        val current = state ?: return
        val updated = engine.apply(current, playerId, action)
        if (updated == current) return
        state = updated
        playActionSound(action)
        recordWinnerIfNeeded(updated)
        render()
        maybePlayAi()
    }

    private fun maybePlayAi() {
        val current = state ?: return
        if (!gameStarted || current.isGameOver || current.currentPlayer.isHuman) return

        handler.postDelayed({
            val latest = state ?: return@postDelayed
            if (!gameStarted || latest.isGameOver || latest.currentPlayer.isHuman) return@postDelayed
            val action = ai.chooseAction(latest, latest.currentPlayer.id)
            val updated = engine.apply(latest, latest.currentPlayer.id, action)
            if (updated == latest) return@postDelayed
            state = updated
            playActionSound(action)
            recordWinnerIfNeeded(updated)
            render()
            maybePlayAi()
        }, Random.nextLong(AI_TURN_MIN_DELAY_MS, AI_TURN_MAX_DELAY_MS + 1))
    }

    private fun render() {
        phoneController?.setSharedInfoVisible(presentation == null)
        if (role == SessionRole.CLIENT) {
            val public = clientPublicState ?: return
            val private = clientPrivateState ?: return
            phoneController?.render(public, private, clientWinCounts)
            return
        }
        val current = state ?: return
        val public = current.publicState()
        phoneController?.render(
            public,
            current.privateStateFor(myPlayerId),
            public.players.associate { player ->
                player.id to scorePreferences.getInt(KEY_PLAYER_WINS_PREFIX + player.id, 0)
            }
        )
        presentation?.update(public)
        if (role == SessionRole.HOST && gameStarted) broadcastGameState()
    }

    private fun handleNetworkPayload(endpointId: String, bytes: ByteArray) {
        val message = try {
            JSONObject(String(bytes, StandardCharsets.UTF_8))
        } catch (error: JSONException) {
            reportProtocolError(error)
            return
        }
        when (message.optString("type")) {
            "action" -> {
                if (role != SessionRole.HOST || !gameStarted) return
                val playerId = remotePlayerIds[endpointId] ?: return
                val action = try {
                    TakiMessageCodec.decodeAction(message)
                } catch (error: IllegalArgumentException) {
                    reportProtocolError(error)
                    return
                } catch (error: JSONException) {
                    reportProtocolError(error)
                    return
                } ?: return
                applyAction(playerId, action)
            }
            "lobby" -> {
                if (role != SessionRole.CLIENT) return
                myPlayerId = message.optString("playerId", myPlayerId)
                val players = message.optJSONArray("players")
                val names = players?.let { array ->
                    (0 until array.length()).map { array.optString(it) }
                }.orEmpty()
                val capacity = message.optInt("capacity", TakiMultiplayerConfig.MAX_PLAYERS)
                updateLobby(
                    "שחקנים בחדר (${names.size}/$capacity): ${names.joinToString(" · ")}. ממתין להתחלת המשחק...",
                    actionEnabled = false
                )
            }
            "state" -> {
                if (role != SessionRole.CLIENT) return
                val previousMessage = clientPublicState?.lastActionMessage
                try {
                    myPlayerId = message.getString("playerId")
                    clientPublicState = TakiMessageCodec.decodePublicState(message)
                    clientPrivateState = TakiMessageCodec.decodePrivateState(message)
                    clientWinCounts = TakiMessageCodec.decodeWins(message)
                } catch (error: JSONException) {
                    reportProtocolError(error)
                    return
                } catch (error: IllegalArgumentException) {
                    reportProtocolError(error)
                    return
                }
                if (!gameStarted) {
                    gameStarted = true
                    showGameUi()
                    startAudio()
                }
                val currentMessage = clientPublicState?.lastActionMessage
                if (currentMessage != null && currentMessage != previousMessage &&
                    currentMessage != "המשחק התחיל"
                ) {
                    if (currentMessage.contains("משך")) gameAudio.playCardDraw()
                    else gameAudio.playCardPlayed()
                }
                render()
            }
        }
    }

    private fun reportProtocolError(error: Exception) {
        Log.e(TAG, "Invalid Taki network message", error)
        Toast.makeText(this, "התקבלה הודעה לא תקינה מהמשחק", Toast.LENGTH_LONG).show()
    }

    private fun sendLobbyUpdates() {
        remotePlayerIds.keys.forEach(::sendLobbyUpdate)
    }

    private fun sendLobbyUpdate(endpointId: String) {
        val playerId = remotePlayerIds[endpointId] ?: return
        val names = listOf(localPlayerName) + remotePlayerNames.values
        nearbySession?.send(
            endpointId,
            TakiMessageCodec.lobbyBytes(playerId, names, hostCapacity)
        )
    }

    private fun broadcastGameState() {
        val current = state ?: return
        if (role != SessionRole.HOST || !gameStarted) return
        val public = current.publicState()
        val wins = public.players.associate { player ->
            player.id to scorePreferences.getInt(KEY_PLAYER_WINS_PREFIX + player.id, 0)
        }
        remotePlayerIds.forEach { (endpointId, playerId) ->
            nearbySession?.send(
                endpointId,
                TakiMessageCodec.stateBytes(
                    playerId,
                    public,
                    current.privateStateFor(playerId),
                    wins
                )
            )
        }
    }

    private fun recordWinnerIfNeeded(current: TakiState) {
        val winnerId = current.winnerId ?: return
        if (!current.isGameOver || winnerRecorded) return
        val key = KEY_PLAYER_WINS_PREFIX + winnerId
        scorePreferences.edit().putInt(key, scorePreferences.getInt(key, 0) + 1).apply()
        winnerRecorded = true
    }

    private fun confirmResetWins() {
        if (role == SessionRole.CLIENT) return
        AlertDialog.Builder(this)
            .setTitle("איפוס ניצחונות")
            .setMessage("לאפס את מונה הניצחונות של כל השחקנים?")
            .setNegativeButton("ביטול", null)
            .setPositiveButton("איפוס") { _, _ ->
                val editor = scorePreferences.edit()
                repeat(TakiMultiplayerConfig.MAX_PLAYERS) { index ->
                    editor.remove(KEY_PLAYER_WINS_PREFIX + "p$index")
                }
                editor.apply()
                render()
            }
            .show()
    }

    private fun playActionSound(action: TakiAction) {
        when (action) {
            is TakiAction.PlayCard -> gameAudio.playCardPlayed()
            TakiAction.Draw -> gameAudio.playCardDraw()
            else -> Unit
        }
    }

    private fun startAudio() {
        gameAudio.startGame(
            music = settingsPreferences.getBoolean(KEY_BACKGROUND_MUSIC, true),
            effects = settingsPreferences.getBoolean(KEY_SOUND_EFFECTS, true),
            musicVolume = TAKI_MUSIC_VOLUME
        )
    }

    private fun showGameUi() {
        val layoutId = resources.getIdentifier("fragment_taki_phone", "layout", packageName)
        check(layoutId != 0) { "Taki phone layout is missing" }
        setContentView(layoutId)
        val root = findViewById<View>(android.R.id.content)
        phoneController = TakiPhoneController(root, ::onPlayerAction)
        root.findViewById<TextView>(viewId("tvAppVersion")).text =
            getString(com.gamebox.war.R.string.app_version_label, com.gamebox.war.BuildConfig.VERSION_NAME)
        root.findViewById<Button>(viewId("btnNewTakiGame")).setOnClickListener {
            when (role) {
                SessionRole.CLIENT -> Toast.makeText(this, "המארח שולט במשחק הבא", Toast.LENGTH_SHORT).show()
                SessionRole.HOST -> if (remotePlayerIds.isNotEmpty()) {
                    startNetworkHostGame()
                } else {
                    leaveNetworkLobby()
                }
                SessionRole.LOCAL -> showModeDialog()
            }
        }
        root.findViewById<Button>(viewId("btnResetTakiWins")).setOnClickListener { confirmResetWins() }
        if (role == SessionRole.CLIENT) root.findViewById<Button>(viewId("btnResetTakiWins")).isEnabled = false
    }

    private fun showLobbyScreen(
        title: String,
        message: String,
        actionLabel: String?,
        actionEnabled: Boolean = true,
        onAction: (() -> Unit)?,
        onCancel: () -> Unit
    ) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(40), dp(28), dp(28))
            setBackgroundColor(0xFF0F172A.toInt())
        }
        val titleView = TextView(this).apply {
            text = title
            textSize = 28f
            setTextColor(0xFFF1F5F9.toInt())
            gravity = Gravity.CENTER
        }
        lobbyMessage = TextView(this).apply {
            textSize = 17f
            setTextColor(0xFFCBD5E1.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, dp(24))
        }
        lobbyOptions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        lobbyActionButton = actionLabel?.let { label ->
            Button(this).apply {
                text = label
                isEnabled = actionEnabled
                setOnClickListener { lobbyAction?.invoke() }
            }
        }
        val cancelButton = Button(this).apply {
            text = "ביטול"
            setOnClickListener { onCancel() }
        }
        content.addView(titleView)
        content.addView(lobbyMessage)
        content.addView(lobbyOptions)
        lobbyActionButton?.let(content::addView)
        content.addView(cancelButton)
        setContentView(content)
        lobbyAction = onAction
        updateLobby(message, actionEnabled)
    }

    private fun updateLobby(message: String, actionEnabled: Boolean) {
        lobbyMessage?.text = message
        lobbyActionButton?.isEnabled = actionEnabled
    }

    private fun refreshHostLobby() {
        val connectedNames = listOf(localPlayerName) + remotePlayerNames.values
        updateLobby(
            "שחקנים (${connectedNames.size}/$hostCapacity): ${connectedNames.joinToString(" · ")}",
            actionEnabled = remotePlayerIds.isNotEmpty()
        )
    }

    private fun refreshDiscoveredDevices() {
        if (role != SessionRole.CLIENT) return
        lobbyOptions?.removeAllViews()
        if (discoveredEndpoints.isEmpty()) {
            updateLobby("מחפש משחקי טאקי בקרבת מקום...", actionEnabled = true)
            return
        }

        updateLobby("בחר משחק כדי להצטרף:", actionEnabled = false)
        discoveredEndpoints.forEach { (endpointId, endpointName) ->
            lobbyOptions?.addView(Button(this).apply {
                text = "הצטרף אל $endpointName"
                setOnClickListener {
                    lobbyActionButton?.isEnabled = false
                    updateLobby("מתחבר אל $endpointName...", actionEnabled = false)
                    nearbySession?.requestConnection(localPlayerName, endpointId)
                }
            })
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (role == SessionRole.HOST && !gameStarted) {
            showLobbyScreen(
                title = "מארחים משחק טאקי",
                message = "מחפש שחקנים בקרבת מקום...",
                actionLabel = "התחל משחק",
                actionEnabled = remotePlayerIds.isNotEmpty(),
                onAction = ::startNetworkHostGame,
                onCancel = ::leaveNetworkLobby
            )
            refreshHostLobby()
        } else if (role == SessionRole.CLIENT && !gameStarted) {
            showLobbyScreen(
                title = "הצטרפות למשחק",
                message = "מחפש משחקי טאקי בקרבת מקום...",
                actionLabel = "סרוק שוב",
                onAction = {
                    discoveredEndpoints.clear()
                    nearbySession?.startDiscovery()
                    refreshDiscoveredDevices()
                },
                onCancel = ::leaveNetworkLobby
            )
            refreshDiscoveredDevices()
        } else {
            showGameUi()
            render()
        }
        checkExternalDisplay()
    }

    private fun leaveNetworkLobby() {
        nearbySession?.stop()
        nearbySession = null
        remotePlayerIds.clear()
        remotePlayerNames.clear()
        discoveredEndpoints.clear()
        role = SessionRole.LOCAL
        gameStarted = false
        showModeDialog()
    }

    private fun withNearbyPermissions(action: () -> Unit) {
        val missing = requiredNearbyPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            action()
            return
        }
        permissionAction = action
        requestPermissions(missing.toTypedArray(), REQUEST_NEARBY_PERMISSIONS)
    }

    private fun requiredNearbyPermissions(): List<String> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.NEARBY_WIFI_DEVICES
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        }
        return listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    @Deprecated("Deprecated in Android, retained for compatibility with the min SDK")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_NEARBY_PERMISSIONS) return
        val action = permissionAction
        permissionAction = null
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            action?.invoke()
        } else {
            Toast.makeText(this, "נדרשות הרשאות בקרבת מקום למשחק ברשת", Toast.LENGTH_LONG).show()
            showModeDialog()
        }
    }

    private fun deviceDisplayName(): String =
        "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".take(32)

    private fun checkExternalDisplay() {
        val dm = getSystemService(DISPLAY_SERVICE) as DisplayManager
        val displays = dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        val secondary = displays.firstOrNull()
            ?: dm.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
        if (secondary == null) {
            presentation?.dismiss()
            presentation = null
            phoneController?.setSharedInfoVisible(true)
            return
        }

        try {
            presentation?.dismiss()
            val newPresentation = TakiTablePresentation(this, secondary)
            newPresentation.show()
            presentation = newPresentation
            phoneController?.setSharedInfoVisible(false)
            render()
        } catch (error: RuntimeException) {
            presentation?.dismiss()
            presentation = null
            phoneController?.setSharedInfoVisible(true)
            Log.e(TAG, "Unable to show Taki on external display", error)
        }
    }

    private fun viewId(name: String): Int = resources.getIdentifier(name, "id", packageName)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onResume() {
        super.onResume()
        if (gameStarted) startAudio()
        checkExternalDisplay()
    }

    override fun onPause() {
        gameAudio.stopGame()
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        nearbySession?.stop()
        gameAudio.stopGame()
        try {
            presentation?.dismiss()
        } catch (error: RuntimeException) {
            Log.e(TAG, "Unable to dismiss Taki presentation", error)
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "TakiHostActivity"
        private const val DEFAULT_PLAYER_COUNT = 3
        private const val REQUEST_NEARBY_PERMISSIONS = 701
        private const val AI_TURN_MIN_DELAY_MS = 2_000L
        private const val AI_TURN_MAX_DELAY_MS = 4_000L
        private const val TAKI_MUSIC_VOLUME = 0.06
        private const val TAKI_SERVICE_ID = "com.gamebox.taki"
        private const val SCORE_PREFERENCES = "taki_scores"
        private const val SETTINGS_PREFERENCES = "game_box_settings"
        private const val KEY_PLAYER_COUNT = "player_count"
        private const val KEY_PLAYER_NAME_PREFIX = "player_name_"
        private const val KEY_PLAYER_WINS_PREFIX = "wins_"
        private const val KEY_BACKGROUND_MUSIC = "background_music_enabled"
        private const val KEY_SOUND_EFFECTS = "sound_effects_enabled"
    }
}
