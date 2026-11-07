package com.gamebox.letter

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Display
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

class LetterTablePresentation(context: Context, display: Display) : Presentation(context, display) {
    private lateinit var picture: TextView
    private lateinit var question: TextView
    private lateinit var score: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 24, 32, 24)
            setBackgroundColor(Color.rgb(15, 23, 42))
        }
        picture = TextView(context).apply {
            gravity = Gravity.CENTER
            textSize = 112f
        }
        question = TextView(context).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 38f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 20, 0, 12)
        }
        score = TextView(context).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(134, 239, 172))
            textSize = 26f
        }
        root.addView(picture, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        root.addView(question)
        root.addView(score)
        setContentView(root)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    fun update(item: LetterQuestion, language: LetterLanguage, points: Int) {
        picture.text = item.picture
        question.text = if (language == LetterLanguage.ENGLISH) {
            "What is the first letter?"
        } else {
            "מה האות הראשונה?"
        }
        score.text = if (language == LetterLanguage.ENGLISH) "Score: $points" else "ניקוד: $points"
    }
}
