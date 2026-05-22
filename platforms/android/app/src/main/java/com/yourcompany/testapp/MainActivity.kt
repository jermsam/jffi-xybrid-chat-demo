package com.yourcompany.testapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourcompany.testapp.ui.theme.TestappTheme
import uniffi.testapp_core.ChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.alpha

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // JFFI: Initialize Android context for Rust code (if ndk-context is needed)
        // This is auto-generated and safe to call even if not needed
        try {
            JffiAndroidInit.initNdkContext(applicationContext)
        } catch (_: UnsatisfiedLinkError) {
            // JffiAndroidInit not generated - ndk-context not needed
        }

        // Initialize local LLM cache directory
        try {
            val cacheDir = java.io.File(applicationContext.filesDir, "models")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            uniffi.testapp_core.initSdkCacheDir(cacheDir.absolutePath)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to initialize SDK cache directory", e)
        }
        
        enableEdgeToEdge()
        setContent {
            TestappTheme {
                val viewModel: AppViewModel = viewModel()
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                if (uiState.isModelReady) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        ChatScreen(
                            uiState = uiState,
                            onMessageChange = viewModel::updateInputText,
                            onSendMessage = viewModel::sendMessage,
                            onClearHistory = viewModel::clearHistory,
                            modifier = Modifier.padding(top = innerPadding.calculateTopPadding())
                        )
                    }
                } else {
                    LaunchScreen(uiState = uiState)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    uiState: AppUiState,
    onMessageChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val isUserScrolling by listState.interactionSource.collectIsDraggedAsState()

    // Auto-scroll to bottom when new messages arrive, typing status changes, or streaming text updates
    var previousMessageCount by remember { mutableIntStateOf(0) }
    val isAtBottom = remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItemsInfo = layoutInfo.visibleItemsInfo
            if (visibleItemsInfo.isEmpty()) {
                true
            } else {
                val lastVisibleItem = visibleItemsInfo.last()
                val lastItemIndex = layoutInfo.totalItemsCount - 1
                val isLastItem = lastVisibleItem.index >= lastItemIndex
                if (isLastItem) {
                    val lastItemBottom = lastVisibleItem.offset + lastVisibleItem.size
                    // We check if the last item's bottom is within 500px of the viewport end.
                    // This threshold easily handles height additions from new tokens.
                    lastItemBottom - layoutInfo.viewportEndOffset <= 500
                } else {
                    false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow {
            val lastItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            Triple(listState.layoutInfo.totalItemsCount, lastItem?.size ?: 0, uiState.messages.size)
        }.collect { (totalItemsCount, lastItemSize, messageCount) ->
            if (messageCount > 0) {
                val sizeChanged = messageCount != previousMessageCount
                previousMessageCount = messageCount
                
                if (isAtBottom.value || sizeChanged) {
                    if (sizeChanged) {
                        listState.animateScrollToItem(messageCount - 1)
                    } else if (!isUserScrolling) {
                        listState.scrollToItem(messageCount - 1, 10000)
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // App Header
        CenterAlignedTopAppBar(
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "JFFI + XYBRID Chat",
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleLarge
                    )
                    
                    // Model ready / loading / offline status
                    val statusText = when {
                        uiState.isModelLoading -> "Warming up engine..."
                        uiState.isModelReady -> "Local LLM Ready"
                        else -> "Initializing..."
                    }
                    val statusColor = when {
                        uiState.isModelLoading -> Color(0xFFF59E0B) // Amber
                        uiState.isModelReady -> Color(0xFF10B981) // Emerald
                        else -> Color(0xFF9CA3AF) // Gray
                    }
                    
                    val statusPulseTransition = rememberInfiniteTransition(label = "pulse")
                    val pulseAlpha by if (uiState.isModelLoading || !uiState.isModelReady) {
                        statusPulseTransition.animateFloat(
                            initialValue = 0.4f,
                            targetValue = 1.0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pulseAlpha"
                        )
                    } else {
                        remember { androidx.compose.runtime.mutableStateOf(1f) }
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(color = statusColor.copy(alpha = pulseAlpha), shape = CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            },
            actions = {
                IconButton(onClick = onClearHistory) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear Chat History",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
            )
        )

        // Error message if any
        uiState.error?.let { err ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Message List Container with Scroll-to-bottom overlay
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Circuit board background
            Image(
                painter = painterResource(id = R.drawable.chat_background),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.18f)
            )
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(uiState.messages) { message ->
                    var visible by remember { androidx.compose.runtime.mutableStateOf(false) }
                    LaunchedEffect(message) {
                        visible = true
                    }
                    Box {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = visible,
                            enter = fadeIn(animationSpec = tween(250)) +
                                    slideInVertically(
                                        initialOffsetY = { 30 },
                                        animationSpec = tween(250, easing = FastOutSlowInEasing)
                                    )
                        ) {
                            MessageBubble(message = message)
                        }
                    }
                }

                val isThinking = uiState.isSending && (uiState.messages.lastOrNull()?.sender != "bot" || uiState.messages.lastOrNull()?.text.isNullOrEmpty())
                if (isThinking) {
                    item {
                        var visible by remember { androidx.compose.runtime.mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            visible = true
                        }
                        Box {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = visible,
                                enter = fadeIn(animationSpec = tween(250)) +
                                        slideInVertically(
                                            initialOffsetY = { 30 },
                                            animationSpec = tween(250, easing = FastOutSlowInEasing)
                                        )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(
                                                RoundedCornerShape(
                                                    topStart = 18.dp,
                                                    topEnd = 18.dp,
                                                    bottomStart = 4.dp,
                                                    bottomEnd = 18.dp
                                                )
                                            )
                                            .background(MaterialTheme.colorScheme.secondaryContainer)
                                    ) {
                                        TypingIndicator()
                                    }

                                    Text(
                                        text = "Gemma-3-1b is thinking...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Floating Scroll-to-bottom pill button
            val showScrollToBottom by remember {
                derivedStateOf {
                    !isAtBottom.value && uiState.messages.isNotEmpty()
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = showScrollToBottom,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
                ) {
                    val buttonText = if (uiState.isSending) {
                        "New message streaming..."
                    } else {
                        "Scroll to bottom"
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                if (uiState.messages.isNotEmpty()) {
                                    listState.animateScrollToItem(uiState.messages.size - 1)
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Scroll to bottom",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = buttonText,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Input bar — always visible above the keyboard and nav bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.ime)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // Text field pill
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    BasicTextField(
                        value = uiState.inputText,
                        onValueChange = onMessageChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Enter) {
                                    if (keyEvent.isShiftPressed) {
                                        false
                                    } else {
                                        val text = uiState.inputText.trim()
                                        if (text.isNotEmpty() && !uiState.isSending) {
                                            onSendMessage()
                                        }
                                        true
                                    }
                                } else false
                            },
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Send,
                            keyboardType = KeyboardType.Text
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                val text = uiState.inputText.trim()
                                if (text.isNotEmpty() && !uiState.isSending) onSendMessage()
                            }
                        ),
                        textStyle = LocalTextStyle.current.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        decorationBox = { innerTextField ->
                            if (uiState.inputText.isEmpty()) {
                                Text(
                                    text = "Ask Gemma anything...",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            innerTextField()
                        },
                        maxLines = 5
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Send button — always a solid circle when there's text
                val isInputEmpty = uiState.inputText.trim().isEmpty()
                val canSend = !isInputEmpty && !uiState.isSending
                IconButton(
                    onClick = { if (canSend) onSendMessage() },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (canSend) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                ) {
                    if (uiState.isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (canSend) MaterialTheme.colorScheme.onPrimary
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val isUser = message.sender == "user"

    val bubbleShape = if (isUser) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
    }

    val bubbleColor = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val textColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    val timeString = remember(message.timestamp) {
        try {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            sdf.format(Date(message.timestamp * 1000))
        } catch (_: Exception) { "" }
    }

    // Avatar resource
    val avatarRes = if (isUser) R.drawable.user_avatar else R.drawable.bot_avatar

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        // Bot avatar on the left
        if (!isUser) {
            Image(
                painter = painterResource(id = avatarRes),
                contentDescription = "AI Assistant",
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            // Bubble
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(bubbleShape)
                    .background(bubbleColor)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                RichText(
                    text = message.text,
                    textColor = textColor,
                    linkColor = if (isUser) Color(0xFFBFDBFE) else MaterialTheme.colorScheme.primary
                )
            }

            // Timestamp + stats row
            Row(
                modifier = Modifier.padding(
                    start = if (isUser) 0.dp else 6.dp,
                    end   = if (isUser) 6.dp else 0.dp,
                    top   = 3.dp
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (timeString.isNotEmpty()) {
                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                }
                if (!isUser && (message.latencyMs != null || message.tokensOut != null || message.tokensPerSecond != null)) {
                    val stats = buildString {
                        append("⚡ ")
                        message.latencyMs?.let { append("${it}ms") }
                        message.tokensOut?.let { if (length > 2) append(" • "); append("$it tok") }
                        message.tokensPerSecond?.let {
                            if (length > 2) append(" • ")
                            append(String.format(Locale.US, "%.1f/s", it))
                        }
                    }
                    Text(
                        text = stats,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                    )
                }
            }
        }

        // User avatar on the right
        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Image(
                painter = painterResource(id = avatarRes),
                contentDescription = "You",
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
            )
        }
    }
}

@Composable
fun TypingIndicator(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val dotCount = 3
    val dotSize = 8.dp
    val delayMs = 150
    
    Row(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        (0 until dotCount).forEach { index ->
            val bounce by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -8f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 600
                        0f at 0 with FastOutSlowInEasing
                        -8f at 200 with FastOutSlowInEasing
                        0f at 400 with FastOutSlowInEasing
                        0f at 600 with FastOutSlowInEasing
                    },
                    repeatMode = RepeatMode.Restart,
                    initialStartOffset = StartOffset(index * delayMs)
                ),
                label = "dot_$index"
            )
            
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .graphicsLayer {
                        translationY = bounce
                    }
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        shape = CircleShape
                    )
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────
// Rich text rendering — block-level Markdown + clickable links
// ──────────────────────────────────────────────────────────────

@Composable
fun RichText(
    text: String,
    textColor: Color,
    linkColor: Color,
    modifier: Modifier = Modifier
) {
    val bodyStyle = MaterialTheme.typography.bodyMedium
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val lines = text.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            // ── Fenced code block  ────────────────────────────
            if (trimmed.startsWith("```")) {
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                val codeText = codeLines.joinToString("\n")
                val scrollState = rememberScrollState()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F172A))
                        .padding(10.dp)
                        .horizontalScroll(scrollState)
                ) {
                    Text(
                        text = codeText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFFA5F3FC),
                        style = bodyStyle
                    )
                }
                i++ // skip closing ```
                continue
            }

            // ── Horizontal rule  ─────────────────────────────
            if (trimmed.matches(Regex("[-*_]{3,}"))) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = textColor.copy(alpha = 0.2f)
                )
                i++; continue
            }

            // ── Heading  ──────────────────────────────────────
            val headingMatch = Regex("^(#{1,6})\\s+(.+)$").find(trimmed)
            if (headingMatch != null) {
                val level = headingMatch.groupValues[1].length
                val headingText = headingMatch.groupValues[2]
                val headingStyle = when (level) {
                    1 -> MaterialTheme.typography.titleLarge
                    2 -> MaterialTheme.typography.titleMedium
                    else -> MaterialTheme.typography.titleSmall
                }
                Text(
                    text = parseInlineMarkdown(headingText, textColor, linkColor),
                    style = headingStyle,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    modifier = Modifier.padding(top = if (level <= 2) 6.dp else 2.dp)
                )
                i++; continue
            }

            // ── Unordered bullet  ─────────────────────────────
            val bulletMatch = Regex("^\\s*[-*•]\\s+(.+)$").find(line)
            if (bulletMatch != null) {
                val indentLevel = line.indexOfFirst { !it.isWhitespace() } / 2
                Row(
                    modifier = Modifier.padding(start = (indentLevel * 12).dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "• ",
                        color = linkColor,
                        style = bodyStyle,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = parseInlineMarkdown(bulletMatch.groupValues[1], textColor, linkColor),
                        style = bodyStyle,
                        color = textColor
                    )
                }
                i++; continue
            }

            // ── Numbered list  ────────────────────────────────
            val numberedMatch = Regex("^\\s*(\\d+)\\.\\s+(.+)$").find(line)
            if (numberedMatch != null) {
                val num = numberedMatch.groupValues[1]
                val indentLevel = line.indexOfFirst { !it.isWhitespace() } / 2
                Row(
                    modifier = Modifier.padding(start = (indentLevel * 12).dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "$num. ",
                        color = linkColor,
                        style = bodyStyle,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = parseInlineMarkdown(numberedMatch.groupValues[2], textColor, linkColor),
                        style = bodyStyle,
                        color = textColor
                    )
                }
                i++; continue
            }

            // ── Blank line → small spacer  ────────────────────
            if (trimmed.isEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                i++; continue
            }

            // ── Regular paragraph  ────────────────────────────
            Text(
                text = parseInlineMarkdown(line, textColor, linkColor),
                style = bodyStyle,
                color = textColor
            )
            i++
        }
    }
}

/**
 * Parses inline Markdown (links, bold, italic, inline code) into an [AnnotatedString]
 * with clickable [LinkAnnotation.Url] for `[text](url)` patterns.
 */
fun parseInlineMarkdown(
    text: String,
    defaultColor: Color,
    linkColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        // Order matters — match links first, then bold (**), then italic (*), then code (`)
        val regex = Regex(
            """\[([^\]]+)\]\(([^)]+)\)|(\*\*|__)(.+?)\3|(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)|`([^`]+)`"""
        )
        var cursor = 0
        for (match in regex.findAll(text)) {
            if (match.range.first > cursor) {
                append(text.substring(cursor, match.range.first))
            }
            val linkLabel = match.groups[1]?.value
            val linkUrl   = match.groups[2]?.value
            val boldText  = match.groups[4]?.value
            val italicText = match.groups[5]?.value
            val codeText  = match.groups[6]?.value
            when {
                linkLabel != null && linkUrl != null -> {
                    pushLink(
                        LinkAnnotation.Url(
                            url = linkUrl,
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = linkColor,
                                    textDecoration = TextDecoration.Underline,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        )
                    )
                    append(linkLabel)
                    pop()
                }
                boldText != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = defaultColor)) {
                    append(boldText)
                }
                italicText != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = defaultColor)) {
                    append(italicText)
                }
                codeText != null -> withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFFF43F5E),
                        background = Color(0xFF1E293B).copy(alpha = 0.12f)
                    )
                ) { append(" $codeText ") }
            }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
}

// Keep legacy alias so any remaining call-sites compile
fun parseMarkdown(text: String): AnnotatedString =
    parseInlineMarkdown(text, Color.Unspecified, Color(0xFF6366F1))


@Preview(showBackground = true)
@Composable
fun GreetingScreenPreview() {
    val sampleMessage = ChatMessage(
        sender = "bot",
        text = "Hello from JFFI",
        timestamp = 1620000000L,
        latencyMs = 124L,
        tokensOut = 45,
        tokensPerSecond = 32.5
    )
    TestappTheme {
        MessageBubble(sampleMessage)
    }
}

@Composable
fun PremiumLogo(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "logoRotation")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    )
                )
        )
        
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .size(80.dp)
                .graphicsLayer { rotationZ = rotation }
        ) {
            val size = size.minDimension
            val strokeWidth = 3.dp.toPx()
            
            drawRoundRect(
                color = androidx.compose.ui.graphics.Color(0xFF6366F1),
                topLeft = androidx.compose.ui.geometry.Offset(size * 0.15f, size * 0.15f),
                size = androidx.compose.ui.geometry.Size(size * 0.7f, size * 0.7f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size * 0.2f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
            )
            
            drawRoundRect(
                color = androidx.compose.ui.graphics.Color(0xFF3B82F6),
                topLeft = androidx.compose.ui.geometry.Offset(size * 0.25f, size * 0.25f),
                size = androidx.compose.ui.geometry.Size(size * 0.5f, size * 0.5f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size * 0.15f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
            )
        }
    }
}

@Composable
fun LaunchScreen(uiState: AppUiState) {
    // Info cards that cycle during warmup
    val infoCards = listOf(
        Pair(
            "\uD83E\uDD16 What is JFFI?",
            "JFFI is a cross-platform framework that lets you write your app logic once in Rust and ship it natively to Android, iOS, macOS, and Windows — no bridges, no JNI boilerplate."
        ),
        Pair(
            "\uD83E\uDDE0 What is xybrid?",
            "xybrid is an on-device AI runtime. It loads and runs GGUF models (like Gemma) directly on your phone's CPU or GPU — no cloud, no data leaves your device."
        ),
        Pair(
            "\uD83D\uDD12 Why on-device AI?",
            "Your conversations never leave your device. No API keys, no internet required after setup, no data harvesting — complete privacy by design."
        ),
        Pair(
            "\u26A1 Gemma-3-1b",
            "Google's 1-billion-parameter instruction-tuned model, quantized to run efficiently on mobile hardware. Compact enough for a phone, capable enough for real conversations."
        )
    )

    var currentCard by remember { mutableStateOf(0) }
    var cardVisible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(3500)
            cardVisible = false
            kotlinx.coroutines.delay(400)
            currentCard = (currentCard + 1) % infoCards.size
            cardVisible = true
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Full-screen background
        Image(
            painter = painterResource(id = R.drawable.chat_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // Dark overlay for readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xCC0F0F23), Color(0xEE0F0F23))
                    )
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
        ) {
            // App icon
            Image(
                painter = painterResource(id = R.drawable.app_icon),
                contentDescription = "App Icon",
                modifier = Modifier
                    .size(110.dp)
                    .clip(RoundedCornerShape(28.dp))
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "JFFI + XYBRID Chat",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "100% on-device AI — private by design",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Animated info card
            androidx.compose.animation.AnimatedVisibility(
                visible = cardVisible,
                enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 3 },
                exit = fadeOut(tween(300))
            ) {
                val (title, body) = infoCards[currentCard]
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1E1B4B).copy(alpha = 0.9f)
                    ),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF818CF8)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f),
                            lineHeight = 22.sp
                        )
                    }
                }
            }

            // Card dot indicators
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                infoCards.indices.forEach { idx ->
                    Box(
                        modifier = Modifier
                            .size(if (idx == currentCard) 10.dp else 6.dp)
                            .background(
                                if (idx == currentCard) Color(0xFF818CF8)
                                else Color.White.copy(alpha = 0.3f),
                                CircleShape
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Loading indicator
            CircularProgressIndicator(
                color = Color(0xFF818CF8),
                strokeWidth = 3.dp,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Warming up Gemma-3-1b engine...",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF818CF8)
            )
        }
    }
}
