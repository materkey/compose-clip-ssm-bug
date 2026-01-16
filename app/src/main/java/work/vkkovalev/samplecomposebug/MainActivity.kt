package work.vkkovalev.samplecomposebug

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import work.vkkovalev.samplecomposebug.ui.theme.SampleComposeBugTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SampleComposeBugTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    var shapeMode by remember { mutableStateOf(ShapeMode.BUG_MODE) } // Start with SINGLETON for test
    var testResult by remember { mutableStateOf(SelfTestResult.NOT_STARTED) }
    var autoTestEnabled by remember { mutableStateOf(true) } // Auto-test on launch

    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        topBar = {
            TopAppBar(
                title = { Text("Compose Clip Bug") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Info Card with test result
            InfoCard(shapeMode = shapeMode, testResult = testResult)

            // Shape mode selector (3 modes)
            ShapeModeSelector(
                selectedMode = shapeMode,
                onModeSelected = {
                    shapeMode = it
                    // Reset test when mode changes
                    testResult = SelfTestResult.NOT_STARTED
                    autoTestEnabled = true
                }
            )

            // Chat screen with auto-test
            ChatScreen(
                shapeMode = shapeMode,
                initialMessageCount = 5,
                autoTest = autoTestEnabled,
                onTestResult = { result ->
                    testResult = result
                    if (result != SelfTestResult.RUNNING) {
                        autoTestEnabled = false // Disable after test completes
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun InfoCard(shapeMode: ShapeMode, testResult: SelfTestResult = SelfTestResult.NOT_STARTED) {
    val ssmEnabled = BuildConfig.SSM_ENABLED
    val isBuggyAndroid = Build.VERSION.SDK_INT < 33

    // Bug expected if: SSM ON + API < 33
    val bugExpected = ssmEnabled && isBuggyAndroid

    // Card color based on ACTUAL test result (pixel-based detection)
    val cardColor = when (testResult) {
        SelfTestResult.NOT_STARTED -> Color(0xFFE0E0E0) // Gray - waiting
        SelfTestResult.RUNNING -> Color(0xFFFFF9C4) // Yellow - testing
        SelfTestResult.PASS -> Color(0xFFC8E6C9) // Green - no bug
        SelfTestResult.FAIL -> Color(0xFFFFCDD2) // Red - bug detected!
    }

    val statusText = when (testResult) {
        SelfTestResult.NOT_STARTED -> "Waiting for test..."
        SelfTestResult.RUNNING -> "TESTING (pixel detection)..."
        SelfTestResult.PASS -> "✓ PASS - Bubble visible (no bug)"
        SelfTestResult.FAIL -> "✗ FAIL - Bubble invisible (BUG!)"
    }

    val textColor = when (testResult) {
        SelfTestResult.FAIL -> Color(0xFFC62828) // Dark red
        SelfTestResult.PASS -> Color(0xFF2E7D32) // Dark green
        else -> Color.Black
    }

    val detailText = buildString {
        append("SSM: ${if (ssmEnabled) "ON" else "OFF"}")
        append(" | API: ${Build.VERSION.SDK_INT}")
        append(" | Mode: ${shapeMode.name}")
        if (bugExpected) {
            append("\n→ Expected: BUG (SSM ON + API < 33)")
        } else {
            append("\n→ Expected: NO BUG")
        }
        if (testResult == SelfTestResult.FAIL) {
            append("\n→ Pixel detection: bubble 5 is INVISIBLE")
        } else if (testResult == SelfTestResult.PASS) {
            append("\n→ Pixel detection: bubble 5 is VISIBLE")
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = detailText,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Black
            )
        }
    }
}

@Composable
private fun ShapeModeSelector(
    selectedMode: ShapeMode,
    onModeSelected: (ShapeMode) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Shape Mode",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShapeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = selectedMode == mode,
                        onClick = { onModeSelected(mode) },
                        label = { Text(mode.chipLabel) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = when (mode) {
                                ShapeMode.BUG_MODE -> Color(0xFFFFCDD2)   // Light red (bug)
                                ShapeMode.KEY_WRAPPER -> Color(0xFFC8E6C9) // Light green (fix)
                            },
                            selectedLabelColor = Color.Black
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = selectedMode.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Display name for the info card */
private val ShapeMode.displayName: String
    get() = when (this) {
        ShapeMode.BUG_MODE -> "Bug Mode"
        ShapeMode.KEY_WRAPPER -> "key() Wrapper (FIX)"
    }

/** Short label for the chip */
private val ShapeMode.chipLabel: String
    get() = when (this) {
        ShapeMode.BUG_MODE -> "Bug Mode"
        ShapeMode.KEY_WRAPPER -> "key() Fix"
    }

/** Detailed description for the selector */
private val ShapeMode.description: String
    get() = when (this) {
        ShapeMode.BUG_MODE -> "Bug reproduces on API < 33 with SSM enabled."
        ShapeMode.KEY_WRAPPER -> "Workaround: key(identityHashCode(shape)) forces composition recreation when shape changes."
    }
