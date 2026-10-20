
package com.violetmusic.app

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay

private val Background = Color(0xFF0B0910)
private val Panel = Color(0xFF17131F)
private val PanelLight = Color(0xFF211A2D)
private val Violet = Color(0xFFAA72FF)
private val VioletLight = Color(0xFFD4B5FF)
private val MainText = Color(0xFFF7F2FF)
private val MutedText = Color(0xFFA69BAF)

private data class ImportedSong(
    val title: String,
    val uri: Uri
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(11, 9, 16)
        window.navigationBarColor = android.graphics.Color.rgb(11, 9, 16)
        window.decorView.systemUiVisibility = 0

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Violet,
                    background = Background,
                    surface = Panel,
                    onSurface = MainText,
                    secondary = VioletLight
                )
            ) {
                VioletMusicApp()
            }
        }
    }
}

@Composable
private fun VioletMusicApp() {
    val context = LocalContext.current
    val preferences = remember {
        context.getSharedPreferences("violet_library", 0)
    }

    val songs = remember {
        mutableStateListOf<ImportedSong>().apply {
            val count = preferences.getInt("count", 0)
            repeat(count) { index ->
                val uriText = preferences.getString("uri_$index", null)
                val title = preferences.getString("title_$index", null)
                if (uriText != null && title != null) {
                    add(ImportedSong(title, Uri.parse(uriText)))
                }
            }
        }
    }

    val player = remember {
        ExoPlayer.Builder(context).build()
    }

    var currentIndex by remember { mutableIntStateOf(-1) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackPosition by remember { mutableLongStateOf(0L) }
    var playbackDuration by remember { mutableLongStateOf(0L) }
    var statusMessage by remember { mutableStateOf("Importa una canción para comenzar") }

    fun saveLibrary() {
        preferences.edit().apply {
            putInt("count", songs.size)
            songs.forEachIndexed { index, song ->
                putString("uri_$index", song.uri.toString())
                putString("title_$index", song.title)
            }
            apply()
        }
    }

    fun playSong(index: Int) {
        if (index !in songs.indices) return
        try {
            player.setMediaItems(
                songs.map { song ->
                    MediaItem.Builder()
                        .setUri(song.uri)
                        .setMediaId(song.uri.toString())
                        .build()
                },
                index,
                0L
            )
            currentIndex = index
            player.prepare()
            player.play()
            statusMessage = "Reproduciendo"
        } catch (_: Exception) {
            statusMessage = "No se pudo abrir esta canción"
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int
            ) {
                currentIndex = player.currentMediaItemIndex
                playbackPosition = 0L
                playbackDuration = player.duration.coerceAtLeast(0L)
            }

            override fun onPlaybackStateChanged(state: Int) {
                playbackDuration = player.duration.coerceAtLeast(0L)
                if (state == Player.STATE_ENDED) {
                    isPlaying = false
                }
                if (state == Player.STATE_READY) {
                    statusMessage = if (player.isPlaying) {
                        "Reproduciendo"
                    } else {
                        "Lista para reproducir"
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                statusMessage = "No se pudo reproducir este archivo"
                isPlaying = false
            }
        }

        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player) {
        while (true) {
            if (player.isPlaying) {
                playbackPosition = player.currentPosition.coerceAtLeast(0L)
                playbackDuration = player.duration.coerceAtLeast(0L)
            }
            delay(500)
        }
    }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult

        val oldCount = songs.size
        var addedCount = 0

        uris.forEach { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Algunos proveedores no permiten permisos persistentes.
            } catch (_: UnsupportedOperationException) {
                // El archivo puede seguir disponible durante esta sesión.
            }

            val title = context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && column >= 0) {
                    cursor.getString(column)
                } else {
                    null
                }
            } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Canción"

            if (songs.none { it.uri == uri }) {
                songs.add(ImportedSong(title, uri))
                addedCount++
            }
        }

        saveLibrary()

        if (addedCount > 0) {
            statusMessage = "$addedCount canción(es) importada(s)"
            // Al importar, comienza a sonar la primera canción recién añadida.
            playSong(oldCount)
        } else {
            statusMessage = "Esas canciones ya están en tu biblioteca"
        }
    }

    val currentSong = songs.getOrNull(currentIndex)
    val progress = if (playbackDuration > 0L) {
        playbackPosition.toFloat() / playbackDuration.toFloat()
    } else {
        0f
    }.coerceIn(0f, 1f)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = 14.dp,
                    bottom = 20.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            Color(0xFFC69AFF),
                                            Color(0xFF7540D8),
                                            Color(0xFF3A1C68)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "V",
                                color = Color.White,
                                fontSize = 29.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "VIOLET",
                                color = MainText,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 2.sp
                            )
                            Text(
                                "M U S I C",
                                color = VioletLight,
                                fontSize = 9.sp,
                                letterSpacing = 3.sp
                            )
                        }

                        Text("♫", color = VioletLight, fontSize = 30.sp)
                    }
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF392052),
                                        Color(0xFF21142F),
                                        Panel
                                    )
                                )
                            )
                            .border(
                                1.dp,
                                Color(0xFF51336C),
                                RoundedCornerShape(24.dp)
                            )
                            .padding(21.dp)
                    ) {
                        Text(
                            "TU MÚSICA, TU UNIVERSO",
                            color = VioletLight,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Siente cada\nnota.",
                            color = MainText,
                            fontSize = 34.sp,
                            lineHeight = 39.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Tu música. Tu ritmo. Tu espacio.",
                            color = Color(0xFFD1C2DE),
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            statusMessage,
                            color = VioletLight,
                            fontSize = 12.sp
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Tu biblioteca musical",
                                color = MainText,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${songs.size} canción(es) importada(s)",
                                color = MutedText,
                                fontSize = 12.sp
                            )
                        }
                        Text(
                            "+ Importar",
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Violet)
                                .clickable {
                                    audioPicker.launch(arrayOf("audio/*"))
                                }
                                .padding(horizontal = 13.dp, vertical = 10.dp),
                            color = Background,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (songs.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(Panel)
                                .border(
                                    1.dp,
                                    PanelLight,
                                    RoundedCornerShape(20.dp)
                                )
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("♫", color = VioletLight, fontSize = 40.sp)
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Tu biblioteca está vacía",
                                color = MainText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Importa archivos de audio y toca una canción para escucharla.",
                                color = MutedText,
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "SELECCIONAR MÚSICA",
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Violet)
                                    .clickable {
                                        audioPicker.launch(arrayOf("audio/*"))
                                    }
                                    .padding(horizontal = 18.dp, vertical = 12.dp),
                                color = Background,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    itemsIndexed(
                        items = songs,
                        key = { _, song -> song.uri.toString() }
                    ) { index, song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(17.dp))
                                .background(
                                    if (index == currentIndex) {
                                        Color(0xFF2A1D39)
                                    } else {
                                        Panel
                                    }
                                )
                                .border(
                                    1.dp,
                                    if (index == currentIndex) Violet.copy(alpha = 0.6f)
                                    else PanelLight,
                                    RoundedCornerShape(17.dp)
                                )
                                .clickable { playSong(index) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                Color(0xFFB28AFF),
                                                Color(0xFF392064)
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (index == currentIndex && isPlaying) "♫" else "♪",
                                    color = Color.White,
                                    fontSize = 25.sp
                                )
                            }

                            Spacer(Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    song.title,
                                    color = MainText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    if (index == currentIndex && isPlaying) {
                                        "Reproduciendo ahora"
                                    } else {
                                        "Archivo de tu dispositivo"
                                    },
                                    color = if (index == currentIndex) VioletLight else MutedText,
                                    fontSize = 11.sp
                                )
                            }

                            Text(
                                if (index == currentIndex && isPlaying) "Ⅱ" else "▶",
                                color = VioletLight,
                                fontSize = 20.sp
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF251A33))
                    .border(
                        1.dp,
                        Color(0xFF4A315D),
                        RoundedCornerShape(18.dp)
                    )
                    .padding(horizontal = 13.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Violet, Color(0xFF553080))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("♫", color = Color.White, fontSize = 23.sp)
                    }

                    Spacer(Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            currentSong?.title ?: "Selecciona una canción",
                            color = MainText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (currentSong == null) "VIOLET Music" else statusMessage,
                            color = MutedText,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }

                    Text(
                        "Ⅱ",
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                if (player.isPlaying) {
                                    player.pause()
                                } else if (currentIndex >= 0) {
                                    player.play()
                                } else if (songs.isNotEmpty()) {
                                    playSong(0)
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        color = VioletLight,
                        fontSize = 21.sp
                    )

                    Text(
                        "»",
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                if (songs.isNotEmpty()) {
                                    if (currentIndex >= 0 &&
                                        currentIndex < songs.lastIndex
                                    ) {
                                        player.seekToNextMediaItem()
                                    } else {
                                        playSong(0)
                                    }
                                }
                            }
                            .padding(start = 7.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        color = VioletLight,
                        fontSize = 25.sp
                    )
                }

                Slider(
                    value = progress,
                    onValueChange = { fraction ->
                        if (playbackDuration > 0L) {
                            val newPosition =
                                (fraction * playbackDuration).toLong()
                            player.seekTo(newPosition)
                            playbackPosition = newPosition
                        }
                    },
                    enabled = currentSong != null && playbackDuration > 0L,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp),
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = VioletLight,
                        activeTrackColor = Violet,
                        inactiveTrackColor = Color(0xFF554460)
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        formatTime(playbackPosition),
                        color = MutedText,
                        fontSize = 10.sp
                    )
                    Text(
                        formatTime(playbackDuration),
                        color = MutedText,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}
