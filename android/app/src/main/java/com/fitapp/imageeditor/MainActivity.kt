package com.fitapp.imageeditor

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fitapp.imageeditor.ui.editor.EditorScreen
import com.fitapp.imageeditor.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val shareUrl = extractShareUrl(intent)
        setContent {
            AppTheme {
                EditorScreen(shareUrl = shareUrl)
            }
        }
    }

    // Called when app is already running (singleTop) and another share arrives
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun extractShareUrl(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        if (intent.type != "text/plain") return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        // Extract first URL from the shared text (Amazon includes extra text around the link)
        return Regex("https?://\\S+").find(text)?.value
    }
}
