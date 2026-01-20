package work.vkkovalev.samplecomposebug

import android.util.Log
import androidx.compose.animation.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * A message bubble composable that displays a chat message.
 *
 * The clip modifier with the shape is where the bug manifests.
 * When Strong Skipping Mode is enabled and singleton shapes are used,
 * the bubble may become invisible after scrolling.
 */
@Composable
fun MessageBubble(
    message: Message,
    shape: Shape,
    modifier: Modifier = Modifier
) {
    // DIAGNOSTIC: Log shape reference on every recomposition
    // This will help determine if SSM is skipping recomposition or if shape reference changes
    SideEffect {
        Log.d(
            "SSM_DEBUG",
            "MessageBubble RECOMPOSED: id=${message.id}, " +
                "shapeRef=${System.identityHashCode(shape)}, " +
                "shapeClass=${shape::class.simpleName}"
        )
    }

    val alignment = if (message.isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
    val backgroundColor = if (message.isOutgoing) {
        Color(0xFF0084FF) // Blue for outgoing
    } else {
        Color(0xFFE4E6EB) // Gray for incoming
    }
    val textColor = if (message.isOutgoing) Color.White else Color.Black

    val color = remember { Animatable(backgroundColor) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .testTag("message_bubble_${message.id}")
                .clip(shape)
                .drawBehind { drawRect(color.value) }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = message.text,
                color = textColor,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
