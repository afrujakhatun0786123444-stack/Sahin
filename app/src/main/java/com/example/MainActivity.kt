package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.Interaction
import java.util.Locale
import com.example.ui.L99OrbCore
import com.example.ui.SuggestionPills
import com.example.ui.TerminalView
import com.example.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("main_scaffold"),
                    containerColor = CyberDark
                ) { innerPadding ->
                    L99AssistantScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun L99AssistantScreen(
    modifier: Modifier = Modifier,
    viewModel: L99ViewModel = viewModel()
) {
    val context = LocalContext.current

    // Observe State Flows from ViewModel
    val isListening by viewModel.isListening.collectAsState()
    val isThinking by viewModel.isThinking.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val isVoiceMode by viewModel.isVoiceMode.collectAsState()
    val assistMode by viewModel.assistMode.collectAsState()
    val isFlashlightOn by viewModel.isFlashlightOn.collectAsState()
    val batteryLevel by viewModel.batteryLevel.collectAsState()
    val terminalLogs by viewModel.terminalLogs.collectAsState()
    val currentInputText by viewModel.currentInputText.collectAsState()
    val speechText by viewModel.speechText.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    
    // Live interaction list from Room
    val interactions by viewModel.interactions.collectAsState(initial = emptyList())

    // Permission launcher for record audio
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startListening(context)
        } else {
            viewModel.logTerminal("PERMISSION REJECTED: Audio capture disabled.")
            viewModel.speak("I require microphone access to capture your vocal matrix.")
        }
    }

    var showKeyboardInput by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        CyberDark,
                        CyberSurface.copy(alpha = 0.9f)
                    )
                )
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // TOP HUD BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Title & Status Glow
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (assistMode) NeonPink else CosmicCyan)
                )
                Text(
                    text = "L99 // OPERATING CORE",
                    color = if (assistMode) NeonPink else CosmicCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.5.sp
                )
            }

            // HUD Metrics
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Battery metric
                Text(
                    text = "SYS PWR: $batteryLevel%",
                    color = CyberTextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )

                // Clear Cache / Database action
                IconButton(
                    onClick = { viewModel.clearHistory() },
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("clear_cache_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear cache",
                        tint = CyberTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // DYNAMIC CYBER CONTROLS ROW
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Flashlight button
            ControlChip(
                icon = if (isFlashlightOn) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                label = "TORCH",
                isActive = isFlashlightOn,
                activeColor = CosmicCyan,
                onClick = { viewModel.sendCommand(if (isFlashlightOn) "turn flashlight off" else "turn flashlight on") },
                testTag = "flashlight_chip"
            )

            // Assist mode / Overdrive trigger
            ControlChip(
                icon = Icons.Default.Bolt,
                label = "OVERDRIVE",
                isActive = assistMode,
                activeColor = NeonPink,
                onClick = { viewModel.toggleAssistMode() },
                testTag = "assist_mode_chip"
            )

            // Voice mute/unmute
            ControlChip(
                icon = if (isVoiceMode) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                label = "VOCAL",
                isActive = isVoiceMode,
                activeColor = ElectricViolet,
                onClick = { viewModel.toggleVoiceMode() },
                testTag = "voice_mode_chip"
            )

            // Language Switcher (EN / HI)
            ControlChip(
                icon = Icons.Default.Language,
                label = if (selectedLanguage == "hi") "HINDI" else "ENGLISH",
                isActive = selectedLanguage == "hi",
                activeColor = CyberAccentAmber,
                onClick = { viewModel.setLanguage(if (selectedLanguage == "hi") "en" else "hi") },
                testTag = "language_chip"
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // THE CORE ORB (Reacts dynamically to states)
        L99OrbCore(
            isListening = isListening,
            isThinking = isThinking,
            isSpeaking = isSpeaking,
            modifier = Modifier.padding(vertical = 10.dp)
        )

        // REAL-TIME SPEECH / TRANSCRIPT TRANSITION
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .height(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedVisibility(
                visible = isListening || speechText.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Text(
                    text = if (speechText.isEmpty()) "Awaiting signal stream..." else "\"$speechText\"",
                    color = CosmicCyan,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            AnimatedVisibility(
                visible = !isListening && speechText.isEmpty() && isThinking,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = "L99 THINKING: Solving mathematical matrix...",
                    color = ElectricViolet,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            AnimatedVisibility(
                visible = !isListening && speechText.isEmpty() && !isThinking && !isSpeaking,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = "“L99 online. Awaiting your command.”",
                    color = CyberTextSecondary,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.SansSerif,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // REAL-TIME TERMINAL CONSOLE LOGGING
        TerminalView(
            logs = terminalLogs,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        Spacer(modifier = Modifier.height(5.dp))

        // QUICK RECOMMENDATION SUGGESTIONS
        SuggestionPills(
            suggestions = listOf(
                "Open YouTube",
                "Search AI news",
                "Play Arijit Singh songs",
                "What is quantum mechanics?",
                "Turn flashlight on",
                "L99 Assist Mode"
            ),
            onSuggestionClick = { query ->
                viewModel.sendCommand(query)
            },
            modifier = Modifier.padding(vertical = 4.dp)
        )

        // HISTORICAL COMMANDS LOG (ROOM DATABASE PRESERVED)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .border(1.dp, CyberSurfaceVariant, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(CyberSurface.copy(alpha = 0.5f))
        ) {
            if (interactions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "NO LOCAL INTERACTION DATABASE RECOVERED.",
                        color = CyberTextSecondary.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(interactions) { interaction ->
                        InteractionCard(interaction = interaction)
                    }
                }
            }
        }

        // TRIGGER CONTROL & KEYBOARD TRAY
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Toggle Keyboard vs Mic
            IconButton(
                onClick = { showKeyboardInput = !showKeyboardInput },
                modifier = Modifier
                    .size(48.dp)
                    .background(CyberSurfaceVariant, CircleShape)
                    .border(1.dp, CosmicCyan.copy(alpha = 0.3f), CircleShape)
                    .testTag("toggle_input_mode_button")
            ) {
                Icon(
                    imageVector = if (showKeyboardInput) Icons.Default.Mic else Icons.Default.Keyboard,
                    contentDescription = "Switch Input Mode",
                    tint = CosmicCyan
                )
            }

            if (showKeyboardInput) {
                // Keyboard Input Text Row
                TextField(
                    value = currentInputText,
                    onValueChange = { viewModel.setInputText(it) },
                    placeholder = { Text("Send cyber command...", color = CyberTextSecondary, fontSize = 14.sp) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = CyberSurface,
                        unfocusedContainerColor = CyberSurface,
                        focusedTextColor = CyberTextPrimary,
                        unfocusedTextColor = CyberTextPrimary,
                        focusedIndicatorColor = CosmicCyan,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .border(1.dp, CosmicCyan.copy(alpha = 0.3f), RoundedCornerShape(26.dp))
                        .clip(RoundedCornerShape(26.dp))
                        .testTag("text_input_field"),
                    shape = RoundedCornerShape(26.dp),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (currentInputText.isNotBlank()) {
                                    viewModel.sendCommand(currentInputText)
                                }
                            },
                            modifier = Modifier.testTag("send_text_button")
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Send", tint = CosmicCyan)
                        }
                    }
                )
            } else {
                // Large voice capture feed toggle
                Button(
                    onClick = {
                        if (isListening) {
                            viewModel.stopListening()
                        } else {
                            val permissionCheck = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            )
                            if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                                viewModel.startListening(context)
                            } else {
                                recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .border(
                            1.dp,
                            if (isListening) NeonPink.copy(alpha = 0.6f) else CosmicCyan.copy(alpha = 0.3f),
                            RoundedCornerShape(26.dp)
                        )
                        .testTag("voice_trigger_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isListening) NeonPink.copy(alpha = 0.2f) else CyberSurface
                    ),
                    shape = RoundedCornerShape(26.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isListening) NeonPink else CosmicCyan)
                        )
                        Text(
                            text = if (isListening) "FEED ACTIVE: SPEECH TO COMPILING" else "IGNITE VOCAL CAPTURE FEED",
                            color = if (isListening) NeonPink else CosmicCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ControlChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isActive) activeColor.copy(alpha = 0.15f) else CyberSurface)
            .border(
                width = 1.dp,
                color = if (isActive) activeColor else CyberTextSecondary.copy(alpha = 0.3f),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) activeColor else CyberTextSecondary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                color = if (isActive) activeColor else CyberTextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun InteractionCard(interaction: Interaction) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberSurfaceVariant, RoundedCornerShape(8.dp))
            .testTag("interaction_card_${interaction.id}"),
        colors = CardDefaults.cardColors(containerColor = CyberSurface.copy(alpha = 0.7f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Operator Query
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = ">> OPERATOR",
                    color = CyberTextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
                val time = java.text.SimpleDateFormat("HH:mm:ss", Locale.US).format(java.util.Date(interaction.timestamp))
                Text(
                    text = "UTC: $time",
                    color = CyberTextSecondary.copy(alpha = 0.5f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp
                )
            }
            Text(
                text = interaction.userInput,
                color = CyberTextPrimary,
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            HorizontalDivider(
                color = CyberSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // L99 Response
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = ">> L99 COGNITIVE FEED",
                    color = if (interaction.action != "NONE") NeonPink else CosmicCyan,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
                if (interaction.action != "NONE") {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(NeonPink.copy(alpha = 0.15f))
                            .border(1.dp, NeonPink.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "${interaction.action}: ${interaction.argument}",
                            color = NeonPink,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Text(
                text = interaction.assistantReply,
                color = if (interaction.action != "NONE") CyberTextPrimary else CosmicCyan.copy(alpha = 0.95f),
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}
