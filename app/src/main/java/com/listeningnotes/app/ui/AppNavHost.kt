package com.listeningnotes.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.listeningnotes.app.AppContainer
import com.listeningnotes.app.ui.common.SimpleFactory
import com.listeningnotes.app.ui.library.LibraryScreen
import com.listeningnotes.app.ui.library.LibraryViewModel
import com.listeningnotes.app.ui.settings.SettingsScreen
import com.listeningnotes.app.ui.settings.SettingsViewModel
import com.listeningnotes.app.ui.study.StudyScreen
import com.listeningnotes.app.ui.study.StudyViewModel
import com.listeningnotes.app.ui.keywords.KeywordsScreen
import com.listeningnotes.app.ui.keywords.KeywordsViewModel
import com.listeningnotes.app.ui.vocab.VocabScreen
import com.listeningnotes.app.ui.vocab.VocabViewModel
import com.listeningnotes.app.ui.transcribe.TranscribeScreen
import com.listeningnotes.app.ui.transcribe.TranscribeViewModel

private object Routes {
    const val LIBRARY = "library"
    const val NEW = "new"
    const val SETTINGS = "settings"
    const val VOCAB = "vocab"
    const val KEYWORDS = "keywords/{id}"
    fun keywords(id: Long) = "keywords/$id"
    const val STUDY = "study/{id}"
    fun study(id: Long) = "study/$id"
}

@Composable
fun AppNavHost(container: AppContainer) {
    val nav = rememberNavController()

    // Un audio compartido desde otra app lleva siempre a la pantalla de transcripción.
    val shared by container.sharedAudio.collectAsStateWithLifecycle()
    LaunchedEffect(shared) {
        if (shared != null && nav.currentDestination?.route != Routes.NEW) {
            nav.navigate(Routes.NEW) { launchSingleTop = true }
        }
    }

    NavHost(nav, startDestination = Routes.LIBRARY) {
        composable(Routes.LIBRARY) {
            val vm: LibraryViewModel = viewModel(factory = SimpleFactory { LibraryViewModel(container) })
            LibraryScreen(
                vm,
                onNew = { nav.navigate(Routes.NEW) { launchSingleTop = true } },
                onOpen = { nav.navigate(Routes.study(it)) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
                onVocab = { nav.navigate(Routes.VOCAB) },
            )
        }
        composable(Routes.NEW) {
            val vm: TranscribeViewModel = viewModel(factory = SimpleFactory { TranscribeViewModel(container) })
            TranscribeScreen(
                vm,
                onBack = { nav.popBackStack() },
                onFinished = { id ->
                    nav.navigate(Routes.study(id)) { popUpTo(Routes.NEW) { inclusive = true } }
                },
            )
        }
        composable(
            Routes.STUDY,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            val vm: StudyViewModel = viewModel(key = "study-$id", factory = SimpleFactory { StudyViewModel(container, id) })
            StudyScreen(vm, onBack = { nav.popBackStack() }, onKeywords = { nav.navigate(Routes.keywords(id)) })
        }
        composable(
            Routes.KEYWORDS,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            val vm: KeywordsViewModel = viewModel(key = "keywords-$id", factory = SimpleFactory { KeywordsViewModel(container, id) })
            KeywordsScreen(vm, onBack = { nav.popBackStack() })
        }
        composable(Routes.VOCAB) {
            val vm: VocabViewModel = viewModel(factory = SimpleFactory { VocabViewModel(container) })
            VocabScreen(vm, onBack = { nav.popBackStack() }, onOpenTranscription = { nav.navigate(Routes.study(it)) })
        }
        composable(Routes.SETTINGS) {
            val vm: SettingsViewModel = viewModel(factory = SimpleFactory { SettingsViewModel(container) })
            SettingsScreen(vm, onBack = { nav.popBackStack() })
        }
    }
}
