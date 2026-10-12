package com.listeningnotes.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.listeningnotes.app.data.repository.AppSettings
import com.listeningnotes.app.ui.AppNavHost
import com.listeningnotes.app.ui.theme.ListeningNotesTheme
import com.listeningnotes.app.whisper.Models
import kotlinx.coroutines.launch

/** Única Activity: aloja la navegación y recibe los audios compartidos / abiertos desde otras apps. */
class MainActivity : ComponentActivity() {

    private val container: AppContainer
        get() = (application as ListeningNotesApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (savedInstanceState == null) {
            handleIntent(intent)
            // Primer arranque: descarga el modelo seleccionado si falta (reanuda si estaba a medias).
            lifecycleScope.launch {
                container.modelManager.ensureInstalled(Models.byId(container.settings.current().modelId))
            }
        }

        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
            ListeningNotesTheme(settings.theme) {
                AppNavHost(container)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
        if (uri != null) container.sharedAudio.value = uri
    }
}
