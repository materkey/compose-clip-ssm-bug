package work.vkkovalev.samplecomposebug

/**
 * Data class representing a chat message.
 */
data class Message(
    val id: Int,
    val text: String,
    val isOutgoing: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Generate a list of sample messages for testing.
 */
fun generateMessages(count: Int): List<Message> {
    val loremIpsum = listOf(
        "Hello! How are you?",
        "I'm fine, thanks! And you?",
        "Great to hear from you!",
        "This is a test message for the compose bug reproduction.",
        "The bug occurs when scrolling through a LazyColumn with clipped shapes.",
        "Strong Skipping Mode in Compose can cause issues with singleton shapes.",
        "Message bubbles may disappear after scrolling up and down.",
        "This is especially noticeable on Android 10 and 11.",
        "Try scrolling quickly to see the bug in action.",
        "The fix is to use factory methods instead of singleton shapes.",
        "Or you can disable Strong Skipping Mode entirely.",
        "But that's not ideal for performance.",
        "Let's see if we can reproduce this issue!",
        "Keep scrolling...",
        "Almost there...",
        "Just a bit more...",
        "Did you see any missing bubbles?",
        "If not, try scrolling faster!",
        "The bug is intermittent and may not always occur.",
        "But it's definitely there on affected devices.",
    )

    return (0 until count).map { index ->
        Message(
            id = index,
            text = "${index + 1}. ${loremIpsum[index % loremIpsum.size]}",
            isOutgoing = index % 2 == 0
        )
    }
}
