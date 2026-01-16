package work.vkkovalev.samplecomposebug

import android.os.Build
import android.util.Log
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Shape wrapper that uses identity (reference) comparison
 * instead of value-based equals(). This is a workaround for
 * the SSM bug where OutlineResolver doesn't update the outline
 * when shapes have same values.
 *
 * Key insight: RoundedCornerShape.equals() compares by VALUES, not references!
 * So creating a new RoundedCornerShape(...) with same values will still
 * return equals() = true, and OutlineResolver won't update the outline.
 *
 * IdentityShape uses default equals/hashCode (identity comparison),
 * so each new instance is considered "different" by OutlineResolver.
 */
class IdentityShape(private val delegate: Shape) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline = delegate.createOutline(size, layoutDirection, density)

    // equals/hashCode are NOT overridden - uses identity comparison by default
}

/**
 * Logging wrapper to track when createOutline is called.
 * Used for debugging to verify if outline is being updated.
 */
class LoggingShape(
    private val delegate: Shape,
    private val label: String
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        Log.d(
            "SHAPE_DEBUG",
            "createOutline called: label=$label, size=$size, API=${Build.VERSION.SDK_INT}, " +
                "shapeRef=${System.identityHashCode(this)}"
        )
        return delegate.createOutline(size, layoutDirection, density)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LoggingShape) return false
        return delegate == other.delegate
    }

    override fun hashCode(): Int = delegate.hashCode()
}

/**
 * Singleton object holding pre-defined bubble shapes.
 *
 * BUG REPRODUCTION: Using singleton shapes with asymmetric corners
 * can cause bubbles to disappear after scrolling on Android 10-11
 * when Strong Skipping Mode is enabled.
 *
 * The issue occurs because:
 * 1. Modifier.clip(shape) → OutlineResolver.update() → compares via equals()
 * 2. RoundedCornerShape has value-based equals() (compares corner values)
 * 3. SSM skips recomposition → outline not re-established
 * 4. On Android 10-11, RenderNode may "lose" outline during recycle
 */
object MessageBubbleShapes {

    // Outgoing message bubble with smaller bottom-right corner
    val outgoingBubble = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomEnd = 6.dp,
        bottomStart = 16.dp
    )

    // Incoming message bubble with smaller bottom-left corner
    val incomingBubble = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomEnd = 16.dp,
        bottomStart = 6.dp
    )

    /**
     * Factory method - creates a new shape instance each time.
     *
     * NOTE: This does NOT fix the bug! RoundedCornerShape.equals()
     * compares by values, so OutlineResolver sees it as "same shape".
     */
    fun createOutgoingBubble() = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomEnd = 6.dp,
        bottomStart = 16.dp
    )

    /**
     * Factory method for incoming bubbles.
     *
     * NOTE: This does NOT fix the bug! See createOutgoingBubble().
     */
    fun createIncomingBubble() = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomEnd = 16.dp,
        bottomStart = 6.dp
    )

    /**
     * Identity wrapper - creates a new IdentityShape wrapper each time.
     *
     * FIX: IdentityShape uses default equals() (reference equality),
     * so OutlineResolver always sees it as "different shape" and updates.
     */
    fun createOutgoingIdentityBubble(): Shape = IdentityShape(outgoingBubble)

    /**
     * Identity wrapper for incoming bubbles.
     */
    fun createIncomingIdentityBubble(): Shape = IdentityShape(incomingBubble)

    // ============ GroupFlags-based shapes (like real messenger) ============

    // When message is LAST in group (or single) - same as outgoingBubble
    private val outgoingLastShape = RoundedCornerShape(16.dp, 16.dp, 6.dp, 16.dp)
    // When message is in MIDDLE of group (not last) - different shape!
    private val outgoingMiddleShape = RoundedCornerShape(16.dp, 6.dp, 6.dp, 16.dp)

    private val incomingLastShape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 6.dp)
    private val incomingMiddleShape = RoundedCornerShape(6.dp, 16.dp, 16.dp, 6.dp)

    // Logging wrappers for debugging
    private val outgoingLastLogging = LoggingShape(outgoingLastShape, "outgoing_LAST")
    private val outgoingMiddleLogging = LoggingShape(outgoingMiddleShape, "outgoing_MIDDLE")
    private val incomingLastLogging = LoggingShape(incomingLastShape, "incoming_LAST")
    private val incomingMiddleLogging = LoggingShape(incomingMiddleShape, "incoming_MIDDLE")

    /**
     * Get shape based on whether this is the last message.
     * This mimics the real messenger's groupFlags logic.
     *
     * KEY FOR BUG: When a new message is sent, the previous message
     * changes from isLast=true to isLast=false, which should change
     * the shape. But SSM may skip recomposition, leaving stale outline.
     */
    fun getOutgoingShape(isLast: Boolean): Shape {
        return if (isLast) outgoingLastLogging else outgoingMiddleLogging
    }

    fun getIncomingShape(isLast: Boolean): Shape {
        return if (isLast) incomingLastLogging else incomingMiddleLogging
    }

    fun getOutgoingIdentityShape(isLast: Boolean): Shape {
        return IdentityShape(if (isLast) outgoingLastShape else outgoingMiddleShape)
    }

    fun getIncomingIdentityShape(isLast: Boolean): Shape {
        return IdentityShape(if (isLast) incomingLastShape else incomingMiddleShape)
    }
}
