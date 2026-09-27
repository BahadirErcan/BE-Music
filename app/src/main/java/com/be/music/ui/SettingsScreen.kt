package com.be.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.be.music.R
import com.be.music.premium.PremiumManager
import com.be.music.update.UpdateChecker
import com.be.music.update.UpdateResult
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    navController: androidx.navigation.NavController? = null
) {
    val settings by viewModel.filterSettingsState.collectAsState()
    val themeStyle by viewModel.themeStyle.collectAsState()
    val timerRemaining by viewModel.sleepTimerRemaining.collectAsState()
    val timerActive by viewModel.sleepTimerActive.collectAsState()

    var folderInput by remember { mutableStateOf("") }
    var showLanguageDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val keepAlivePrefs = remember {
        context.getSharedPreferences("premium_prefs", android.content.Context.MODE_PRIVATE)
    }
    var keepAliveEnabled by remember { mutableStateOf(keepAlivePrefs.getBoolean("keep_alive_enabled", false)) }

    val languageOptions = listOf(
        "SYSTEM" to stringResource(R.string.lang_system),
        "en" to stringResource(R.string.lang_en),
        "tr" to stringResource(R.string.lang_tr),
        "de" to stringResource(R.string.lang_de),
        "pt" to stringResource(R.string.lang_pt),
        "ar" to stringResource(R.string.lang_ar),
        "bn" to stringResource(R.string.lang_bn),
        "es" to stringResource(R.string.lang_es),
        "fr" to stringResource(R.string.lang_fr),
        "zh" to stringResource(R.string.lang_zh),
        "ru" to stringResource(R.string.lang_ru)
    )
    val currentLangLabel = languageOptions.firstOrNull { it.first == settings.language }?.second ?: stringResource(R.string.lang_system)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Dil Secimi
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.language_select),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = currentLangLabel,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                            )
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { showLanguageDialog = true }
                            )
                        }
                    }
                }
            }

            // Theme Manager Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.theme),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ThemeStyle.values().forEach { style ->
                                val selected = themeStyle == style
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.setTheme(style) },
                                    label = { 
                                        Text(when(style) {
                                            ThemeStyle.LIGHT -> stringResource(R.string.light)
                                            ThemeStyle.DARK -> stringResource(R.string.dark)
                                            ThemeStyle.SYSTEM -> stringResource(R.string.system_default)
                                        })
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // Color Selection Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.color),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        val colorOptions = listOf("ACCENT", "GREEN", "BLUE", "RED", "PURPLE", "YELLOW")
                        val firstRow = colorOptions.take(3)
                        val secondRow = colorOptions.drop(3)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            firstRow.forEach { c ->
                                val selected = settings.themeColor == c
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.updateFilterSettings(settings.copy(themeColor = c)) },
                                    label = {
                                        Text(when (c) {
                                            "ACCENT" -> stringResource(R.string.accent)
                                            "GREEN" -> stringResource(R.string.green)
                                            "BLUE" -> stringResource(R.string.blue)
                                            "RED" -> stringResource(R.string.red)
                                            "PURPLE" -> stringResource(R.string.purple)
                                            "YELLOW" -> stringResource(R.string.yellow)
                                            else -> c
                                        })
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            secondRow.forEach { c ->
                                val selected = settings.themeColor == c
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.updateFilterSettings(settings.copy(themeColor = c)) },
                                    label = {
                                        Text(when (c) {
                                            "ACCENT" -> stringResource(R.string.accent)
                                            "GREEN" -> stringResource(R.string.green)
                                            "BLUE" -> stringResource(R.string.blue)
                                            "RED" -> stringResource(R.string.red)
                                            "PURPLE" -> stringResource(R.string.purple)
                                            "YELLOW" -> stringResource(R.string.yellow)
                                            else -> c
                                        })
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        if (PremiumManager.isPremiumStatic) {
                            Spacer(modifier = Modifier.height(8.dp))
                            var showColorPicker by remember { mutableStateOf(false) }
                            val customColor = remember { mutableStateOf<Color?>(null) }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilterChip(
                                    selected = settings.themeColor == "CUSTOM",
                                    onClick = {
                                        showColorPicker = true
                                    },
                                    label = { Text(stringResource(R.string.custom_color)) },
                                    modifier = Modifier.weight(1f)
                                )
                                if (settings.themeColor == "CUSTOM") {
                                    IconButton(onClick = {
                                        viewModel.updateFilterSettings(settings.copy(themeColor = "ACCENT"))
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            if (showColorPicker) {
                                val red = remember { mutableIntStateOf(29) }
                                val green = remember { mutableIntStateOf(185) }
                                val blue = remember { mutableIntStateOf(84) }
                                val previewColor = Color(red.intValue, green.intValue, blue.intValue)

                                AlertDialog(
                                    onDismissRequest = { showColorPicker = false },
                                    title = { Text(stringResource(R.string.select_custom_color), fontWeight = FontWeight.Bold) },
                                    text = {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Box(
                                                modifier = Modifier
                                                    .size(80.dp)
                                                    .clip(RoundedCornerShape(40.dp))
                                                    .background(previewColor),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    "RGB",
                                                    color = if ((red.intValue * 299 + green.intValue * 587 + blue.intValue * 114) / 1000 > 128)
                                                        Color.Black else Color.White,
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(20.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("R", color = Color.Red, fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp))
                                                Slider(
                                                    value = red.intValue.toFloat(),
                                                    onValueChange = { red.intValue = it.toInt() },
                                                    valueRange = 0f..255f,
                                                    modifier = Modifier.weight(1f),
                                                    colors = SliderDefaults.colors(
                                                        thumbColor = Color.Red,
                                                        activeTrackColor = Color.Red
                                                    )
                                                )
                                                Text(
                                                    red.intValue.toString(),
                                                    modifier = Modifier.width(36.dp),
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("G", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp))
                                                Slider(
                                                    value = green.intValue.toFloat(),
                                                    onValueChange = { green.intValue = it.toInt() },
                                                    valueRange = 0f..255f,
                                                    modifier = Modifier.weight(1f),
                                                    colors = SliderDefaults.colors(
                                                        thumbColor = Color(0xFF4CAF50),
                                                        activeTrackColor = Color(0xFF4CAF50)
                                                    )
                                                )
                                                Text(
                                                    green.intValue.toString(),
                                                    modifier = Modifier.width(36.dp),
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("B", color = Color(0xFF2196F3), fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp))
                                                Slider(
                                                    value = blue.intValue.toFloat(),
                                                    onValueChange = { blue.intValue = it.toInt() },
                                                    valueRange = 0f..255f,
                                                    modifier = Modifier.weight(1f),
                                                    colors = SliderDefaults.colors(
                                                        thumbColor = Color(0xFF2196F3),
                                                        activeTrackColor = Color(0xFF2196F3)
                                                    )
                                                )
                                                Text(
                                                    blue.intValue.toString(),
                                                    modifier = Modifier.width(36.dp),
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                String.format("#%06X", (red.intValue shl 16) or (green.intValue shl 8) or blue.intValue),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    confirmButton = {
                                        Button(onClick = {
                                            val colorInt = (red.intValue shl 16) or (green.intValue shl 8) or blue.intValue or 0xFF000000.toInt()
                                            viewModel.updateFilterSettings(settings.copy(themeColor = "CUSTOM"))
                                            val prefs = context.getSharedPreferences("premium_prefs", android.content.Context.MODE_PRIVATE)
                                            prefs.edit().putInt("custom_theme_color", colorInt).apply()
                                            showColorPicker = false
                                        }) {
                                            Text(stringResource(R.string.apply))
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showColorPicker = false }) {
                                            Text(stringResource(R.string.cancel))
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Playlist Settings Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.playlist_settings),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.auto_playlist_name),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            if (!PremiumManager.isPremiumStatic) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = Color(0xFFFFC107)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.premium_only_note),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = settings.autoPlaylistName,
                            onValueChange = { value ->
                                if (PremiumManager.isPremiumStatic) {
                                    viewModel.updateFilterSettings(settings.copy(autoPlaylistName = value))
                                }
                            },
                            label = { Text(stringResource(R.string.auto_playlist_name)) },
                            singleLine = true,
                            enabled = PremiumManager.isPremiumStatic,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !PremiumManager.isPremiumStatic) {
                                    navController?.navigate("premium")
                                },
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = if (!PremiumManager.isPremiumStatic) {
                                {
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = Color(0xFFFFC107)
                                    )
                                }
                            } else null
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = stringResource(R.string.number_prefix),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (!PremiumManager.isPremiumStatic) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            Icons.Default.Star,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = Color(0xFFFFC107)
                                        )
                                    }
                                }
                                Text(
                                    text = stringResource(R.string.number_position_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.autoPlaylistNumberPosition == "PREFIX",
                                enabled = PremiumManager.isPremiumStatic,
                                onCheckedChange = { enabled ->
                                    if (PremiumManager.isPremiumStatic) {
                                        viewModel.updateFilterSettings(
                                            settings.copy(
                                                autoPlaylistNumberPosition = if (enabled) "PREFIX" else "SUFFIX"
                                            )
                                        )
                                    } else {
                                        navController?.navigate("premium")
                                    }
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.current_position_prefix),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (settings.autoPlaylistNumberPosition == "PREFIX") "1-${
                                    settings.autoPlaylistName.ifBlank { "List" }
                                }" else "${settings.autoPlaylistName.ifBlank { "List" }}-1",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                        }
                    }
                }
            }

            // Sleep Timer Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.sleep_timer),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Timer status display
                        if (timerActive && timerRemaining > 0L) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = formatDurationSeconds(timerRemaining.toLong()),
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = stringResource(R.string.timer_remaining, timerRemaining / 60, timerRemaining % 60),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            TextButton(onClick = { viewModel.cancelSleepTimer() }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.stop_timer))
                            }
                        } else {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = settings.sleepTimerMinutes.toString(),
                                    onValueChange = { value ->
                                        val minutes = value.toIntOrNull() ?: 0
                                        viewModel.updateFilterSettings(settings.copy(sleepTimerMinutes = minutes))
                                    },
                                    label = { Text(stringResource(R.string.timer_minutes)) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                    )
                                )
                                Button(onClick = {
                                    if (settings.sleepTimerMinutes > 0) viewModel.startSleepTimer(settings.sleepTimerMinutes)
                                }) {
                                    Text(stringResource(R.string.start_timer))
                                }
                            }
                        }
                    }
                }
            }

            // Skip Silence Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = stringResource(R.string.skip_silence), style = MaterialTheme.typography.bodyLarge)
                            Text(text = stringResource(R.string.skip_silence_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settings.skipSilenceEnabled,
                            onCheckedChange = { viewModel.updateFilterSettings(settings.copy(skipSilenceEnabled = it)) }
                        )
                    }
                }
            }

            // Download Options Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.download_options),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(text = stringResource(R.string.download_lyrics), style = MaterialTheme.typography.bodyLarge)
                                Text(text = stringResource(R.string.download_lyrics_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (PremiumManager.isPremiumStatic) {
                                    Text(
                                        text = stringResource(R.string.lyrics_speed_note),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            Switch(
                                checked = settings.downloadLyricsEnabled,
                                onCheckedChange = { viewModel.updateFilterSettings(settings.copy(downloadLyricsEnabled = it)) }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(text = stringResource(R.string.video_quality), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        val qualityOptions = listOf("240p", "360p", "480p", "720p", "1080p", stringResource(R.string.highest_quality))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            qualityOptions.forEach { quality ->
                                val selected = settings.preferredVideoQuality.equals(quality, ignoreCase = true)
                                val isPremiumQuality = PremiumManager.isPremiumStatic.not() &&
                                    (quality.equals("1080p", ignoreCase = true) ||
                                     quality.equals(stringResource(R.string.highest_quality), ignoreCase = true))
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        if (isPremiumQuality) {
                                            navController?.navigate("premium")
                                        } else {
                                            viewModel.updateFilterSettings(settings.copy(preferredVideoQuality = quality))
                                        }
                                    },
                                    label = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(quality)
                                            if (isPremiumQuality) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    Icons.Default.Star,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(12.dp),
                                                    tint = Color(0xFFFFC107)
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                        }

                        // Parallel Download Count (Premium)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = stringResource(R.string.parallel_downloads), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                if (!PremiumManager.isPremiumStatic) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = Color(0xFFFFC107)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(text = stringResource(R.string.parallel_downloads_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(text = "${settings.parallelDownloadCount}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 16.dp))
                        }
                        Slider(
                            value = settings.parallelDownloadCount.toFloat(),
                            onValueChange = {
                                if (PremiumManager.isPremiumStatic) {
                                    viewModel.updateFilterSettings(settings.copy(parallelDownloadCount = it.toInt().coerceIn(1, 10)))
                                } else {
                                    navController?.navigate("premium")
                                }
                            },
                            valueRange = 1f..10f,
                            steps = 8,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = PremiumManager.isPremiumStatic
                        )
                        if (!PremiumManager.isPremiumStatic) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.premium_only_note),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Folder Filtering Mode Selection Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.folder_filtering_mode),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        val modes = listOf("NONE", "BLACKLIST", "WHITELIST")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            modes.forEach { mode ->
                                val selected = settings.filterMode == mode
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        viewModel.updateFilterSettings(settings.copy(filterMode = mode))
                                    },
                                    label = { 
                                        // Madde 7: Blacklist/Whitelist label'larını ters çeviriyoruz
                                        Text(when(mode) {
                                            "NONE" -> stringResource(R.string.none)
                                            "BLACKLIST" -> stringResource(R.string.whitelist) // Ters mantık!
                                            "WHITELIST" -> stringResource(R.string.blacklist) // Ters mantık!
                                            else -> mode
                                        })
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // Minimum Duration Settings Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.min_song_duration),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${settings.durationThresholdSec}s",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Slider(
                            value = settings.durationThresholdSec.toFloat(),
                            onValueChange = {
                                viewModel.updateFilterSettings(settings.copy(durationThresholdSec = it.toInt()))
                            },
                            valueRange = 0f..180f,
                            steps = 18
                        )
                    }
                }
            }

            // Folder Lists Configuration Section
            if (settings.filterMode != "NONE") {
                item {
                    // Madde 7: Burada da ters isimleri gösterelim
                    val visualMode = when(settings.filterMode) {
                        "BLACKLIST" -> stringResource(R.string.whitelist)
                        "WHITELIST" -> stringResource(R.string.blacklist)
                        else -> ""
                    }
                    Text(
                        text = stringResource(R.string.configured_folders, visualMode),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = folderInput,
                            onValueChange = { folderInput = it },
                            placeholder = { Text("/sdcard/Music/Folder") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                if (folderInput.isNotBlank()) {
                                    val currentFolders = settings.folders.toMutableList()
                                    if (!currentFolders.contains(folderInput)) {
                                        currentFolders.add(folderInput)
                                        viewModel.updateFilterSettings(settings.copy(folders = currentFolders))
                                        folderInput = ""
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.add))
                        }
                    }
                }

                items(settings.folders) { path ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = path, style = MaterialTheme.typography.bodyMedium)
                            IconButton(
                                onClick = {
                                    val currentFolders = settings.folders.toMutableList()
                                    currentFolders.remove(path)
                                    viewModel.updateFilterSettings(settings.copy(folders = currentFolders))
                                }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            
            // Uygulamayı Açık Tut (Premium)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.keep_alive),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (!PremiumManager.isPremiumStatic) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = Color(0xFFFFC107)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.keep_alive_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = keepAliveEnabled,
                            onCheckedChange = { enabled ->
                                if (PremiumManager.isPremiumStatic) {
                                    keepAliveEnabled = enabled
                                    keepAlivePrefs.edit().putBoolean("keep_alive_enabled", enabled).apply()
                                } else {
                                    navController?.navigate("premium")
                                }
                            },
                            enabled = PremiumManager.isPremiumStatic
                        )
                    }
                }
            }

            // Hakkında Kartı (Madde 9)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.about),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "${stringResource(R.string.developer)}: Bahadır",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${stringResource(R.string.project)}: BE OS",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = stringResource(R.string.licenses_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        Text(
                            text = "• youtubedl-android (GPL v3)\n• yt-dlp (Unlicense)\n• ExoPlayer / Media3 (Apache 2.0)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Güncellemeleri Kontrol Et
            item {
                val scope = rememberCoroutineScope()
                val updateChecker = remember {
                    UpdateChecker(
                        okhttp3.OkHttpClient.Builder()
                            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                            .build()
                    )
                }
                var updateState by remember { mutableStateOf<String?>(null) }
                var updateInfo by remember { mutableStateOf<com.be.music.update.UpdateInfo?>(null) }
                var isChecking by remember { mutableStateOf(false) }

                fun performUpdateCheck() {
                    isChecking = true
                    updateState = null
                    scope.launch {
                        when (val result = updateChecker.checkForUpdate()) {
                            is UpdateResult.UpToDate -> {
                                updateState = "up_to_date"
                                isChecking = false
                            }
                            is UpdateResult.UpdateAvailable -> {
                                updateInfo = result.info
                                updateState = "available"
                                isChecking = false
                            }
                            is UpdateResult.MandatoryUpdate -> {
                                updateInfo = result.info
                                updateState = "mandatory"
                                isChecking = false
                            }
                            is UpdateResult.Error -> {
                                updateState = "error"
                                isChecking = false
                            }
                        }
                    }
                }

                /*

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.check_updates),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        when {
                            isChecking -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        stringResource(R.string.checking_for_updates),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            updateState == "up_to_date" -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        stringResource(R.string.up_to_date),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            updateState == "error" -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Error,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        stringResource(R.string.update_error),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { performUpdateCheck() },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(stringResource(R.string.update_retry))
                                }
                            }
                            (updateState == "available" || updateState == "mandatory") && updateInfo != null -> {
                                val info = updateInfo!!
                                val isMandatory = updateState == "mandatory"

                                if (isMandatory) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Warning,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                stringResource(R.string.update_mandatory),
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                Text(
                                    stringResource(R.string.update_available),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    stringResource(R.string.update_version, info.latest_version_name),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (info.file_size.isNotBlank() && info.file_size != "0 MB") {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        info.file_size,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (info.changelog.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        stringResource(R.string.update_changelog),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        info.changelog,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 18.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        val intent = android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse(info.download_url)
                                        )
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Download,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Text(stringResource(R.string.update_download))
                                }
                            }
                            else -> {
                                OutlinedButton(
                                    onClick = { performUpdateCheck() },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        Icons.Default.SystemUpdate,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Text(stringResource(R.string.check_updates))
                                }
                            }
                        }
                    }
                }*/
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))

                val activity = context as? android.app.Activity
                Button(
                    onClick = {
                        activity?.let {
                            com.be.music.BeMusicApplication.interstitialAdManager.loadAd()
                            com.be.music.BeMusicApplication.interstitialAdManager.showAd(it)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(stringResource(R.string.support_us_ad))
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }

        if (showLanguageDialog) {
            AlertDialog(
                onDismissRequest = { showLanguageDialog = false },
                title = { Text(stringResource(R.string.language_select), fontWeight = FontWeight.Bold) },
                text = {
                    LazyColumn {
                        items(languageOptions) { (code, label) ->
                            ListItem(
                                headlineContent = { Text(label) },
                                leadingContent = {
                                    if (settings.language == code) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                modifier = Modifier.clickable {
                                    showLanguageDialog = false
                                    viewModel.setLanguage(code)
                                    context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                                        .edit().putString("app_language", code).apply()
                                    (context as? android.app.Activity)?.recreate()
                                }
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLanguageDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
}

