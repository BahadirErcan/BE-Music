package com.be.music.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.be.music.premium.PremiumScreen

@Composable
fun AppNavigation(viewModel: MusicViewModel) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "home",
    ) {
        composable("home") {
            MainMusicScreen(viewModel = viewModel, navController = navController)
        }

        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                navController = navController
            )
        }

        composable("premium") {
            PremiumScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "playlist_detail/{playlistId}",
            arguments = listOf(navArgument("playlistId") { type = NavType.IntType })
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getInt("playlistId") ?: -1
            PlaylistDetailScreen(
                viewModel = viewModel,
                playlistId = playlistId,
                navController = navController,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "song_picker/{playlistId}",
            arguments = listOf(navArgument("playlistId") { type = NavType.IntType })
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getInt("playlistId") ?: -1
            SongPickerScreen(
                viewModel = viewModel,
                playlistId = playlistId,
                onBack = { navController.popBackStack() }
            )
        }
        composable("youtube_download") {
            YoutubeDownloadScreen(
                musicViewModel = viewModel,
                onBack = {
                    // İndirme bittiğinde müzik listesini yenile
                    viewModel.scanMusic(force = true)
                    navController.popBackStack()
                },
                onNavigateToPremium = {
                    navController.navigate("premium")
                }
            )
        }

        composable("lyrics_search_results") {
            LyricsSearchResultsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onVideoSelected = {
                    // Video seçildikten sonra geri dön
                    viewModel.scanMusic(force = true)
                    navController.popBackStack()
                }
            )
        }

    }
}
