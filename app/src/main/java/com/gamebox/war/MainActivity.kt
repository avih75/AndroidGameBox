package com.gamebox.war

import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private enum class Screen { HOME, GAME_SETUP, SETTINGS, GAME }

    private val preferences by lazy {
        getSharedPreferences("game_box_settings", MODE_PRIVATE)
    }

    private var currentScreen = Screen.HOME
    private lateinit var game: GameEngine
    private var presentation: TablePresentation? = null
    private var presentationDisplay: Display? = null

    private lateinit var tvPlayerCount: TextView
    private lateinit var tvCpuCount: TextView
    private lateinit var cardPlayer: TextView
    private lateinit var cardCpu: TextView
    private lateinit var tvMessage: TextView
    private lateinit var tvDisplayStatus: TextView
    private lateinit var btnDraw: Button
    private lateinit var btnNewGame: Button

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
                    itemView.setOnClickListener { showGameSetup() }
                }
                catalog.addView(itemView)
            }
        }
        findViewById<Button>(R.id.btnOpenSettings).setOnClickListener { showSettings() }
    }

    private fun showGameSetup() {
        currentScreen = Screen.GAME_SETUP
        dismissPresentation()
        setContentView(R.layout.screen_game_setup)
        findViewById<Button>(R.id.btnStartWar).setOnClickListener { startWarGame() }
        findViewById<Button>(R.id.btnSetupBack).setOnClickListener { showHome() }
    }

    private fun showSettings() {
        currentScreen = Screen.SETTINGS
        dismissPresentation()
        setContentView(R.layout.screen_settings)

        val externalDisplaySetting = findViewById<CheckBox>(R.id.checkboxExternalDisplay)
        externalDisplaySetting.isChecked = usesExternalDisplay()
        externalDisplaySetting.setOnCheckedChangeListener { _, isChecked ->
            preferences.edit().putBoolean(KEY_EXTERNAL_DISPLAY, isChecked).apply()
            if (!isChecked) {
                dismissPresentation()
            }
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
        tvDisplayStatus = findViewById(R.id.tvDisplayStatus)
        btnDraw = findViewById(R.id.btnDraw)
        btnNewGame = findViewById(R.id.btnNewGame)

        game.newGame()
        btnDraw.setOnClickListener {
            if (game.draw()) updateUI()
        }
        btnNewGame.setOnClickListener {
            game.newGame()
            updateUI()
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
            }
        }
    }

    private fun showPresentation(display: Display) {
        try {
            presentation = TablePresentation(this, display)
            presentation?.show()
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
    }

    private fun updateUI() {
        if (currentScreen != Screen.GAME) return

        tvPlayerCount.text = "אתה: ${game.playerDeck.size}"
        tvCpuCount.text = "מחשב: ${game.cpuDeck.size}"
        tvMessage.text = game.lastMessage

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
            messageType = game.lastMessageType
        )
    }

    private fun renderCard(view: TextView, card: Card?) {
        if (card == null) {
            view.text = "?"
            view.setTextColor(0xFF111111.toInt())
        } else {
            view.text = card.display()
            view.setTextColor(if (card.isRed) 0xFFDC2626.toInt() else 0xFF111111.toInt())
        }
    }

    override fun onResume() {
        super.onResume()
        if (currentScreen == Screen.GAME) checkExternalDisplay()
    }

    override fun onDestroy() {
        val dm = getSystemService(DISPLAY_SERVICE) as DisplayManager
        dm.unregisterDisplayListener(displayListener)
        dismissPresentation()
        super.onDestroy()
    }

    private fun usesExternalDisplay(): Boolean =
        preferences.getBoolean(KEY_EXTERNAL_DISPLAY, true)

    companion object {
        private const val KEY_EXTERNAL_DISPLAY = "use_external_display"
    }
}
