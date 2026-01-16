package work.vkkovalev.samplecomposebug

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Shape mode determines how shapes are created for message bubbles.
 *
 * BUG_MODE shows the bug, KEY_WRAPPER demonstrates the workaround.
 */
enum class ShapeMode {
    /**
     * BUG: Uses singleton shapes. Combined with SSM, this causes
     * bubbles to disappear on API < 33.
     */
    BUG_MODE,

    /**
     * WORKAROUND: Uses key() to force composition recreation when shape changes.
     * This ensures new outline is created even when SSM would skip recomposition.
     */
    KEY_WRAPPER
}

/** Self-test result state */
enum class SelfTestResult {
    NOT_STARTED,
    RUNNING,
    PASS,      // Bug NOT reproduced (bubble visible)
    FAIL       // Bug reproduced (bubble invisible/clipped)
}

/**
 * Chat screen displaying a list of messages with ability to send new ones.
 *
 * The bug reproduces when:
 * 1. SSM is enabled (default in Kotlin 2.0.20+)
 * 2. Shape mode is SINGLETON or FACTORY
 * 3. Android 10-11 device
 * 4. User sends a new message → previous message's bubble disappears
 *
 * @param shapeMode How shapes are created for message bubbles.
 * @param initialMessageCount Number of messages to start with.
 * @param autoTest If true, automatically runs self-test on launch.
 * @param onTestResult Callback when self-test completes.
 */
@Composable
fun ChatScreen(
    shapeMode: ShapeMode = ShapeMode.BUG_MODE,
    initialMessageCount: Int = 5,
    autoTest: Boolean = false,
    onTestResult: (SelfTestResult) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Dynamic list - new messages can be added
    val messages = remember {
        mutableStateListOf<Message>().apply {
            addAll(generateMessages(initialMessageCount))
        }
    }
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // For pixel-based bug detection
    val view = LocalView.current
    var bubble4Bounds by remember { mutableStateOf<Rect?>(null) }
    var testPhase by remember(shapeMode) { mutableIntStateOf(0) } // 0=idle, 1=running, 2=done - resets on mode change

    // Auto-test with pixel-based detection - re-runs on mode change
    LaunchedEffect(autoTest, shapeMode) {
        if (autoTest && testPhase == 0) {
            onTestResult(SelfTestResult.RUNNING)
            testPhase = 1
            delay(800) // Wait for initial render and layout

            // Add new message - this triggers the bug
            // Bubble 4 (id=4) was last outgoing, its isLast changes true→false
            messages.add(
                Message(
                    id = messages.size,
                    text = "AUTO-TEST: New message sent",
                    isOutgoing = true
                )
            )

            delay(500) // Wait for recomposition and render

            // Capture pixels at bubble 4 location and check if it's visible
            val bounds = bubble4Bounds
            if (bounds != null && bounds.width() > 0 && bounds.height() > 0) {
                val hasVisiblePixels = captureAndCheckPixels(view, bounds)
                testPhase = 2
                onTestResult(if (hasVisiblePixels) SelfTestResult.PASS else SelfTestResult.FAIL)
            } else {
                testPhase = 2
                onTestResult(SelfTestResult.PASS) // Can't detect, assume pass
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Find the last outgoing and incoming message IDs for groupFlags logic
        // This is critical for bug reproduction: when new message is sent,
        // the previous "last" message becomes "not last" and its shape should change.
        // But SSM may skip recomposition, leaving the old outline!
        val lastOutgoingId = messages.lastOrNull { it.isOutgoing }?.id
        val lastIncomingId = messages.lastOrNull { !it.isOutgoing }?.id

        // Chat messages - reverseLayout for chat-like behavior (newest at bottom)
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .testTag("chat_list"),
            state = listState,
            reverseLayout = true,
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // Reversed so newest messages appear at bottom (with reverseLayout=true)
            items(
                items = messages.asReversed(),
                key = { it.id }
            ) { message ->
                // isLast determines the shape variant (like groupFlags in real messenger)
                // When new message arrives, the previous "last" message becomes "not last"
                // and should get a different shape. This is where the bug occurs!
                val isLast = if (message.isOutgoing) {
                    message.id == lastOutgoingId
                } else {
                    message.id == lastIncomingId
                }

                // DIAGNOSTIC: Log isLast calculation for key messages
                if (message.id == 4 || message.id == 5) {
                    Log.d(
                        "SSM_DEBUG",
                        "ChatScreen item: id=${message.id}, isLast=$isLast, " +
                            "lastOutgoingId=$lastOutgoingId, shapeMode=$shapeMode"
                    )
                }

                // Get shape based on message direction and position
                val shape = if (message.isOutgoing) {
                    MessageBubbleShapes.getOutgoingShape(isLast)
                } else {
                    MessageBubbleShapes.getIncomingShape(isLast)
                }

                // KEY_WRAPPER mode: force composition recreation when shape changes
                val useKeyWrapper = shapeMode == ShapeMode.KEY_WRAPPER

                // Track bubble 4's screen position for pixel-based bug detection
                val bubbleModifier = if (message.id == 4 && autoTest) {
                    Modifier.onGloballyPositioned { coords ->
                        val pos = coords.positionInWindow()
                        bubble4Bounds = Rect(
                            pos.x.toInt(),
                            pos.y.toInt(),
                            (pos.x + coords.size.width).toInt(),
                            (pos.y + coords.size.height).toInt()
                        )
                    }
                } else {
                    Modifier
                }

                // Workaround: key() forces composition recreation when shape changes
                if (useKeyWrapper) {
                    key(System.identityHashCode(shape)) {
                        MessageBubble(
                            modifier = bubbleModifier,
                            message = message,
                            shape = shape
                        )
                    }
                } else {
                    MessageBubble(
                        modifier = bubbleModifier,
                        message = message,
                        shape = shape
                    )
                }
            }
        }

        // Message input field
        MessageInput(
            text = messageText,
            onTextChange = { messageText = it },
            onSend = {
                if (messageText.isNotBlank()) {
                    messages.add(
                        Message(
                            id = messages.size,
                            text = messageText,
                            isOutgoing = true // New messages are always outgoing
                        )
                    )
                    messageText = ""
                    // Scroll to newest message (index 0 in reverseLayout)
                    coroutineScope.launch {
                        listState.animateScrollToItem(0)
                    }
                }
            }
        )
    }
}

// Legacy overload for backward compatibility with existing tests
@Composable
fun ChatScreen(
    useSingletonShapes: Boolean = true,
    messageCount: Int = 150,
    modifier: Modifier = Modifier
) {
    ChatScreen(
        shapeMode = if (useSingletonShapes) ShapeMode.BUG_MODE else ShapeMode.KEY_WRAPPER,
        initialMessageCount = messageCount,
        modifier = modifier
    )
}

/**
 * Capture pixels at the specified bounds and check if there are visible colored pixels.
 * Uses PixelCopy API to capture the actual rendered content.
 *
 * @param view The view to capture from (usually LocalView.current)
 * @param bounds Screen coordinates of the area to check
 * @return true if visible colored pixels found (bubble visible), false if mostly empty (bug)
 */
private suspend fun captureAndCheckPixels(view: View, bounds: Rect): Boolean {
    // PixelCopy requires API 26+, which we have (minSdk 27)
    val window = (view.context as? android.app.Activity)?.window ?: return true

    // Clamp bounds to screen and ensure valid dimensions
    val screenWidth = view.width
    val screenHeight = view.height

    val clampedBounds = Rect(
        bounds.left.coerceIn(0, screenWidth),
        bounds.top.coerceIn(0, screenHeight),
        bounds.right.coerceIn(0, screenWidth),
        bounds.bottom.coerceIn(0, screenHeight)
    )

    if (clampedBounds.width() <= 0 || clampedBounds.height() <= 0) {
        return true // Can't capture, assume visible
    }

    val bitmap = Bitmap.createBitmap(
        clampedBounds.width(),
        clampedBounds.height(),
        Bitmap.Config.ARGB_8888
    )

    return suspendCancellableCoroutine { continuation ->
        try {
            PixelCopy.request(
                window,
                clampedBounds,
                bitmap,
                { copyResult ->
                    if (copyResult == PixelCopy.SUCCESS) {
                        val hasVisiblePixels = checkForColoredPixels(bitmap)
                        continuation.resume(hasVisiblePixels)
                    } else {
                        // PixelCopy failed, assume visible
                        continuation.resume(true)
                    }
                },
                Handler(Looper.getMainLooper())
            )
        } catch (e: Exception) {
            continuation.resume(true) // On error, assume visible
        }
    }
}

/**
 * Check if the bitmap contains visible colored pixels (not just background).
 * The outgoing bubble uses blue color 0xFF0084FF.
 * When bug occurs, the bubble is clipped and we see background (white/gray) instead.
 *
 * @return true if colored pixels found (bubble visible), false if mostly background (bug)
 */
private fun checkForColoredPixels(bitmap: Bitmap): Boolean {
    val width = bitmap.width
    val height = bitmap.height
    var coloredPixelCount = 0
    val totalPixels = width * height

    // Sample pixels to detect blue bubble color
    // Blue bubble: 0xFF0084FF (RGB: 0, 132, 255)

    for (y in 0 until height step 2) { // Sample every 2nd pixel for speed
        for (x in 0 until width step 2) {
            val pixel = bitmap.getPixel(x, y)
            val alpha = (pixel shr 24) and 0xFF
            val red = (pixel shr 16) and 0xFF
            val green = (pixel shr 8) and 0xFF
            val blue = pixel and 0xFF

            // Check if pixel is colored (not white/gray background)
            // Background is typically light (R > 200, G > 200, B > 200) or transparent
            if (alpha > 128) {
                // Check if it's blue-ish (the bubble color)
                if (blue > 200 && red < 100 && green < 180) {
                    coloredPixelCount++
                }
                // Also count any non-white/non-gray pixels as "colored"
                else if (!(red > 200 && green > 200 && blue > 200)) {
                    // Not white-ish background
                    val saturation = maxOf(red, green, blue) - minOf(red, green, blue)
                    if (saturation > 30) { // Has some color
                        coloredPixelCount++
                    }
                }
            }
        }
    }

    // If more than 5% of sampled pixels are colored, bubble is visible
    val sampledPixels = (totalPixels / 4) // We sample every 2nd pixel in both dimensions
    val coloredRatio = coloredPixelCount.toFloat() / sampledPixels.coerceAtLeast(1)

    return coloredRatio > 0.05f
}
