package com.gamebox.war

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.gamebox.common.ui.PlayingCardView

class WarMultiplayerPresentation(context: Context, display: Display) :
    Presentation(context, display) {

    private lateinit var playerList: LinearLayout
    private lateinit var status: TextView
    private lateinit var roundStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(32, 24, 32, 24)
            setBackgroundColor(0xFF0C4A6E.toInt())
        }
        root.addView(TextView(context).apply {
            text = "מלחמה"
            textSize = 34f
            setTextColor(0xFFF1F5F9.toInt())
            gravity = Gravity.CENTER
        })
        root.addView(TextView(context).apply {
            text = context.getString(R.string.app_version_label, BuildConfig.VERSION_NAME)
            textSize = 11f
            setTextColor(0xFF94A3B8.toInt())
            gravity = Gravity.CENTER
        })
        status = TextView(context).apply {
            textSize = 24f
            setTextColor(0xFFFDE68A.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 8)
        }
        roundStatus = TextView(context).apply {
            textSize = 18f
            setTextColor(0xFFE0F2FE.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 12)
        }
        playerList = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(status)
        root.addView(roundStatus)
        root.addView(ScrollView(context).apply {
            addView(playerList)
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
        setContentView(root)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    fun update(
        players: List<WarMultiplayerPublicPlayer>,
        readyIds: Set<String>,
        cards: Map<String, Card>,
        round: Int,
        gameOver: Boolean,
        message: String
    ) {
        if (!::playerList.isInitialized) return
        status.text = message
        val active = players.count { it.cardCount > 0 }
        val ready = readyIds.count { id -> players.any { it.id == id && it.cardCount > 0 } }
        roundStatus.text = if (gameOver) "המשחק הסתיים" else
            "סיבוב ${round + 1} · $ready מתוך $active שחקנים מוכנים"

        playerList.removeAllViews()
        players.chunked(3).forEach { group ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            group.forEach { player ->
                val column = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(18, 12, 18, 12)
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                }
                column.addView(TextView(context).apply {
                    text = player.name
                    textSize = 18f
                    setTextColor(0xFFF1F5F9.toInt())
                    gravity = Gravity.CENTER
                })
                column.addView(PlayingCardView(context).apply {
                    cards[player.id]?.let { showCard(it.rank, it.suit, it.isRed) }
                        ?: showCardBack()
                }, LinearLayout.LayoutParams(120, 168).apply {
                    topMargin = 8
                    bottomMargin = 8
                })
                column.addView(TextView(context).apply {
                    text = "קלפים: ${player.cardCount}" +
                        if (player.id in readyIds) " · מוכן" else ""
                    textSize = 16f
                    setTextColor(0xFFE0F2FE.toInt())
                    gravity = Gravity.CENTER
                })
                row.addView(column)
            }
            playerList.addView(row)
        }
    }
}
