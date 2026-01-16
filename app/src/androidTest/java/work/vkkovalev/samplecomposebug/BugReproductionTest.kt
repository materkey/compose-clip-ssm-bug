package work.vkkovalev.samplecomposebug

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test that catches the SSM bug by checking actual pixel visibility.
 *
 * The bug: Message bubble becomes visually invisible after sending a new message,
 * even though the element still exists in the semantic tree.
 *
 * This test uses captureToImage() to verify that the bubble actually renders
 * visible pixels, not just exists in the tree.
 */
@RunWith(AndroidJUnit4::class)
class BugReproductionTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    /**
     * VISUAL BUG TEST
     *
     * Scenario:
     * 1. Switch to Bug mode (bug mode)
     * 2. Capture last outgoing message (id=4) which has isLast=true shape
     * 3. Send ONE new message
     * 4. Now bubble_4 should change to isLast=false shape
     * 5. BUG: The bubble background disappears because outline is not updated
     *
     * Expected: Bubble should still have colored pixels (blue background)
     * Bug: Bubble becomes transparent/invisible
     */
    @Test
    fun singletonMode_sendMessage_previousBubbleShouldBeVisuallyVisible() {
        // Switch to Bug mode (bug mode)
        composeTestRule.onNodeWithText("Bug Mode")
            .performClick()
        composeTestRule.waitForIdle()

        // Initial messages: 0,1,2,3,4 (5 messages)
        // Outgoing (even ids): 0, 2, 4
        // Last outgoing is message_bubble_4 with isLast=true

        // Capture bubble_4 BEFORE sending - it should be visible
        val bubbleBeforeSend = composeTestRule
            .onNodeWithTag("message_bubble_4", useUnmergedTree = true)
            .captureToImage()
            .asAndroidBitmap()

        val pixelsBeforeSend = countVisiblePixels(bubbleBeforeSend)
        assert(pixelsBeforeSend > 0) {
            "Bubble should be visible before sending (found $pixelsBeforeSend visible pixels)"
        }

        // Send ONE new message
        composeTestRule.onNodeWithTag("message_input")
            .performTextInput("New message")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("send_button")
            .performClick()
        composeTestRule.waitForIdle()

        // Now:
        // - message_bubble_5 is the new last outgoing (isLast=true)
        // - message_bubble_4 changed from isLast=true to isLast=false
        // - Its shape should change, but SSM skips recomposition
        // - OutlineResolver doesn't update → bubble disappears!

        // Wait for rendering to complete
        Thread.sleep(500)

        // Capture bubble_4 AFTER sending
        val bubbleAfterSend = composeTestRule
            .onNodeWithTag("message_bubble_4", useUnmergedTree = true)
            .captureToImage()
            .asAndroidBitmap()

        val pixelsAfterSend = countVisiblePixels(bubbleAfterSend)

        // BUG CHECK: The bubble should still have visible pixels!
        assert(pixelsAfterSend > pixelsBeforeSend / 2) {
            "BUG DETECTED: Bubble lost visibility after sending message!\n" +
            "Visible pixels before: $pixelsBeforeSend\n" +
            "Visible pixels after: $pixelsAfterSend\n" +
            "The bubble outline was not updated due to SSM skipping recomposition."
        }
    }

    /**
     * Count blue background pixels in bitmap.
     * Outgoing bubble color is 0xFF0084FF (blue).
     */
    private fun countVisiblePixels(bitmap: Bitmap): Int {
        var count = 0
        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = (pixel shr 24) and 0xFF
                val red = (pixel shr 16) and 0xFF
                val green = (pixel shr 8) and 0xFF
                val blue = pixel and 0xFF

                // Count blue pixels (bubble background is 0xFF0084FF)
                if (alpha > 200 && blue > 200 && red < 50 && green < 150) {
                    count++
                }
            }
        }
        return count
    }
}
