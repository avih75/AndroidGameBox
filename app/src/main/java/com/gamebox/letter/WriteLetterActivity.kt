package com.gamebox.letter

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class WriteLetterActivity : Activity(), TextToSpeech.OnInitListener {
    private var language = LetterLanguage.ENGLISH
    private var questionDeck = emptyList<LetterQuestion>()
    private var question: LetterQuestion? = null
    private var points = 0
    private var nextHintIndex = 0
    private var roundCompleted = false
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var presentation: LetterWritingPresentation? = null
    private var presentationDisplay: Display? = null
    private lateinit var picture: TextView
    private lateinit var prompt: TextView
    private lateinit var score: TextView
    private lateinit var message: TextView
    private lateinit var letterHint: TextView
    private lateinit var canvas: LetterWritingCanvas
    private lateinit var hintButtons: List<Button>
    private lateinit var doneButton: Button
    private lateinit var nextButton: Button

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = updateExternalDisplay()

        override fun onDisplayRemoved(displayId: Int) {
            if (presentationDisplay?.displayId == displayId) {
                presentation?.dismiss()
                presentation = null
                presentationDisplay = null
            }
        }

        override fun onDisplayChanged(displayId: Int) = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        (getSystemService(DISPLAY_SERVICE) as DisplayManager)
            .registerDisplayListener(displayListener, null)
        chooseLanguage()
    }

    override fun onInit(statusCode: Int) {
        ttsReady = statusCode == TextToSpeech.SUCCESS
    }

    private fun chooseLanguage() {
        AlertDialog.Builder(this)
            .setTitle("Choose the game language / בחרו שפה")
            .setItems(arrayOf("English", "עברית")) { _, choice ->
                language = if (choice == 0) LetterLanguage.ENGLISH else LetterLanguage.HEBREW
                questionDeck = LetterQuestionBank.questions.shuffled()
                startRound()
                buildScreen()
                updateExternalDisplay()
            }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun startRound() {
        if (questionDeck.isEmpty()) questionDeck = LetterQuestionBank.questions.shuffled()
        question = questionDeck.first()
        questionDeck = questionDeck.drop(1)
        nextHintIndex = 0
        roundCompleted = false
        canvasOrNull()?.clear()
        messageText = if (language == LetterLanguage.ENGLISH) {
            "Write the first letter of the picture's name."
        } else {
            "כתבו את האות הראשונה בשם של התמונה."
        }
        letterHintText = ""
        updateExternalDisplay()
    }

    private var messageText = ""
    private var letterHintText = ""

    private fun buildScreen() {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(Color.rgb(15, 23, 42))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(12), dp(18), dp(18))
        }
        val title = TextView(this).apply {
            text = if (language == LetterLanguage.ENGLISH) "Write the letter" else "כתיבת האות"
            textSize = 25f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        score = TextView(this).apply {
            textSize = 18f
            setTextColor(Color.rgb(134, 239, 172))
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, dp(5))
        }
        picture = TextView(this).apply {
            textSize = 50f
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, 0)
        }
        prompt = TextView(this).apply {
            textSize = 17f
            setTextColor(Color.rgb(203, 213, 225))
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(5))
        }
        canvas = LetterWritingCanvas(this).apply {
            minimumHeight = dp(220)
        }
        val canvasParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(240)
        ).apply {
            setMargins(0, dp(6), 0, dp(6))
        }
        message = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.rgb(203, 213, 225))
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(4))
        }
        letterHint = TextView(this).apply {
            textSize = 42f
            setTextColor(Color.rgb(134, 239, 172))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        content.addView(title, matchWrap())
        content.addView(score, matchWrap())
        content.addView(picture, matchWrap())
        content.addView(prompt, matchWrap())
        content.addView(canvas, canvasParams)
        content.addView(message, matchWrap())
        content.addView(letterHint, matchWrap())

        val hintRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        hintButtons = listOf(
            hintButton(if (language == LetterLanguage.ENGLISH) "1 · Word" else "1 · מילה", ::hintWord),
            hintButton(if (language == LetterLanguage.ENGLISH) "2 · Letter" else "2 · אות", ::hintLetter),
            hintButton(if (language == LetterLanguage.ENGLISH) "3 · Show" else "3 · הצג", ::hintShowLetter),
            hintButton(if (language == LetterLanguage.ENGLISH) "4 · Trace" else "4 · עקבו", ::hintTrace)
        )
        hintButtons.forEach { hintRow.addView(it, weightedButton()) }
        content.addView(hintRow, matchWrap())

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        actions.addView(Button(this).apply {
            text = if (language == LetterLanguage.ENGLISH) "Clear" else "נקה"
            isAllCaps = false
            setOnClickListener {
                if (roundCompleted) return@setOnClickListener
                canvas.clear()
                refreshScreen()
            }
        }, weightedButton())
        doneButton = Button(this).apply {
            text = if (language == LetterLanguage.ENGLISH) "I wrote it" else "סיימתי לכתוב"
            isAllCaps = false
            setOnClickListener { finishRound() }
        }
        actions.addView(doneButton, weightedButton())
        nextButton = Button(this).apply {
            text = if (language == LetterLanguage.ENGLISH) "Next picture" else "התמונה הבאה"
            isAllCaps = false
            setOnClickListener {
                startRound()
                refreshScreen()
            }
        }
        actions.addView(nextButton, weightedButton())
        content.addView(actions, matchWrap())

        scroll.addView(content)
        setContentView(scroll)
        refreshScreen()
    }

    private fun hintButton(label: String, onClick: () -> Unit) =
        Button(this).apply {
            text = label
            textSize = 11f
            isAllCaps = false
            setPadding(dp(2), 0, dp(2), 0)
            setOnClickListener { onClick() }
        }

    private fun hintWord() {
        if (nextHintIndex != 0) return
        nextHintIndex = 1
        speak(question?.word(language).orEmpty())
        messageText = if (language == LetterLanguage.ENGLISH) {
            "Listen to the word, then write its first letter."
        } else {
            "הקשיבו למילה וכתבו את האות הראשונה שלה."
        }
        refreshScreen()
    }

    private fun hintLetter() {
        if (nextHintIndex != 1) return
        nextHintIndex = 2
        speak(question?.letter(language).orEmpty())
        messageText = if (language == LetterLanguage.ENGLISH) {
            "Listen to the letter."
        } else {
            "הקשיבו לשם האות."
        }
        refreshScreen()
    }

    private fun hintShowLetter() {
        if (nextHintIndex != 2) return
        nextHintIndex = 3
        val letter = question?.letter(language).orEmpty()
        letterHintText = letter
        canvas.showLetter(letter)
        messageText = if (language == LetterLanguage.ENGLISH) {
            "Here is the letter. Try writing it in the practice area."
        } else {
            "הנה האות. נסו לכתוב אותה באזור התרגול."
        }
        refreshScreen()
    }

    private fun hintTrace() {
        if (nextHintIndex != 3) return
        nextHintIndex = 4
        val letter = question?.letter(language).orEmpty()
        letterHintText = ""
        canvas.showWritingDemo(letter)
        messageText = if (language == LetterLanguage.ENGLISH) {
            "Watch the letter shape being traced, then try it yourself."
        } else {
            "צפו בצורה של האות בזמן העקיבה, ואז נסו בעצמכם."
        }
        refreshScreen()
    }

    private fun speak(text: String) {
        if (!ttsReady) {
            Toast.makeText(this, "Text-to-speech is not ready on this device.", Toast.LENGTH_LONG).show()
            return
        }
        val locale = if (language == LetterLanguage.ENGLISH) Locale.US else Locale("he", "IL")
        val languageResult = tts?.setLanguage(locale) ?: TextToSpeech.ERROR
        if (languageResult < TextToSpeech.LANG_AVAILABLE) {
            Toast.makeText(this, "Text-to-speech does not support this language on this device.", Toast.LENGTH_LONG).show()
            return
        }
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "write-letter-hint")
        if (result == null || result == TextToSpeech.ERROR) {
            Toast.makeText(this, "Could not speak the hint on this device.", Toast.LENGTH_LONG).show()
        }
    }

    private fun finishRound() {
        if (roundCompleted) return
        if (!canvas.hasWriting()) {
            messageText = if (language == LetterLanguage.ENGLISH) {
                "Write the letter in the practice area first."
            } else {
                "כתבו את האות באזור התרגול קודם."
            }
            refreshScreen()
            return
        }
        roundCompleted = true
        points += 1
        messageText = if (language == LetterLanguage.ENGLISH) {
            "Nice writing practice! +1 point."
        } else {
            "כל הכבוד על התרגול! נקודה אחת."
        }
        refreshScreen()
    }

    private fun refreshScreen() {
        val item = question ?: return
        picture.text = item.picture
        prompt.text = if (language == LetterLanguage.ENGLISH) {
            "Write the first letter of the picture's name."
        } else {
            "כתבו את האות הראשונה בשם של התמונה."
        }
        score.text = if (language == LetterLanguage.ENGLISH) "Practice points: $points" else "נקודות תרגול: $points"
        message.text = messageText
        letterHint.text = letterHintText
        hintButtons.forEachIndexed { index, button ->
            button.isEnabled = !roundCompleted && index == nextHintIndex
        }
        doneButton.isEnabled = !roundCompleted
        nextButton.visibility = if (roundCompleted) View.VISIBLE else View.GONE
    }

    private fun updateExternalDisplay() {
        val manager = getSystemService(DISPLAY_SERVICE) as DisplayManager
        val display = manager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull()
            ?: manager.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
        if (display == null) {
            presentation?.dismiss()
            presentation = null
            presentationDisplay = null
            return
        }
        if (presentationDisplay?.displayId != display.displayId) {
            presentation?.dismiss()
            presentation = LetterWritingPresentation(this, display).also { it.show() }
            presentationDisplay = display
        }
        question?.let { presentation?.update(it, language) }
    }

    private fun canvasOrNull(): LetterWritingCanvas? =
        if (::canvas.isInitialized) canvas else null

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun weightedButton() = LinearLayout.LayoutParams(
        0,
        dp(48),
        1f
    ).apply {
        setMargins(dp(2), dp(3), dp(2), dp(3))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        (getSystemService(DISPLAY_SERVICE) as DisplayManager)
            .unregisterDisplayListener(displayListener)
        presentation?.dismiss()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
