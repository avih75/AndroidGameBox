package com.gamebox.letter

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.gamebox.common.audio.GameAudioController
import com.gamebox.common.multiplayer.NearbySession
import org.json.JSONException
import org.json.JSONObject
import java.util.Locale

class LetterGameActivity : Activity(), TextToSpeech.OnInitListener {
    private enum class Role { SOLO, GUIDED_LOCAL, TEACHER_HOST, LEARNER }

    private var role = Role.SOLO
    private var language = LetterLanguage.ENGLISH
    private var question: LetterQuestion? = null
    private var questionDeck = emptyList<LetterQuestion>()
    private var options = emptyList<String>()
    private var points = 0
    private var completedQuestion = false
    private var revealed = false
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val gameAudio = GameAudioController()
    private var permissionAction: (() -> Unit)? = null
    private var nearby: NearbySession? = null
    private var discovered = linkedMapOf<String, String>()
    private var hostEndpoint: String? = null
    private var presentation: LetterTablePresentation? = null
    private var presentationDisplay: Display? = null

    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var scoreView: TextView
    private lateinit var pictureView: TextView
    private lateinit var questionView: TextView
    private lateinit var answerGrid: GridLayout
    private lateinit var helpContainer: LinearLayout
    private lateinit var nextButton: Button
    private var displayListener: DisplayManager.DisplayListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        initDisplayListener()
        showModeDialog()
    }

    override fun onInit(statusCode: Int) {
        ttsReady = statusCode == TextToSpeech.SUCCESS
    }

    private fun showModeDialog() {
        showButtonChoice(
            "נחשו את האות הראשונה · Guess the first letter",
            listOf(
                "משחק לבד · Play alone",
                "מורה מדריך, השחקן משתמש במכשיר הזה · Teacher guides on this device",
                "ארח משחק עם מורה · Host a teacher-guided game",
                "הצטרף כשחקן · Join as the learner"
            ),
            onSelected = { selected ->
                when (selected) {
                    0 -> { role = Role.SOLO; chooseLanguage() }
                    1 -> { role = Role.GUIDED_LOCAL; chooseLanguage() }
                    2 -> { role = Role.TEACHER_HOST; chooseLanguage() }
                    else -> { role = Role.LEARNER; withNearbyPermissions(::startDiscovery) }
                }
            },
            onCancel = { finish() }
        )
    }

    private fun chooseLanguage() {
        showButtonChoice(
            "Choose the game language / בחרו שפה",
            listOf("English", "עברית"),
            onSelected = { selected ->
                language = if (selected == 0) LetterLanguage.ENGLISH else LetterLanguage.HEBREW
                questionDeck = LetterQuestionBank.questions.shuffled()
                if (role == Role.TEACHER_HOST) withNearbyPermissions(::startAdvertising)
                else startLocalRound()
            }
        )
    }

    private fun startLocalRound() {
        points = 0
        startNextQuestion()
        renderGame()
        startGameSounds()
        checkExternalDisplay()
    }

    private fun startNextQuestion() {
        if (questionDeck.isEmpty()) questionDeck = LetterQuestionBank.questions.shuffled()
        question = questionDeck.first()
        questionDeck = questionDeck.drop(1)
        options = LetterQuestionBank.alphabet(language).shuffled()
        completedQuestion = false
        revealed = false
        statusText = if (language == LetterLanguage.ENGLISH) {
            "Choose the first letter of the word."
        } else {
            "בחרו את האות הראשונה של המילה."
        }
        updatePresentation()
    }

    private var statusText = ""

    private fun renderGame() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(12))
            setBackgroundColor(Color.rgb(15, 23, 42))
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val title = TextView(this).apply {
            text = if (language == LetterLanguage.HEBREW) {
                when (role) {
                    Role.GUIDED_LOCAL, Role.TEACHER_HOST -> "נחשו את האות · המורה מדריך"
                    Role.LEARNER -> "נחשו את האות · שחקן"
                    else -> "נחשו את האות הראשונה"
                }
            } else {
                when (role) {
                    Role.GUIDED_LOCAL, Role.TEACHER_HOST -> "Letter game · Teacher guides"
                    Role.LEARNER -> "Letter game · Learner"
                    else -> "Guess the first letter"
                }
            }
            textSize = 23f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        scoreView = TextView(this).apply {
            textSize = 19f
            setTextColor(Color.rgb(134, 239, 172))
            gravity = Gravity.CENTER
            setPadding(0, dp(7), 0, dp(7))
        }
        pictureView = TextView(this).apply {
            textSize = 82f
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(4))
        }
        questionView = TextView(this).apply {
            textSize = 23f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }
        status = TextView(this).apply {
            textSize = 17f
            setTextColor(Color.rgb(203, 213, 225))
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, dp(8))
        }
        content.addView(title, matchWrap())
        content.addView(scoreView, matchWrap())
        content.addView(pictureView, matchWrap())
        content.addView(questionView, matchWrap())
        content.addView(status, matchWrap())

        answerGrid = GridLayout(this).apply { columnCount = 4 }
        content.addView(answerGrid, matchWrap())
        helpContainer = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.HORIZONTAL
        }
        helpContainer.addView(helpButton(if (language == LetterLanguage.ENGLISH) "🔊 Word" else "🔊 מילה") {
            speakWord()
        }, weightedButton())
        helpContainer.addView(helpButton(if (language == LetterLanguage.ENGLISH) "4 letters" else "4 אותיות") {
            reduceChoices()
        }, weightedButton())
        helpContainer.addView(helpButton(if (language == LetterLanguage.ENGLISH) "Reveal" else "גלה") {
            revealAnswer()
        }, weightedButton())
        content.addView(helpContainer, matchWrap())

        nextButton = Button(this).apply {
            text = if (language == LetterLanguage.ENGLISH) "Next question" else "השאלה הבאה"
            isAllCaps = false
            setOnClickListener {
                if (role == Role.TEACHER_HOST) startNextNetworkQuestion() else {
                    startNextQuestion()
                    renderGame()
                }
            }
        }
        content.addView(nextButton, matchWrap())
        val menuButton = Button(this).apply {
            text = if (language == LetterLanguage.ENGLISH) "End game" else "סיום משחק"
            isAllCaps = false
            setOnClickListener {
                nearby?.stop()
                nearby = null
                finish()
            }
        }
        content.addView(menuButton, matchWrap())
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)
        refreshGameUi()
    }

    private fun refreshGameUi() {
        val item = question ?: return
        val isHebrew = language == LetterLanguage.HEBREW
        pictureView.text = item.picture
        questionView.text = if (isHebrew) "מה האות הראשונה?" else "What is the first letter?"
        updatePromptVisibility()
        scoreView.text = if (isHebrew) "ניקוד: $points" else "Score: $points"
        status.text = statusText
        status.textSize = if (completedQuestion) 20f else 17f
        status.setTextColor(
            if (completedQuestion) Color.rgb(15, 23, 42) else Color.rgb(203, 213, 225)
        )
        status.background = if (completedQuestion) {
            GradientDrawable().apply {
                setColor(if (revealed) Color.rgb(253, 230, 138) else Color.rgb(134, 239, 172))
                cornerRadius = dp(12).toFloat()
            }
        } else null
        status.setPadding(dp(8), dp(8), dp(8), dp(8))
        val learnerCanAnswer = role != Role.TEACHER_HOST
        answerGrid.visibility = if (learnerCanAnswer) View.VISIBLE else View.GONE
        renderOptions()
        helpContainer.visibility = if (learnerCanAnswer) View.VISIBLE else View.GONE
        nextButton.visibility = if (role == Role.TEACHER_HOST) {
            if (completedQuestion) View.VISIBLE else View.GONE
        } else if (role == Role.LEARNER) {
            View.GONE
        } else if (completedQuestion) View.VISIBLE else View.GONE
        updatePresentation()
    }

    private fun renderOptions() {
        answerGrid.removeAllViews()
        options.forEach { letter ->
            val button = Button(this).apply {
                text = letter
                textSize = 20f
                isAllCaps = false
                isEnabled = !completedQuestion
                if (completedQuestion) {
                    backgroundTintList = android.content.res.ColorStateList.valueOf(
                        if (letter == question?.letter(language)) {
                            Color.rgb(134, 239, 172)
                        } else {
                            Color.rgb(203, 213, 225)
                        }
                    )
                }
                setOnClickListener { chooseLetter(letter) }
            }
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = dp(48)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(2), dp(2), dp(2), dp(2))
            }
            answerGrid.addView(button, params)
        }
    }

    private fun chooseLetter(letter: String) {
        if (completedQuestion) return
        if (role == Role.LEARNER) {
            hostEndpoint?.let { nearby?.send(it, JSONObject().put("type", "answer").put("letter", letter).toString().toByteArray()) }
            statusText = if (language == LetterLanguage.ENGLISH) "Answer sent to the teacher." else "התשובה נשלחה למורה."
            refreshGameUi()
            return
        }
        checkAnswer(letter)
    }

    private fun checkAnswer(letter: String) {
        if (completedQuestion) return
        val correct = question?.letter(language) == letter
        if (correct) {
            points += 1
            completedQuestion = true
            statusText = if (language == LetterLanguage.ENGLISH) "Correct! +1 point." else "נכון! נקודה אחת."
            gameAudio.playSuccess()
        } else {
            statusText = if (language == LetterLanguage.ENGLISH) "Not quite. Try another letter." else "לא בדיוק. נסו אות אחרת."
            gameAudio.playIncorrect()
        }
        refreshGameUi()
        if (role == Role.TEACHER_HOST) broadcastResult(correct)
    }

    private fun speakWord() {
        if (role == Role.LEARNER) {
            statusText = if (language == LetterLanguage.ENGLISH) "Listen for the word on this device." else "השמיעו את המילה במכשיר הזה."
            refreshGameUi()
        }
        if (!ttsReady) {
            Toast.makeText(this, "Text-to-speech is not ready on this device.", Toast.LENGTH_LONG).show()
            return
        }
        val locale = if (language == LetterLanguage.ENGLISH) Locale.US else Locale("he", "IL")
        val result = tts?.setLanguage(locale) ?: TextToSpeech.ERROR
        if (result < TextToSpeech.LANG_AVAILABLE) {
            Toast.makeText(this, "Text-to-speech does not support this language on this device.", Toast.LENGTH_LONG).show()
            return
        }
        val item = question ?: return
        val speakResult = tts?.speak(item.word(language), TextToSpeech.QUEUE_FLUSH, null, "letter-word")
        if (speakResult == null || speakResult == TextToSpeech.ERROR) {
            Toast.makeText(this, "Could not speak the word on this device.", Toast.LENGTH_LONG).show()
        }
    }

    private fun reduceChoices() {
        if (completedQuestion) return
        val correct = question?.letter(language) ?: return
        options = (listOf(correct) + LetterQuestionBank.alphabet(language)
            .filter { it != correct }
            .shuffled()
            .take(3)).shuffled()
        if (role == Role.TEACHER_HOST) broadcastQuestion()
        refreshGameUi()
    }

    private fun revealAnswer() {
        if (completedQuestion) return
        if (role == Role.LEARNER) {
            hostEndpoint?.let { nearby?.send(it, JSONObject().put("type", "help").put("help", "reveal").toString().toByteArray()) }
            statusText = if (language == LetterLanguage.ENGLISH) "Asked the teacher to reveal the answer." else "ביקשתם מהמורה לגלות את התשובה."
            refreshGameUi()
            return
        }
        val answer = question?.letter(language) ?: return
        completedQuestion = true
        revealed = true
        statusText = if (language == LetterLanguage.ENGLISH) {
            "The answer is $answer. No point for this question."
        } else {
            "התשובה היא $answer. לא מקבלים נקודה בשאלה זו."
        }
        refreshGameUi()
        if (role == Role.TEACHER_HOST) broadcastResult(false, revealed = true)
    }

    private fun initDisplayListener() {
        val manager = getSystemService(DISPLAY_SERVICE) as DisplayManager
        displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = checkExternalDisplay()
            override fun onDisplayRemoved(displayId: Int) {
                if (presentationDisplay?.displayId == displayId) {
                    presentation?.dismiss()
                    presentation = null
                    presentationDisplay = null
                    updatePromptVisibility()
                }
            }
            override fun onDisplayChanged(displayId: Int) = Unit
        }
        manager.registerDisplayListener(displayListener, null)
    }

    private fun checkExternalDisplay() {
        val manager = getSystemService(DISPLAY_SERVICE) as DisplayManager
        val display = manager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull()
            ?: manager.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
        if (display == null) {
            presentation?.dismiss()
            presentation = null
            presentationDisplay = null
            updatePromptVisibility()
            return
        }
        if (presentationDisplay?.displayId != display.displayId) {
            presentation?.dismiss()
            presentation = LetterTablePresentation(this, display).also { it.show() }
            presentationDisplay = display
        }
        updatePresentation()
        updatePromptVisibility()
    }

    private fun updatePromptVisibility() {
        if (!::pictureView.isInitialized || !::questionView.isInitialized) return
        val visibility = if (presentation == null) View.VISIBLE else View.GONE
        pictureView.visibility = visibility
        questionView.visibility = visibility
    }

    private fun updatePresentation() {
        question?.let { presentation?.update(it, language, points) }
    }

    private fun startAdvertising() {
        showLobby(if (language == LetterLanguage.ENGLISH) {
            "Waiting for a learner to join..."
        } else {
            "ממתינים לשחקן שיצטרף..."
        })
        nearby = createNearbySession()
        nearby?.startAdvertising(deviceDisplayName())
    }

    private fun startDiscovery() {
        discovered.clear()
        showLobby("מחפשים משחק אותיות של מורה · Looking for a teacher-hosted game...")
        nearby = createNearbySession()
        nearby?.startDiscovery()
    }

    private fun showLobby(message: String) {
        val lobby = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(Color.rgb(15, 23, 42))
        }
        val heading = TextView(this).apply {
            text = message
            textSize = 21f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        lobby.addView(heading, matchWrap())
        if (role == Role.TEACHER_HOST) {
            lobby.addView(TextView(this).apply {
                text = if (language == LetterLanguage.ENGLISH) "Language: English" else "שפה: עברית"
                textSize = 18f
                setTextColor(Color.LTGRAY)
                gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, dp(12))
            }, matchWrap())
        }
        val devices = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            tag = "discovered-devices"
        }
        lobby.addView(devices, matchWrap())
        val cancel = Button(this).apply {
            text = "Cancel"
            isAllCaps = false
            setOnClickListener {
                nearby?.stop()
                nearby = null
                if (role == Role.LEARNER) showModeDialog() else finish()
            }
        }
        lobby.addView(cancel, matchWrap())
        setContentView(lobby)
        if (role == Role.LEARNER) refreshDiscoveredDevices(devices)
    }

    private fun refreshDiscoveredDevices(container: LinearLayout? = null) {
        val devicesContainer = container ?: return
        devicesContainer.removeAllViews()
        discovered.forEach { (endpointId, name) ->
            devicesContainer.addView(Button(this).apply {
                text = name
                isAllCaps = false
                setOnClickListener {
                    hostEndpoint = endpointId
                    nearby?.requestConnection(deviceDisplayName(), endpointId)
                }
            }, matchWrap())
        }
    }

    private fun createNearbySession() = NearbySession(this, SERVICE_ID, object : NearbySession.Listener {
        override fun onConnectionInitiated(endpointId: String, endpointName: String, authenticationDigits: String) {
            runOnUiThread {
                if (role == Role.TEACHER_HOST) {
                    if (hostEndpoint != null) {
                        nearby?.rejectConnection(endpointId)
                        return@runOnUiThread
                    }
                    AlertDialog.Builder(this@LetterGameActivity)
                        .setTitle("Confirm learner")
                        .setMessage("Check that $authenticationDigits matches on both devices.")
                        .setPositiveButton("Connect") { _, _ -> nearby?.acceptConnection(endpointId) }
                        .setNegativeButton("Decline") { _, _ -> nearby?.rejectConnection(endpointId) }
                        .setOnCancelListener { nearby?.rejectConnection(endpointId) }
                        .show()
                } else {
                    nearby?.acceptConnection(endpointId)
                }
            }
        }

        override fun onConnected(endpointId: String, endpointName: String) = runOnUiThread {
            if (role == Role.LEARNER) {
                hostEndpoint = endpointId
                nearby?.stopDiscovery()
                showLobby("Connected to $endpointName. Waiting for the teacher to start...")
            } else if (role == Role.TEACHER_HOST) {
                if (hostEndpoint != null && hostEndpoint != endpointId) {
                    nearby?.disconnect(endpointId)
                    return@runOnUiThread
                }
                nearby?.stopAdvertising()
                hostEndpoint = endpointId
                points = 0
                questionDeck = LetterQuestionBank.questions.shuffled()
                startNextNetworkQuestion()
                startGameSounds()
                checkExternalDisplay()
            }
        }

        override fun onDisconnected(endpointId: String) = runOnUiThread {
            nearby?.stop()
            nearby = null
            Toast.makeText(this@LetterGameActivity, "The game connection was lost.", Toast.LENGTH_LONG).show()
            finish()
        }

        override fun onEndpointFound(endpointId: String, endpointName: String) = runOnUiThread {
            if (role != Role.LEARNER) return@runOnUiThread
            discovered[endpointId] = endpointName
            refreshDiscoveredDevicesFromView()
        }

        override fun onEndpointLost(endpointId: String) = runOnUiThread {
            discovered.remove(endpointId)
            refreshDiscoveredDevicesFromView()
        }

        override fun onPayload(endpointId: String, bytes: ByteArray) = runOnUiThread {
            handlePayload(endpointId, bytes)
        }

        override fun onError(error: Exception) = runOnUiThread {
            android.util.Log.e(TAG, "Nearby letter game operation failed", error)
            Toast.makeText(this@LetterGameActivity, "Connection error: ${error.localizedMessage ?: "try again"}", Toast.LENGTH_LONG).show()
        }
    })

    private fun refreshDiscoveredDevicesFromView() {
        val content = (window.decorView as? ViewGroup)?.let { findTextContainer(it) }
        refreshDiscoveredDevices(content)
    }

    private fun findTextContainer(view: View): LinearLayout? {
        if (view is LinearLayout && view.tag == "discovered-devices") return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findTextContainer(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private fun startNextNetworkQuestion(broadcast: Boolean = true) {
        startNextQuestion()
        completedQuestion = false
        revealed = false
        renderGame()
        if (broadcast) broadcastQuestion()
    }

    private fun broadcastQuestion() {
        val endpoint = hostEndpoint ?: return
        val item = question ?: return
        val payload = JSONObject()
            .put("type", "question")
            .put("picture", item.picture)
            .put("english", item.englishWord)
            .put("hebrew", item.hebrewWord)
            .put("englishLetter", item.englishLetter)
            .put("hebrewLetter", item.hebrewLetter)
            .put("language", language.name)
            .put("options", options.joinToString(""))
            .put("points", points)
        nearby?.send(endpoint, payload.toString().toByteArray())
    }

    private fun broadcastResult(correct: Boolean, revealed: Boolean = false) {
        val endpoint = hostEndpoint ?: return
        val payload = JSONObject()
            .put("type", "result")
            .put("correct", correct)
            .put("revealed", revealed)
            .put("answer", question?.letter(language))
            .put("points", points)
            .put("complete", completedQuestion)
        nearby?.send(endpoint, payload.toString().toByteArray())
    }

    private fun handlePayload(endpointId: String, bytes: ByteArray) {
        try {
            val payload = JSONObject(String(bytes))
            when (payload.optString("type")) {
                "question" -> {
                    if (role != Role.LEARNER) return
                    language = LetterLanguage.valueOf(payload.getString("language"))
                    question = LetterQuestion(
                        payload.getString("picture"),
                        payload.getString("english"),
                        payload.getString("hebrew"),
                        payload.getString("englishLetter"),
                        payload.getString("hebrewLetter")
                    )
                    options = payload.getString("options").map(Char::toString)
                    points = payload.getInt("points")
                    completedQuestion = false
                    statusText = if (language == LetterLanguage.ENGLISH) "Choose the first letter." else "בחרו את האות הראשונה."
                    renderGame()
                    startGameSounds()
                    checkExternalDisplay()
                }
                "answer" -> {
                    if (role == Role.TEACHER_HOST && endpointId == hostEndpoint) {
                        checkAnswer(payload.getString("letter"))
                    }
                }
                "help" -> {
                    if (role == Role.TEACHER_HOST && endpointId == hostEndpoint &&
                        payload.optString("help") == "reveal"
                    ) revealAnswer()
                }
                "result" -> {
                    if (role != Role.LEARNER) return
                    points = payload.getInt("points")
                    val correct = payload.getBoolean("correct")
                    val answer = payload.optString("answer")
                    statusText = when {
                        payload.optBoolean("revealed") -> if (language == LetterLanguage.ENGLISH) {
                            "The answer is $answer. No point this time."
                        } else "התשובה היא $answer. לא מקבלים נקודה הפעם."
                        correct -> if (language == LetterLanguage.ENGLISH) "Correct! +1 point." else "נכון! נקודה אחת."
                        else -> if (language == LetterLanguage.ENGLISH) "Not quite. Try another letter." else "לא בדיוק. נסו שוב."
                    }
                    completedQuestion = payload.optBoolean("complete")
                    revealed = payload.optBoolean("revealed")
                    if (correct) gameAudio.playSuccess() else gameAudio.playIncorrect()
                    refreshGameUi()
                }
            }
        } catch (error: JSONException) {
            android.util.Log.e(TAG, "Invalid letter game message", error)
            Toast.makeText(this, "Received an invalid game message.", Toast.LENGTH_LONG).show()
        } catch (error: IllegalArgumentException) {
            android.util.Log.e(TAG, "Unknown letter game language", error)
            Toast.makeText(this, "Received an unsupported game language.", Toast.LENGTH_LONG).show()
        }
    }

    private fun withNearbyPermissions(action: () -> Unit) {
        val required = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.NEARBY_WIFI_DEVICES
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT)
        } else listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        val missing = required.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) action() else {
            permissionAction = action
            requestPermissions(missing.toTypedArray(), REQUEST_PERMISSIONS)
        }
    }

    @Deprecated("Deprecated in Android, retained for the min SDK")
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_PERMISSIONS) return
        val action = permissionAction
        permissionAction = null
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            action?.invoke()
        } else {
            Toast.makeText(this, "Nearby permissions are required for connected play.", Toast.LENGTH_LONG).show()
            showModeDialog()
        }
    }

    private fun helpButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        textSize = 12f
        isAllCaps = false
        setPadding(dp(2), 0, dp(2), 0)
        setOnClickListener { onClick() }
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun weightedButton() = LinearLayout.LayoutParams(0, dp(48), 1f).apply {
        setMargins(dp(2), dp(3), dp(2), dp(3))
    }

    private fun showButtonChoice(
        title: String,
        choices: List<String>,
        onSelected: (Int) -> Unit,
        onCancel: () -> Unit = { showModeDialog() }
    ) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(content)
            .setNegativeButton(if (language == LetterLanguage.HEBREW) "ביטול" else "Cancel") { _, _ ->
                onCancel()
            }
            .create()
        choices.forEachIndexed { index, choice ->
            content.addView(Button(this).apply {
                text = choice
                isAllCaps = false
                setOnClickListener {
                    dialog.dismiss()
                    onSelected(index)
                }
            }, matchWrap())
        }
        dialog.show()
    }

    private fun startGameSounds() {
        gameAudio.startGame(music = false, effects = true)
    }

    override fun onResume() {
        super.onResume()
        if (question != null) startGameSounds()
    }

    override fun onPause() {
        gameAudio.stopGame()
        super.onPause()
    }

    private fun deviceDisplayName() = "${Build.MANUFACTURER} ${Build.MODEL}".take(32)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        (getSystemService(DISPLAY_SERVICE) as DisplayManager).unregisterDisplayListener(displayListener)
        presentation?.dismiss()
        nearby?.stop()
        gameAudio.stopGame()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val SERVICE_ID = "com.gamebox.letter"
        private const val REQUEST_PERMISSIONS = 701
        private const val TAG = "LetterGame"
    }
}
