package com.gamebox.taki.ui.phone

import android.app.Activity
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.View
import com.gamebox.games.taki.domain.TakiAction
import com.gamebox.games.taki.domain.TakiAi
import com.gamebox.games.taki.domain.TakiEngine
import com.gamebox.games.taki.domain.TakiState
import com.gamebox.games.taki.ui.external.TakiTablePresentation

/**
 * Host activity for single-player / local Taki.
 * Runs the engine, drives phone UI + external Presentation, and plays AI turns.
 *
 * In the real project this should become a Fragment or be integrated
 * with the existing navigation and display helper.
 */
class TakiHostActivity : Activity() {

    private val engine = TakiEngine()
    private val ai = TakiAi()
    private val handler = Handler(Looper.getMainLooper())

    private var state: TakiState? = null
    private var phoneController: TakiPhoneController? = null
    private var presentation: TakiTablePresentation? = null
    private var myPlayerId: String = "p0"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // In real project: setContentView(R.layout.fragment_taki_phone)
        val layoutId = resources.getIdentifier("fragment_taki_phone", "layout", packageName)
        if (layoutId != 0) setContentView(layoutId)

        val root = findViewById<View>(android.R.id.content)
        phoneController = TakiPhoneController(root) { action ->
            onPlayerAction(action)
        }

        startNewGame(aiCount = 2) // 1 human + 2 AI by default
        checkExternalDisplay()
    }

    private fun startNewGame(aiCount: Int) {
        val names = mutableListOf("אני")
        repeat(aiCount) { names += "מחשב ${it + 1}" }

        state = engine.newGame(
            playerNames = names,
            humanPlayerIds = setOf("p0"),
            hostPlayerId = "p0"
        )
        myPlayerId = "p0"
        render()
        maybePlayAi()
    }

    private fun onPlayerAction(action: TakiAction) {
        val s = state ?: return
        state = engine.apply(s, myPlayerId, action)
        render()
        maybePlayAi()
    }

    private fun maybePlayAi() {
        val s = state ?: return
        if (s.isGameOver) return
        if (s.currentPlayer.isHuman) return

        handler.postDelayed({
            val current = state ?: return@postDelayed
            if (current.isGameOver || current.currentPlayer.isHuman) return@postDelayed
            val action = ai.chooseAction(current, current.currentPlayer.id)
            state = engine.apply(current, current.currentPlayer.id, action)
            render()
            maybePlayAi() // next AI or back to human
        }, 700)
    }

    private fun render() {
        val s = state ?: return
        val public = s.publicState()
        val private = s.privateStateFor(myPlayerId)
        phoneController?.render(public, private)
        presentation?.update(public)
    }

    private fun checkExternalDisplay() {
        val dm = getSystemService(DISPLAY_SERVICE) as DisplayManager
        val displays = dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        val secondary = displays.firstOrNull()
            ?: dm.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }

        if (secondary != null) {
            try {
                presentation?.dismiss()
                presentation = TakiTablePresentation(this, secondary).also { it.show() }
                render()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try { presentation?.dismiss() } catch (_: Exception) {}
        super.onDestroy()
    }
}
