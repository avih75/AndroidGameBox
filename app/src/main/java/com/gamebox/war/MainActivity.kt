package com.gamebox.war

import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import android.view.LayoutInflater
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.gamebox.common.audio.GameAudioController
import com.gamebox.common.ui.PlayingCardView

class MainActivity : AppCompatActivity() {

    private enum class Screen { HOME, GAME_SETUP, SETTINGS, GAME }

    private val preferences by lazy {
        getSharedPreferences("game_box_settings", MODE_PRIVATE)
    }

    private var currentScreen = Screen.HOME
    private lateinit var game: GameEngine
    private val gameAudio = GameAudioController()
    private var presentation: TablePresentation? = null
    private var presentationDisplay: Display? = null

    private lateinit var tvPlayerCount: TextView
    private lateinit var tvCpuCount: TextView
    private lateinit var cardPlayer: PlayingCardView
    private lateinit var cardCpu: PlayingCardView
    private lateinit var tvMessage: TextView
    private lateinit var tvPrivatePlayerCount: TextView
    private lateinit var tvSeriesScore: TextView
    private lateinit var tvPublicMessageStatus: TextView
    private lateinit var tvDisplayStatus: TextView
    private lateinit var btnDraw: Button
    private lateinit var btnNewGame: Button
    private lateinit var btnResetSeries: Button
    private lateinit var btnSendPublicMessage: Button
    private lateinit var publicMessageInput: AutoCompleteTextView
    private var playerSeriesScore = 0
    private var cpuSeriesScore = 0
    private var matchScoreRecorded = false
    private var publicMessage: String? = null

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            if (currentScreen == Screen.GAME) checkExternalDisplay()
        }

        override fun onDisplayRemoved(displayId: Int) {
            if (presentationDisplay?.displayId == displayId) {
                dismissPresentation()
                if (currentScreen == Screen.GAME) {
                    tvDisplayStatus.text = getString(R.string.external_display_disconnected)
                }
            }
        }

        override fun onDisplayChanged(displayId: Int) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        game = GameEngine()
        playerSeriesScore = preferences.getInt(KEY_PLAYER_SERIES_SCORE, 0)
        cpuSeriesScore = preferences.getInt(KEY_CPU_SERIES_SCORE, 0)

        val dm = getSystemService(DISPLAY_SERVICE) as DisplayManager
        dm.registerDisplayListener(displayListener, null)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (currentScreen) {
                    Screen.HOME -> finish()
                    Screen.GAME_SETUP, Screen.SETTINGS -> showHome()
                    Screen.GAME -> showHome()
                }
            }
        })

        showHome()
    }

    private fun showHome() {
        currentScreen = Screen.HOME
        gameAudio.stopGame()
        dismissPresentation()
        setContentView(R.layout.screen_home)
        val catalog = findViewById<LinearLayout>(R.id.gameCatalogContainer)
        val inflater = LayoutInflater.from(this)
        GameCatalog.categories.forEach { category ->
            val heading = TextView(this).apply {
                setText(category.title)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text))
                textSize = 20f
                setPadding(0, resources.getDimensionPixelSize(R.dimen.catalog_section_top), 0,
                    resources.getDimensionPixelSize(R.dimen.catalog_section_bottom))
            }
            catalog.addView(heading)

            category.games.forEach { gameItem ->
                val itemView = inflater.inflate(R.layout.item_game_catalog, catalog, false)
                itemView.findViewById<ImageView>(R.id.ivGameArtwork)
                    .setImageResource(gameItem.artwork)
                itemView.findViewById<TextView>(R.id.tvGameTitle).setText(gameItem.title)
                itemView.findViewById<TextView>(R.id.tvGameDescription).setText(gameItem.description)
                itemView.findViewById<TextView>(R.id.tvGameStatus).text = getString(
                    if (gameItem.isAvailable) R.string.game_available_status else R.string.game_planned_status,
                    getString(gameItem.effort)
                )
                itemView.alpha = if (gameItem.isAvailable) 1f else 0.72f
                itemView.isClickable = gameItem.isAvailable
                itemView.isFocusable = gameItem.isAvailable
                if (gameItem.isAvailable) {
                    itemView.setOnClickListener {
                        when (gameItem.title) {
                            R.string.game_war -> showGameSetup()
                            R.string.game_taki -> {
                                startActivity(android.content.Intent(
                                    this,
                                    com.gamebox.taki.ui.phone.TakiHostActivity::class.java
                                ))
                            }
                            else -> showGameSetup()
                        }
                    }
                }
                catalog.addView(itemView)
            }
        }
        findViewById<Button>(R.id.btnOpenSettings).setOnClickListener { showSettings() }
    }

    private fun showGameSetup() {
        currentScreen = Screen.GAME_SETUP
        gameAudio.stopGame()
        dismissPresentation()
        setContentView(R.layout.screen_game_setup)
        findViewById<Button>(R.id.btnStartWar).setOnClickListener { startWarGame() }
        findViewById<Button>(R.id.btnHostWar).setOnClickListener {
            startActivity(
                android.content.Intent(this, WarMultiplayerActivity::class.java)
                    .putExtra(WarMultiplayerActivity.EXTRA_MODE, WarMultiplayerActivity.MODE_HOST)
            )
        }
        findViewById<Button>(R.id.btnJoinWar).setOnClickListener {
            startActivity(
                android.content.Intent(this, WarMultiplayerActivity::class.java)
                    .putExtra(WarMultiplayerActivity.EXTRA_MODE, WarMultiplayerActivity.MODE_JOIN)
            )
        }
        findViewById<Button>(R.id.btnSetupBack).setOnClickListener { showHome() }
    }

    private fun showSettings() {
        currentScreen = Screen.SETTINGS
        gameAudio.stopGame()
        dismissPresentation()
        setContentView(R.layout.screen_settings)

        val externalDisplaySetting = findViewById<CheckBox>(R.id.checkboxExternalDisplay)
        externalDisplaySetting.isChecked = usesExternalDisplay()
        externalDisplaySetting.setOnCheckedChangeListener { _, isChecked ->
            preferences.edit().putBoolean(KEY_EXTERNAL_DISPLAY, isChecked).apply()
            if (!isChecked) {
                dismissPresentation()
                updatePhoneSharedInfoVisibility()
            }
        }
        val showSharedInfoOnPhone = findViewById<CheckBox>(R.id.checkboxShowSharedInfoOnPhone)
        showSharedInfoOnPhone.isChecked = preferences.getBoolean(KEY_SHOW_SHARED_INFO_ON_PHONE, false)
        showSharedInfoOnPhone.setOnCheckedChangeListener { _, isChecked ->
            preferences.edit().putBoolean(KEY_SHOW_SHARED_INFO_ON_PHONE, isChecked).apply()
            updatePhoneSharedInfoVisibility()
        }
        val backgroundMusicSetting = findViewById<CheckBox>(R.id.checkboxBackgroundMusic)
        backgroundMusicSetting.isChecked = backgroundMusicEnabled()
        backgroundMusicSetting.setOnCheckedChangeListener { _, isChecked ->
            preferences.edit().putBoolean(KEY_BACKGROUND_MUSIC, isChecked).apply()
            gameAudio.updateSettings(isChecked, soundEffectsEnabled())
        }
        val soundEffectsSetting = findViewById<CheckBox>(R.id.checkboxSoundEffects)
        soundEffectsSetting.isChecked = soundEffectsEnabled()
        soundEffectsSetting.setOnCheckedChangeListener { _, isChecked ->
            preferences.edit().putBoolean(KEY_SOUND_EFFECTS, isChecked).apply()
            gameAudio.updateSettings(backgroundMusicEnabled(), isChecked)
        }
        findViewById<Button>(R.id.btnSettingsBack).setOnClickListener { showHome() }
    }

    private fun startWarGame() {
        currentScreen = Screen.GAME
        setContentView(R.layout.activity_main)

        tvPlayerCount = findViewById(R.id.tvPlayerCount)
        tvCpuCount = findViewById(R.id.tvCpuCount)
        cardPlayer = findViewById(R.id.cardPlayer)
        cardCpu = findViewById(R.id.cardCpu)
        tvMessage = findViewById(R.id.tvMessage)
        tvPrivatePlayerCount = findViewById(R.id.tvPrivatePlayerCount)
        tvSeriesScore = findViewById(R.id.tvSeriesScore)
        tvPublicMessageStatus = findViewById(R.id.tvPublicMessageStatus)
        tvDisplayStatus = findViewById(R.id.tvDisplayStatus)
        btnDraw = findViewById(R.id.btnDraw)
        btnNewGame = findViewById(R.id.btnNewGame)
        btnResetSeries = findViewById(R.id.btnResetSeries)
        btnSendPublicMessage = findViewById(R.id.btnSendPublicMessage)
        publicMessageInput = findViewById(R.id.inputPublicMessage)
        publicMessageInput.setAdapter(android.widget.ArrayAdapter.createFromResource(
            this,
            R.array.public_game_messages,
            R.layout.item_spinner_dropdown
        ))
        publicMessageInput.setOnClickListener {
            if (publicMessageInput.text.isNullOrBlank()) publicMessageInput.showDropDown()
        }
        publicMessageInput.doAfterTextChanged { updatePublicMessageControls() }

        gameAudio.startGame(backgroundMusicEnabled(), soundEffectsEnabled())
        game.newGame()
        matchScoreRecorded = false
        publicMessage = null
        publicMessageInput.text.clear()
        btnDraw.setOnClickListener {
            gameAudio.playCardDraw()
            if (game.draw()) {
                gameAudio.playCardsCollected()
                updateUI()
            }
        }
        btnNewGame.setOnClickListener {
            startNextWarGame()
        }
        btnResetSeries.setOnClickListener { resetWarSeries() }
        btnSendPublicMessage.setOnClickListener {
            if (presentation != null) {
                val message = publicMessageInput.text.toString().trim()
                if (message.isNotEmpty()) {
                    publicMessage = message
                    updateUI()
                }
            }
        }
        findViewById<Button>(R.id.btnGameMenu).setOnClickListener { showHome() }

        updateUI()
        checkExternalDisplay()
    }

    private fun checkExternalDisplay() {
        if (currentScreen != Screen.GAME || !usesExternalDisplay()) {
            dismissPresentation()
            return
        }

        val dm = getSystemService(DISPLAY_SERVICE) as DisplayManager
        val displays = dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)

        if (displays.isNotEmpty()) {
            val display = displays[0]
            if (presentation == null || presentationDisplay?.displayId != display.displayId) {
                dismissPresentation()
                showPresentation(display)
            }
        } else {
            // Fallback: try any secondary display
            val all = dm.displays
            val secondary = all.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
            if (secondary != null) {
                if (presentation == null || presentationDisplay?.displayId != secondary.displayId) {
                    dismissPresentation()
                    showPresentation(secondary)
                }
            } else {
                dismissPresentation()
                tvDisplayStatus.text = getString(R.string.external_display_disconnected)
                updatePhoneSharedInfoVisibility()
            }
        }
    }

    private fun showPresentation(display: Display) {
        try {
            val newPresentation = TablePresentation(this, display)
            newPresentation.show()
            presentation = newPresentation
            presentationDisplay = display
            tvDisplayStatus.text = getString(R.string.external_display_connected)
            updateUI()
        } catch (e: Exception) {
            tvDisplayStatus.text = getString(R.string.external_display_error)
            e.printStackTrace()
        }
    }

    private fun dismissPresentation() {
        try {
            presentation?.dismiss()
        } catch (_: Exception) {}
        presentation = null
        presentationDisplay = null
        updatePhoneSharedInfoVisibility()
    }

    private fun updateUI() {
        if (currentScreen != Screen.GAME) return
        if (game.isGameOver && !matchScoreRecorded) recordCompletedMatch()

        tvPlayerCount.text = "אתה: ${game.playerDeck.size}"
        tvCpuCount.text = "מחשב: ${game.cpuDeck.size}"
        tvPrivatePlayerCount.text = getString(R.string.private_player_card_count, game.playerDeck.size)
        tvSeriesScore.text = getString(
            R.string.series_score,
            playerSeriesScore,
            cpuSeriesScore
        )
        tvMessage.text = game.lastMessage
        updatePublicMessageControls()

        val color = when (game.lastMessageType) {
            GameEngine.MessageType.WIN -> ContextCompat.getColor(this, R.color.win)
            GameEngine.MessageType.LOSE -> ContextCompat.getColor(this, R.color.lose)
            GameEngine.MessageType.WAR -> ContextCompat.getColor(this, R.color.war)
            else -> ContextCompat.getColor(this, R.color.text)
        }
        tvMessage.setTextColor(color)

        renderCard(cardPlayer, game.lastPlayerCard)
        renderCard(cardCpu, game.lastCpuCard)

        btnDraw.isEnabled = !game.isGameOver

        // Update external screen
        presentation?.update(
            playerCount = game.playerDeck.size,
            cpuCount = game.cpuDeck.size,
            playerCard = game.lastPlayerCard,
            cpuCard = game.lastCpuCard,
            message = game.lastMessage,
            messageType = game.lastMessageType,
            playerSeriesScore = playerSeriesScore,
            cpuSeriesScore = cpuSeriesScore,
            publicMessage = publicMessage
        )
        updatePhoneSharedInfoVisibility()
    }

    private fun updatePhoneSharedInfoVisibility() {
        if (currentScreen != Screen.GAME) return
        val sharedInfoVisibility =
            if (presentation != null &&
                !preferences.getBoolean(KEY_SHOW_SHARED_INFO_ON_PHONE, false)
            ) View.GONE else View.VISIBLE

        findViewById<View>(R.id.playerCounts).visibility = sharedInfoVisibility
        findViewById<View>(R.id.tvMessage).visibility = sharedInfoVisibility
        findViewById<View>(R.id.cardArea).visibility = sharedInfoVisibility
        tvSeriesScore.visibility = sharedInfoVisibility
        updatePublicMessageControls()
    }

    private fun updatePublicMessageControls() {
        if (currentScreen != Screen.GAME) return
        btnSendPublicMessage.isEnabled =
            presentation != null && publicMessageInput.text.isNotBlank()
        tvPublicMessageStatus.setText(
            when {
                presentation == null -> R.string.public_message_needs_display
                publicMessage?.let {
                    it == publicMessageInput.text.toString().trim()
                } == true -> R.string.public_message_sent
                else -> R.string.public_message_ready
            }
        )
    }

    private fun recordCompletedMatch() {
        when (game.lastMessageType) {
            GameEngine.MessageType.WIN -> playerSeriesScore++
            GameEngine.MessageType.LOSE -> cpuSeriesScore++
            else -> return
        }
        matchScoreRecorded = true
        preferences.edit()
            .putInt(KEY_PLAYER_SERIES_SCORE, playerSeriesScore)
            .putInt(KEY_CPU_SERIES_SCORE, cpuSeriesScore)
            .apply()
    }

    private fun startNextWarGame() {
        game.newGame()
        matchScoreRecorded = false
        publicMessage = null
        publicMessageInput.text.clear()
        updateUI()
    }

    private fun resetWarSeries() {
        playerSeriesScore = 0
        cpuSeriesScore = 0
        preferences.edit()
            .putInt(KEY_PLAYER_SERIES_SCORE, playerSeriesScore)
            .putInt(KEY_CPU_SERIES_SCORE, cpuSeriesScore)
            .apply()
        startNextWarGame()
    }

    private fun renderCard(view: PlayingCardView, card: Card?) {
        if (card == null) {
            view.showCardBack()
        } else {
            view.showCard(card.rank, card.suit, card.isRed)
        }
    }

    override fun onResume() {
        super.onResume()
        if (currentScreen == Screen.GAME) {
            gameAudio.startGame(backgroundMusicEnabled(), soundEffectsEnabled())
            checkExternalDisplay()
        }
    }

    override fun onPause() {
        gameAudio.stopGame()
        super.onPause()
    }

    override fun onDestroy() {
        gameAudio.stopGame()
        val dm = getSystemService(DISPLAY_SERVICE) as DisplayManager
        dm.unregisterDisplayListener(displayListener)
        dismissPresentation()
        super.onDestroy()
    }

    private fun usesExternalDisplay(): Boolean =
        preferences.getBoolean(KEY_EXTERNAL_DISPLAY, true)

    private fun backgroundMusicEnabled(): Boolean =
        preferences.getBoolean(KEY_BACKGROUND_MUSIC, true)

    private fun soundEffectsEnabled(): Boolean =
        preferences.getBoolean(KEY_SOUND_EFFECTS, true)

    companion object {
        private const val KEY_EXTERNAL_DISPLAY = "use_external_display"
        private const val KEY_SHOW_SHARED_INFO_ON_PHONE = "show_shared_info_on_phone"
        private const val KEY_BACKGROUND_MUSIC = "background_music_enabled"
        private const val KEY_SOUND_EFFECTS = "sound_effects_enabled"
        private const val KEY_PLAYER_SERIES_SCORE = "war_player_series_score"
        private const val KEY_CPU_SERIES_SCORE = "war_cpu_series_score"
    }
}
