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

class LetterWritingPresentation(context: Context, display: Display) :
    Presentation(context, display) {
    private lateinit var picture: TextView
    private lateinit var prompt: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(36, 24, 36, 24)
            setBackgroundColor(Color.rgb(15, 23, 42))
        }
        picture = TextView(context).apply {
            gravity = Gravity.CENTER
            textSize = 144f
        }
        prompt = TextView(context).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 40f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 20, 0, 0)
        }
        root.addView(
            picture,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        root.addView(prompt)
        setContentView(root)
        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    fun update(item: LetterQuestion, language: LetterLanguage) {
        picture.text = item.picture
        prompt.text = if (language == LetterLanguage.ENGLISH) {
            "Write the first letter"
        } else {
            "כתבו את האות הראשונה"
        }
    }
}
