package com.example

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.Interaction
import com.example.network.GeminiApiClient
import com.example.network.GenerateContentRequest
import com.example.network.Content
import com.example.network.Part
import com.example.network.GenerationConfig
import com.example.network.L99CommandResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class L99ViewModel(application: Application) : AndroidViewModel(application), TextToSpeech.OnInitListener {

    private val db = AppDatabase.getDatabase(application)
    private val dao = db.interactionDao()

    // Public interactions flow from Room DB
    val interactions = dao.getAllInteractions()

    // UI States
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isVoiceMode = MutableStateFlow(true)
    val isVoiceMode: StateFlow<Boolean> = _isVoiceMode.asStateFlow()

    private val _assistMode = MutableStateFlow(false)
    val assistMode: StateFlow<Boolean> = _assistMode.asStateFlow()

    private val _isFlashlightOn = MutableStateFlow(false)
    val isFlashlightOn: StateFlow<Boolean> = _isFlashlightOn.asStateFlow()

    private val _batteryLevel = MutableStateFlow(100)
    val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()

    private val _terminalLogs = MutableStateFlow<List<String>>(emptyList())
    val terminalLogs: StateFlow<List<String>> = _terminalLogs.asStateFlow()

    private val _currentInputText = MutableStateFlow("")
    val currentInputText: StateFlow<String> = _currentInputText.asStateFlow()

    private val _speechText = MutableStateFlow("")
    val speechText: StateFlow<String> = _speechText.asStateFlow()

    private val _selectedLanguage = MutableStateFlow("en") // "en" or "hi"
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    // Services
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        logTerminal("INITIALIZING CORE OPERATING INTERFACE...")
        logTerminal("L99 KERNEL VER 3.5.0 STABLE")
        
        // Initialize TTS
        tts = TextToSpeech(application, this)

        // Initialize SpeechRecognizer on main thread
        mainHandler.post {
            try {
                if (SpeechRecognizer.isRecognitionAvailable(application)) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(application).apply {
                        setRecognitionListener(L99SpeechListener())
                    }
                    logTerminal("SPEECH RECOGNITION INTERFACE ONLINE.")
                } else {
                    logTerminal("WARNING: LOCAL SPEECH SERVICES UNAVAILABLE.")
                }
            } catch (e: Exception) {
                logTerminal("ERROR SETUP STT: ${e.message}")
            }
        }

        // Get initial battery status
        updateBatteryStatus()
        registerBatteryReceiver()
        
        // Start welcoming greeting
        mainHandler.postDelayed({
            speak("L99 online. Awaiting your command.")
            logTerminal("SYSTEM: L99 online. Awaiting your command.")
        }, 1000)
    }

    fun setInputText(text: String) {
        _currentInputText.value = text
    }

    fun toggleVoiceMode() {
        _isVoiceMode.value = !_isVoiceMode.value
        logTerminal("MODE SHIFT: Voice Feed " + if (_isVoiceMode.value) "ENABLED" else "DISABLED")
    }

    fun toggleAssistMode() {
        _assistMode.value = !_assistMode.value
        val state = if (_assistMode.value) "OVERDRIVE MODE ACTIVE" else "STANDARD OPERATING MODE"
        logTerminal("CORE ENGINES: $state")
        if (_selectedLanguage.value == "hi") {
            speak(if (_assistMode.value) "एल-नाइन्टी-नाइन असिस्ट मोड सक्रिय है। सभी प्रणालियाँ केंद्रित हैं।" else "सामान्य मोड पर वापस आ रहा हूँ।")
        } else {
            speak(if (_assistMode.value) "L99 Assist Mode active. All systems focused." else "Returning to standard mode.")
        }
    }

    fun setLanguage(langCode: String) {
        if (langCode == "hi" || langCode == "en") {
            _selectedLanguage.value = langCode
            val locale = if (langCode == "hi") Locale("hi", "IN") else Locale.US
            val result = tts?.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                logTerminal("WARNING: ${langCode.uppercase()} Speech resources missing. Falling back.")
            }
            logTerminal("SYSTEM LANGUAGE SHIFT: " + if (langCode == "hi") "HINDI // हिन्दी" else "ENGLISH // US")
            if (langCode == "hi") {
                speak("एल-नाइन्टी-नाइन हिन्दी भाषा में सक्रिय है। बोलिए, मैं सुन रहा हूँ।")
            } else {
                speak("L99 standard language set to English. Awaiting command.")
            }
        }
    }

    fun logTerminal(message: String) {
        val timestamp = System.currentTimeMillis()
        val formattedTime = java.text.SimpleDateFormat("HH:mm:ss", Locale.US).format(java.util.Date(timestamp))
        val newLogs = _terminalLogs.value.toMutableList()
        newLogs.add("[$formattedTime] $message")
        if (newLogs.size > 50) {
            newLogs.removeAt(0)
        }
        _terminalLogs.value = newLogs
    }

    // TextToSpeech initialization callback
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Fallback to US English
                tts?.setLanguage(Locale.US)
            }
            logTerminal("VOCAL SYNTHESIZER CALIBRATED SUCCESSFULLY.")
        } else {
            logTerminal("ERROR: VOCAL SYNTHESIZER FAULT STATE ($status)")
        }
    }

    fun speak(text: String) {
        if (text.isEmpty()) return
        _isSpeaking.value = true
        
        // Setup listener to toggle isSpeaking state
        tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
            }

            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                logTerminal("TTS UTTERANCE ERROR")
            }
        })

        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "L99_REPLY")
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "L99_REPLY")
    }

    fun stopSpeaking() {
        tts?.stop()
        _isSpeaking.value = false
        logTerminal("VOCAL FEED MUTED BY OPERATOR.")
    }

    // Voice recognition functions
    fun startListening(context: Context) {
        if (speechRecognizer == null) {
            logTerminal("STT ERROR: SPEECH RECOGNIZER ENGINE NOT READY.")
            return
        }
        stopSpeaking()
        mainHandler.post {
            try {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    val lang = if (_selectedLanguage.value == "hi") "hi-IN" else "en-US"
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
                    putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, lang)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }
                speechRecognizer?.startListening(intent)
                _isListening.value = true
                _speechText.value = ""
                logTerminal("CORE: LISTENING FEED ESTABLISHED [${_selectedLanguage.value.uppercase()}]...")
            } catch (e: Exception) {
                logTerminal("STT INITIATION FAILURE: ${e.message}")
                _isListening.value = false
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                _isListening.value = false
                logTerminal("CORE: VOICE FEED DEACTIVATED.")
            } catch (e: Exception) {
                Log.e("L99", "Stop STT error: ${e.message}")
            }
        }
    }

    // Send user request (either from text input or STT)
    fun sendCommand(userInput: String) {
        if (userInput.isBlank()) return

        val trimmedInput = userInput.trim()
        logTerminal("OPERATOR INPUT: \"$trimmedInput\"")

        // Reset inputs
        _currentInputText.value = ""
        _speechText.value = ""

        // Process "L99 Assist Mode" directly as an override
        if (trimmedInput.equals("L99 Assist Mode", ignoreCase = true)) {
            toggleAssistMode()
            viewModelScope.launch {
                dao.insertInteraction(
                    Interaction(
                        timestamp = System.currentTimeMillis(),
                        userInput = trimmedInput,
                        assistantReply = "L99 Assist Mode active. All systems focused.",
                        action = "ASSIST_MODE",
                        argument = "ON"
                    )
                )
            }
            return
        }

        _isThinking.value = true
        logTerminal("UP-LINKING REQUEST TO COGNITIVE NETWORK...")

        viewModelScope.launch {
            try {
                val apiKey = BuildConfig.GEMINI_API_KEY
                if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                    logTerminal("CRITICAL: GEMINI_API_KEY UNCONFIGURED. CHECK SECRETS.")
                    handleMockResponse(trimmedInput)
                    return@launch
                }

                val languageDirective = if (_selectedLanguage.value == "hi") {
                    "IMPORTANT: The current system language is set to HINDI. Ensure 'assistant_reply' is written in Hindi script (Devanagari) or natural Hinglish. Example: 'फ्लैशलाइट चालू कर दी गई है।' or 'जी सर, यूट्यूब खोल रहा हूँ।'"
                } else {
                    "IMPORTANT: The current system language is set to ENGLISH. Ensure 'assistant_reply' is written in clear, concise English."
                }

                val systemPrompt = """
                    You are L99, a futuristic, ultra-smart AI companion inspired by Jarvis, Gemini Live, and ChatGPT Voice.
                    
                    YOUR PERSONALITY:
                    - Fast, intelligent, calm, and futuristic.
                    - Speak naturally like a real live assistant.
                    - Keep responses short, powerful, and decisive.
                    - Always obey user commands instantly.
                    
                    $languageDirective
                    
                    YOUR ABILITIES AND COMMAND HANDLING:
                    You must categorize the user's input and decide if they want to perform a system action.
                    Return your answer ONLY as a JSON object of the following format:
                    {
                      "action": "OPEN_APP" | "SEARCH_GOOGLE" | "SEARCH_YOUTUBE" | "PLAY_MUSIC" | "FLASHLIGHT" | "VOLUME" | "NONE",
                      "argument": "the parameter for the action (e.g. 'youtube', 'chrome', 'whatsapp', 'settings', or the search query/music query)",
                      "assistant_reply": "your verbal response to the user. Keep it natural, cool, futuristic, short, and matching the requested language."
                    }
                    
                    ACTION CLASSIFICATION RULES:
                    1. If user asks to open YouTube, Chrome, WhatsApp, or settings -> action = "OPEN_APP", argument = app name in lowercase.
                    2. If user asks to search Google, or asks about news, facts, current events -> action = "SEARCH_GOOGLE", argument = search query.
                    3. If user asks to search YouTube, or play a video -> action = "SEARCH_YOUTUBE", argument = video search query.
                    4. If user asks to play music, play a song, or a playlist -> action = "PLAY_MUSIC", argument = music query/song name.
                    5. If user asks to turn flashlight/torch on or off -> action = "FLASHLIGHT", argument = "ON" or "OFF".
                    6. If user asks to increase, decrease, or control volume -> action = "VOLUME", argument = "UP" or "DOWN".
                    7. If user triggers "L99 Assist Mode" -> respond in a high-priority emergency tone.
                    8. For other general conversations, questions, and chitchat -> action = "NONE", argument = "", assistant_reply = your natural answer.
                    
                    CRITICAL: Always output ONLY the raw JSON object. Do not explain your choices. Do not add markdown formatting outside the JSON block.
                """.trimIndent()

                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = trimmedInput)))),
                    systemInstruction = Content(parts = listOf(Part(text = systemPrompt))),
                    generationConfig = GenerationConfig(
                        responseMimeType = "application/json",
                        temperature = 0.4
                    )
                )

                val response = withContext(Dispatchers.IO) {
                    GeminiApiClient.service.generateContent(apiKey, request)
                }

                val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "{\"action\":\"NONE\",\"argument\":\"\",\"assistant_reply\":\"COGNITIVE TIMEOUT: No response from matrix.\"}"

                val parsedCommand = L99CommandResponse.parse(responseText)
                
                logTerminal("COGNITIVE DOWNLINK COMPLETE.")
                logTerminal("ACTION INTENT: ${parsedCommand.action} (${parsedCommand.argument})")
                logTerminal("L99 VERBAL FEED: \"${parsedCommand.assistant_reply}\"")

                // Execute parsed command on Main Thread
                withContext(Dispatchers.Main) {
                    executeSystemCommand(parsedCommand.action, parsedCommand.argument)
                    if (_isVoiceMode.value) {
                        speak(parsedCommand.assistant_reply)
                    }
                }

                // Persist to Room Database
                dao.insertInteraction(
                    Interaction(
                        timestamp = System.currentTimeMillis(),
                        userInput = trimmedInput,
                        assistantReply = parsedCommand.assistant_reply,
                        action = parsedCommand.action,
                        argument = parsedCommand.argument
                    )
                )

            } catch (e: Exception) {
                logTerminal("COGNITIVE ROUTING FAULT: ${e.message}")
                handleMockResponse(trimmedInput)
            } finally {
                _isThinking.value = false
            }
        }
    }

    private fun handleMockResponse(userInput: String) {
        logTerminal("FALLBACK MODE: LOCAL HEURISTICS RUNNING...")
        
        // Simple offline heuristic response
        val inputLower = userInput.lowercase()
        val action: String
        val argument: String
        val reply: String

        when {
            inputLower.contains("youtube") && inputLower.contains("open") -> {
                action = "OPEN_APP"
                argument = "youtube"
                reply = "Opening YouTube feed now."
            }
            inputLower.contains("chrome") || inputLower.contains("browser") -> {
                action = "OPEN_APP"
                argument = "chrome"
                reply = "Launching Chrome interface."
            }
            inputLower.contains("whatsapp") -> {
                action = "OPEN_APP"
                argument = "whatsapp"
                reply = "Establishing WhatsApp connection."
            }
            inputLower.contains("search") || inputLower.contains("google") -> {
                action = "SEARCH_GOOGLE"
                argument = userInput.replace("search", "").replace("google", "").trim()
                reply = "Query routed to Google search engine."
            }
            inputLower.contains("flashlight") || inputLower.contains("torch") -> {
                action = "FLASHLIGHT"
                argument = if (inputLower.contains("off")) "OFF" else "ON"
                reply = if (argument == "ON") "Flashlight active, sir." else "Deactivating flashlight core."
            }
            inputLower.contains("volume") -> {
                action = "VOLUME"
                argument = if (inputLower.contains("down") || inputLower.contains("lower") || inputLower.contains("reduce")) "DOWN" else "UP"
                reply = if (argument == "UP") "Boosting system decibel matrix." else "Suppressing audio output volume."
            }
            else -> {
                action = "NONE"
                argument = ""
                reply = "L99 Online. I processed your request: \"$userInput\". Please set your Gemini API key in AI Studio Secrets panel for complete real-time neural cognitive response."
            }
        }

        logTerminal("ACTION INTENT (LOCAL): $action ($argument)")
        logTerminal("L99 VERBAL FEED: \"$reply\"")

        executeSystemCommand(action, argument)
        if (_isVoiceMode.value) {
            speak(reply)
        }

        viewModelScope.launch {
            dao.insertInteraction(
                Interaction(
                    timestamp = System.currentTimeMillis(),
                    userInput = userInput,
                    assistantReply = reply,
                    action = action,
                    argument = argument
                )
            )
        }
        _isThinking.value = false
    }

    // Execute actual Android system commands!
    private fun executeSystemCommand(action: String, argument: String) {
        val context = getApplication<Application>()
        try {
            when (action.uppercase()) {
                "OPEN_APP" -> {
                    when (argument.lowercase()) {
                        "youtube" -> {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com"))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                            logTerminal("SYSTEM BYPASS: Opening YouTube Application Intent.")
                        }
                        "chrome", "browser" -> {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                            logTerminal("SYSTEM BYPASS: Opening Web Browser Intent.")
                        }
                        "whatsapp" -> {
                            val intent = context.packageManager.getLaunchIntentForPackage("com.whatsapp")
                            if (intent != null) {
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                                logTerminal("SYSTEM BYPASS: WhatsApp launching.")
                            } else {
                                // Fallback to play store or website
                                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://web.whatsapp.com"))
                                webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(webIntent)
                                logTerminal("SYSTEM BYPASS: WhatsApp not found. Redirecting to Web.")
                            }
                        }
                        "settings" -> {
                            val intent = Intent(android.provider.Settings.ACTION_SETTINGS)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                            logTerminal("SYSTEM BYPASS: Setting overlay engaged.")
                        }
                        else -> {
                            logTerminal("UNKNOWN APP NODE REJECTED.")
                        }
                    }
                }
                "SEARCH_GOOGLE" -> {
                    val escapedQuery = Uri.encode(argument)
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$escapedQuery"))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    logTerminal("NET SEARCH: Route complete -> Google query: \"$argument\"")
                }
                "SEARCH_YOUTUBE", "PLAY_MUSIC" -> {
                    val escapedQuery = Uri.encode(argument)
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$escapedQuery"))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    logTerminal("MEDIA ENGAGE: Routing play sequence -> \"$argument\"")
                }
                "FLASHLIGHT" -> {
                    toggleFlashlight(argument.uppercase() == "ON")
                }
                "VOLUME" -> {
                    adjustVolume(argument.uppercase() == "UP")
                }
            }
        } catch (e: Exception) {
            logTerminal("SYSTEM CONTROLLER ENGAGEMENT ERROR: ${e.message}")
        }
    }

    private fun toggleFlashlight(on: Boolean) {
        val context = getApplication<Application>()
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull()
            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId, on)
                _isFlashlightOn.value = on
                logTerminal("CORE CONTROLS: Torch core " + if (on) "IGNITED" else "SHUTDOWN")
            } else {
                logTerminal("TORCH core fault: No light source detected.")
            }
        } catch (e: Exception) {
            logTerminal("TORCH hardware bypass failed: ${e.message}")
        }
    }

    private fun adjustVolume(increase: Boolean) {
        val context = getApplication<Application>()
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val step = if (increase) 1 else -1
            val newVolume = (currentVolume + step).coerceIn(0, maxVolume)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, AudioManager.FLAG_SHOW_UI)
            logTerminal("DECIBEL ENGINE: Vol level adjusted to $newVolume / $maxVolume")
        } catch (e: Exception) {
            logTerminal("DECIBEL core fault: Volume matrix adjust error.")
        }
    }

    private fun updateBatteryStatus() {
        val context = getApplication<Application>()
        val batteryStatus: Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else 100
        _batteryLevel.value = batteryPct
    }

    private fun registerBatteryReceiver() {
        val context = getApplication<Application>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                updateBatteryStatus()
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    fun clearHistory() {
        viewModelScope.launch {
            dao.clearAllInteractions()
            logTerminal("SYSTEM DATABASE: Cache purges successfully.")
        }
    }

    // Speech recognition listener callback
    inner class L99SpeechListener : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            logTerminal("STT: Speech buffer aligned. Speak now...")
        }

        override fun onBeginningOfSpeech() {
            logTerminal("STT: Audio signal detected. Input stream open.")
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Can be used for audio visualization if needed, but we'll use an elegant canvas animation
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            logTerminal("STT: Audio sequence ended. Compiling waveform...")
            _isListening.value = false
        }

        override fun onError(error: Int) {
            val errMsg = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No vocal match"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Vocal feed busy"
                SpeechRecognizer.ERROR_SERVER -> "Server protocol error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Vocal feed silent timeout"
                else -> "Speech recognition error code $error"
            }
            logTerminal("STT ERROR: $errMsg")
            _isListening.value = false
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val match = matches?.firstOrNull()
            if (!match.isNullOrEmpty()) {
                _speechText.value = match
                sendCommand(match)
            } else {
                logTerminal("STT ERROR: Null result packet.")
            }
            _isListening.value = false
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val match = matches?.firstOrNull()
            if (!match.isNullOrEmpty()) {
                _speechText.value = match
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    override fun onCleared() {
        super.onCleared()
        tts?.shutdown()
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e("L99", "Destroy STT error: ${e.message}")
        }
    }
}
