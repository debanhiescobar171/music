package com.violetmusic.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Background = Color(0xFF0B0910)
private val Panel = Color(0xFF17131F)
private val PanelLight = Color(0xFF251B32)
private val Violet = Color(0xFFAA72FF)
private val VioletLight = Color(0xFFD4B5FF)
private val MainText = Color(0xFFF7F2FF)
private val MutedText = Color(0xFFA69BAF)

private data class ImportedSong(
    val title: String,
    val uri: Uri
)

private data class SongArtwork(
    val bitmap: Bitmap? = null,
    val metadataTitle: String? = null,
    val artist: String? = null
)

private data class MusicPlaylist(
    val id: String,
    val name: String,
    val songUris: List<String>
)

private enum class MusicTab(val label: String, val symbol: String) {
    HOME("Inicio", "⌂"),
    LIBRARY("Biblioteca", "♫"),
    FAVORITES("Favoritos", "♡"),
    PLAYLISTS("Playlists", "≡")
}

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
                MusicApp()
            }
        }
    }
}

private fun loadPlaylists(preferences: android.content.SharedPreferences): List<MusicPlaylist> {
    val result = mutableListOf<MusicPlaylist>()

    try {
        val json = JSONArray(preferences.getString("playlists_json", "[]") ?: "[]")

        for (i in 0 until json.length()) {
            val item = json.optJSONObject(i) ?: continue
            val id = item.optString("id")
            val name = item.optString("name")
            val uris = item.optJSONArray("songs") ?: JSONArray()
            val songUris = mutableListOf<String>()

            for (j in 0 until uris.length()) {
                val uri = uris.optString(j)
                if (uri.isNotBlank()) songUris.add(uri)
            }

            if (id.isNotBlank() && name.isNotBlank()) {
                result.add(MusicPlaylist(id, name, songUris))
            }
        }
    } catch (_: Exception) {
        // Si los datos guardados no se pueden leer, se inicia con listas vacías.
    }

    return result
}

private fun savePlaylists(
    preferences: android.content.SharedPreferences,
    playlists: List<MusicPlaylist>
) {
    val json = JSONArray()

    playlists.forEach { playlist ->
        val item = JSONObject()
        item.put("id", playlist.id)
        item.put("name", playlist.name)

        val songs = JSONArray()
        playlist.songUris.forEach { songs.put(it) }

        item.put("songs", songs)
        json.put(item)
    }

    preferences.edit().putString("playlists_json", json.toString()).apply()
}

@Composable
private fun MusicApp() {
    val context = LocalContext.current

    val preferences = remember {
        context.getSharedPreferences("music_library", Context.MODE_PRIVATE)
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

    val favorites = remember {
        mutableStateListOf<String>().apply {
            addAll(
                preferences.getStringSet("favorites", emptySet())
                    ?: emptySet()
            )
        }
    }

    val playlists = remember {
        mutableStateListOf<MusicPlaylist>().apply {
            addAll(loadPlaylists(preferences))
        }
    }

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
        }
    }

    var playbackQueue by remember {
        mutableStateOf<List<ImportedSong>>(emptyList())
    }
    var currentIndex by remember { mutableIntStateOf(-1) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackPosition by remember { mutableLongStateOf(0L) }
    var playbackDuration by remember { mutableLongStateOf(0L) }
    var statusMessage by remember {
        mutableStateOf("Importa tu música para comenzar")
    }
    var selectedTab by remember { mutableStateOf(MusicTab.HOME) }
    var showFullPlayer by remember { mutableStateOf(false) }
    var speedMenuExpanded by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }

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

    fun saveFavorites() {
        preferences.edit()
            .putStringSet("favorites", favorites.toSet())
            .apply()
    }

    fun saveCurrentPlaylists() {
        savePlaylists(preferences, playlists.toList())
    }

    fun toggleFavorite(song: ImportedSong) {
        val key = song.uri.toString()

        if (favorites.contains(key)) {
            favorites.remove(key)
        } else {
            favorites.add(key)
        }

        saveFavorites()
    }

    fun playQueue(queue: List<ImportedSong>, startIndex: Int) {
        if (queue.isEmpty() || startIndex !in queue.indices) return

        try {
            val mediaItems = queue.map { song ->
                MediaItem.Builder()
                    .setUri(song.uri)
                    .setMediaId(song.uri.toString())
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .build()
                    )
                    .build()
            }

            playbackQueue = queue.toList()
            currentIndex = startIndex
            playbackPosition = 0L
            playbackDuration = 0L

            player.setMediaItems(mediaItems, startIndex, 0L)
            player.prepare()
            player.play()

            statusMessage = "Reproduciendo"
            showFullPlayer = true
        } catch (_: Exception) {
            statusMessage = "No se pudo abrir esta canción"
        }
    }

    fun playSong(index: Int) {
        playQueue(songs.toList(), index)
    }

    fun updatePlaylist(
        playlistId: String,
        transform: (MusicPlaylist) -> MusicPlaylist
    ) {
        val index = playlists.indexOfFirst { it.id == playlistId }
        if (index < 0) return

        playlists[index] = transform(playlists[index])
        saveCurrentPlaylists()
    }

    fun createPlaylist(name: String) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return

        playlists.add(
            MusicPlaylist(
                id = UUID.randomUUID().toString(),
                name = cleanName,
                songUris = emptyList()
            )
        )
        saveCurrentPlaylists()
    }

    fun playPlaylistSong(playlistId: String, uriText: String) {
        val playlist = playlists.firstOrNull { it.id == playlistId } ?: return

        val queue = playlist.songUris.mapNotNull { uri ->
            songs.firstOrNull { it.uri.toString() == uri }
        }

        val startIndex = queue.indexOfFirst {
            it.uri.toString() == uriText
        }

        if (startIndex >= 0) playQueue(queue, startIndex)
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

            override fun onPlayerError(
                error: androidx.media3.common.PlaybackException
            ) {
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
            delay(400)
        }
    }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult

        var addedCount = 0
        var firstNewIndex = -1

        uris.forEach { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // Algunos proveedores no permiten conservar el permiso.
            }

            val title = try {
                context.contentResolver.query(
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
                }
            } catch (_: Exception) {
                null
            } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Canción"

            if (songs.none { it.uri == uri }) {
                if (firstNewIndex < 0) firstNewIndex = songs.size
                songs.add(ImportedSong(title, uri))
                addedCount++
            }
        }

        saveLibrary()

        if (addedCount > 0) {
            statusMessage = "$addedCount canción(es) importada(s)"
            playSong(firstNewIndex)
        } else {
            statusMessage = "Esas canciones ya están en tu biblioteca"
        }
    }

    val currentSong = playbackQueue.getOrNull(currentIndex)

    val progress = (
        if (playbackDuration > 0L) {
            playbackPosition.toFloat() / playbackDuration.toFloat()
        } else {
            0f
        }
    ).coerceIn(0f, 1f)

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
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (showFullPlayer && currentSong != null) {
                    FullPlayerScreen(
                        song = currentSong,
                        player = player,
                        isPlaying = isPlaying,
                        position = playbackPosition,
                        duration = playbackDuration,
                        progress = progress,
                        statusMessage = statusMessage,
                        playbackSpeed = playbackSpeed,
                        speedMenuExpanded = speedMenuExpanded,
                        onSpeedMenuChange = { speedMenuExpanded = it },
                        onSpeedChange = {
                            playbackSpeed = it
                            player.playbackParameters = PlaybackParameters(it)
                        },
                        onSeek = { fraction ->
                            if (playbackDuration > 0L) {
                                val position = (fraction * playbackDuration).toLong()
                                player.seekTo(position)
                                playbackPosition = position
                            }
                        },
                        onPlayPause = {
                            if (player.isPlaying) player.pause() else player.play()
                        },
                        onPrevious = {
                            if (player.currentPosition > 3000L) {
                                player.seekTo(0L)
                            } else if (player.hasPreviousMediaItem()) {
                                player.seekToPreviousMediaItem()
                            } else if (player.mediaItemCount > 0) {
                                player.seekTo(player.mediaItemCount - 1, 0L)
                            }
                        },
                        onNext = {
                            if (player.hasNextMediaItem()) {
                                player.seekToNextMediaItem()
                            } else if (player.mediaItemCount > 0) {
                                player.seekTo(0, 0L)
                            }
                        },
                        onRepeat = {
                            player.repeatMode = when (player.repeatMode) {
                                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
                                else -> Player.REPEAT_MODE_ALL
                            }
                        },
                        onShuffle = {
                            player.shuffleModeEnabled = !player.shuffleModeEnabled
                        },
                        onMinimize = { showFullPlayer = false },
                        isFavorite = favorites.contains(currentSong.uri.toString()),
                        onFavorite = { toggleFavorite(currentSong) }
                    )
                } else {
                    MainContent(
                        selectedTab = selectedTab,
                        songs = songs,
                        favorites = favorites,
                        playlists = playlists,
                        currentSongUri = currentSong?.uri?.toString(),
                        isPlaying = isPlaying,
                        statusMessage = statusMessage,
                        onImport = { audioPicker.launch(arrayOf("audio/*")) },
                        onPlaySong = { playSong(it) },
                        onFavorite = { toggleFavorite(it) },
                        onCreatePlaylist = { createPlaylist(it) },
                        onDeletePlaylist = { playlist ->
                            playlists.removeAll { it.id == playlist.id }
                            saveCurrentPlaylists()
                        },
                        onAddSongsToPlaylist = { playlistId, urisToAdd ->
                            updatePlaylist(playlistId) { playlist ->
                                val combined = (playlist.songUris + urisToAdd).distinct()
                                playlist.copy(songUris = combined)
                            }
                        },
                        onRemoveSongFromPlaylist = { playlistId, uri ->
                            updatePlaylist(playlistId) { playlist ->
                                playlist.copy(
                                    songUris = playlist.songUris.filterNot { it == uri }
                                )
                            }
                        },
                        onPlayPlaylistSong = { playlistId, uri ->
                            playPlaylistSong(playlistId, uri)
                        }
                    )
                }
            }

            if (currentSong != null && !showFullPlayer) {
                MiniPlayer(
                    song = currentSong,
                    isPlaying = isPlaying,
                    statusMessage = statusMessage,
                    position = playbackPosition,
                    duration = playbackDuration,
                    progress = progress,
                    onOpen = { showFullPlayer = true },
                    onPlayPause = {
                        if (player.isPlaying) player.pause() else player.play()
                    },
                    onNext = {
                        if (player.hasNextMediaItem()) {
                            player.seekToNextMediaItem()
                        } else if (player.mediaItemCount > 0) {
                            player.seekTo(0, 0L)
                        }
                    }
                )
            }

            BottomNavigationBar(
                selectedTab = selectedTab,
                onSelect = {
                    selectedTab = it
                    showFullPlayer = false
                }
            )
        }
    }
}

@Composable
private fun MainContent(
    selectedTab: MusicTab,
    songs: List<ImportedSong>,
    favorites: List<String>,
    playlists: List<MusicPlaylist>,
    currentSongUri: String?,
    isPlaying: Boolean,
    statusMessage: String,
    onImport: () -> Unit,
    onPlaySong: (Int) -> Unit,
    onFavorite: (ImportedSong) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDeletePlaylist: (MusicPlaylist) -> Unit,
    onAddSongsToPlaylist: (String, List<String>) -> Unit,
    onRemoveSongFromPlaylist: (String, String) -> Unit,
    onPlayPlaylistSong: (String, String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var selectedPlaylistId by remember { mutableStateOf<String?>(null) }
    var showAddSongsDialog by remember { mutableStateOf(false) }
    var pendingSongUris = remember { mutableStateListOf<String>() }
    var deleteCandidate by remember { mutableStateOf<MusicPlaylist?>(null) }

    LaunchedEffect(selectedTab) {
        if (selectedTab != MusicTab.PLAYLISTS) {
            selectedPlaylistId = null
        }
    }

    val selectedPlaylist = playlists.firstOrNull {
        it.id == selectedPlaylistId
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = Panel,
            title = {
                Text("Crear playlist", color = MainText, fontWeight = FontWeight.Bold)
            },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Nombre de la playlist") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPlaylistName.trim().isNotEmpty()) {
                            onCreatePlaylist(newPlaylistName)
                            newPlaylistName = ""
                            showCreateDialog = false
                        }
                    }
                ) {
                    Text("Crear", color = VioletLight)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancelar", color = MutedText)
                }
            }
        )
    }

    if (showAddSongsDialog && selectedPlaylist != null) {
        AlertDialog(
            onDismissRequest = { showAddSongsDialog = false },
            containerColor = Panel,
            title = {
                Text("Agregar canciones", color = MainText, fontWeight = FontWeight.Bold)
            },
            text = {
                if (songs.isEmpty()) {
                    Text("Primero importa canciones a tu biblioteca.", color = MutedText)
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(songs, key = { it.uri.toString() }) { song ->
                            val uri = song.uri.toString()
                            val alreadyAdded = selectedPlaylist.songUris.contains(uri)
                            val checked = pendingSongUris.contains(uri)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !alreadyAdded) {
                                        if (checked) pendingSongUris.remove(uri)
                                        else pendingSongUris.add(uri)
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = alreadyAdded || checked,
                                    enabled = !alreadyAdded,
                                    onCheckedChange = { value ->
                                        if (value) {
                                            if (!pendingSongUris.contains(uri)) {
                                                pendingSongUris.add(uri)
                                            }
                                        } else {
                                            pendingSongUris.remove(uri)
                                        }
                                    }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        song.title,
                                        color = if (alreadyAdded) MutedText else MainText,
                                        fontSize = 13.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (alreadyAdded) {
                                        Text(
                                            "Ya está en esta playlist",
                                            color = MutedText,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAddSongsToPlaylist(
                            selectedPlaylist.id,
                            pendingSongUris.toList()
                        )
                        pendingSongUris.clear()
                        showAddSongsDialog = false
                    }
                ) {
                    Text("Agregar", color = VioletLight)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingSongUris.clear()
                        showAddSongsDialog = false
                    }
                ) {
                    Text("Cancelar", color = MutedText)
                }
            }
        )
    }

    if (deleteCandidate != null) {
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            containerColor = Panel,
            title = {
                Text("Eliminar playlist", color = MainText)
            },
            text = {
                Text(
                    "¿Quieres eliminar «${deleteCandidate!!.name}»? Las canciones seguirán en tu biblioteca.",
                    color = MutedText
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteCandidate?.let(onDeletePlaylist)
                        if (selectedPlaylistId == deleteCandidate?.id) {
                            selectedPlaylistId = null
                        }
                        deleteCandidate = null
                    }
                ) {
                    Text("Eliminar", color = VioletLight)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text("Cancelar", color = MutedText)
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFFC69AFF), Color(0xFF7540D8), Color(0xFF3A1C68))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("M", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
                }

                Spacer(Modifier.width(12.dp))

                Column {
                    Text(
                        "MÚSICA",
                        color = MainText,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        "TU RITMO, TU ESPACIO",
                        color = VioletLight,
                        fontSize = 9.sp,
                        letterSpacing = 2.sp
                    )
                }
            }
        }

        when (selectedTab) {
            MusicTab.HOME -> {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF392052), Color(0xFF21142F), Panel)
                                )
                            )
                            .border(1.dp, Color(0xFF51336C), RoundedCornerShape(24.dp))
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
                        Spacer(Modifier.height(12.dp))
                        Text(statusMessage, color = VioletLight, fontSize = 12.sp)
                    }
                }

                item {
                    SectionHeader(
                        title = "Tu biblioteca musical",
                        subtitle = "${songs.size} canción(es)",
                        action = "+ Importar",
                        onAction = onImport
                    )
                }

                if (songs.isEmpty()) {
                    item {
                        EmptyPanel(
                            title = "Tu biblioteca está vacía",
                            message = "Importa archivos de audio para comenzar.",
                            action = "SELECCIONAR MÚSICA",
                            onAction = onImport
                        )
                    }
                } else {
                    itemsIndexed(songs, key = { _, song -> song.uri.toString() }) { index, song ->
                        SongRow(
                            song = song,
                            isCurrent = song.uri.toString() == currentSongUri,
                            isPlaying = song.uri.toString() == currentSongUri && isPlaying,
                            isFavorite = favorites.contains(song.uri.toString()),
                            onClick = { onPlaySong(index) },
                            onFavorite = { onFavorite(song) }
                        )
                    }
                }
            }

            MusicTab.LIBRARY -> {
                item {
                    SectionHeader(
                        title = "Biblioteca",
                        subtitle = "${songs.size} canción(es) importada(s)",
                        action = "+ Importar",
                        onAction = onImport
                    )
                }

                if (songs.isEmpty()) {
                    item {
                        EmptyPanel(
                            title = "Todavía no hay canciones",
                            message = "Importa tus archivos de audio desde tu dispositivo.",
                            action = "IMPORTAR MÚSICA",
                            onAction = onImport
                        )
                    }
                } else {
                    itemsIndexed(songs, key = { _, song -> song.uri.toString() }) { index, song ->
                        SongRow(
                            song = song,
                            isCurrent = song.uri.toString() == currentSongUri,
                            isPlaying = song.uri.toString() == currentSongUri && isPlaying,
                            isFavorite = favorites.contains(song.uri.toString()),
                            onClick = { onPlaySong(index) },
                            onFavorite = { onFavorite(song) }
                        )
                    }
                }
            }

            MusicTab.FAVORITES -> {
                val favoriteSongs = songs.filter {
                    favorites.contains(it.uri.toString())
                }

                item {
                    SectionHeader(
                        title = "Tus favoritos",
                        subtitle = "${favoriteSongs.size} canción(es)",
                        action = null,
                        onAction = {}
                    )
                }

                if (favoriteSongs.isEmpty()) {
                    item {
                        EmptyPanel(
                            title = "Aún no tienes favoritos",
                            message = "Toca el corazón de una canción para guardarla aquí.",
                            action = null,
                            onAction = {}
                        )
                    }
                } else {
                    items(favoriteSongs, key = { it.uri.toString() }) { song ->
                        val originalIndex = songs.indexOfFirst { it.uri == song.uri }

                        SongRow(
                            song = song,
                            isCurrent = song.uri.toString() == currentSongUri,
                            isPlaying = song.uri.toString() == currentSongUri && isPlaying,
                            isFavorite = true,
                            onClick = { if (originalIndex >= 0) onPlaySong(originalIndex) },
                            onFavorite = { onFavorite(song) }
                        )
                    }
                }
            }

            MusicTab.PLAYLISTS -> {
                if (selectedPlaylist == null) {
                    item {
                        SectionHeader(
                            title = "Tus playlists",
                            subtitle = "${playlists.size} lista(s) creada(s)",
                            action = "+ Crear",
                            onAction = { showCreateDialog = true }
                        )
                    }

                    if (playlists.isEmpty()) {
                        item {
                            EmptyPanel(
                                title = "Crea tu primera playlist",
                                message = "Organiza tus canciones favoritas en listas personalizadas.",
                                action = "CREAR PLAYLIST",
                                onAction = { showCreateDialog = true }
                            )
                        }
                    } else {
                        items(playlists, key = { it.id }) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Panel)
                                    .border(1.dp, PanelLight, RoundedCornerShape(18.dp))
                                    .clickable { selectedPlaylistId = playlist.id }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            Brush.linearGradient(
                                                listOf(Color(0xFFB28AFF), Color(0xFF392064))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("♫", color = Color.White, fontSize = 28.sp)
                                }

                                Spacer(Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        playlist.name,
                                        color = MainText,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${playlist.songUris.size} canción(es)",
                                        color = MutedText,
                                        fontSize = 11.sp
                                    )
                                }

                                Text(
                                    "›",
                                    color = VioletLight,
                                    fontSize = 28.sp,
                                    modifier = Modifier.padding(horizontal = 5.dp)
                                )

                                Text(
                                    "×",
                                    color = MutedText,
                                    fontSize = 22.sp,
                                    modifier = Modifier
                                        .clickable { deleteCandidate = playlist }
                                        .padding(8.dp)
                                )
                            }
                        }
                    }
                } else {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "‹ Volver a playlists",
                                modifier = Modifier
                                    .clickable { selectedPlaylistId = null }
                                    .padding(vertical = 5.dp),
                                color = VioletLight,
                                fontSize = 13.sp
                            )

                            Text(
                                selectedPlaylist.name,
                                color = MainText,
                                fontSize = 25.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                "${selectedPlaylist.songUris.size} canción(es)",
                                color = MutedText,
                                fontSize = 12.sp
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ActionChip(
                                    text = "+ Agregar canciones",
                                    onClick = {
                                        pendingSongUris.clear()
                                        showAddSongsDialog = true
                                    }
                                )
                                ActionChip(
                                    text = "Eliminar lista",
                                    onClick = { deleteCandidate = selectedPlaylist }
                                )
                            }
                        }
                    }

                    val playlistSongs = selectedPlaylist.songUris.mapNotNull { uri ->
                        songs.firstOrNull { it.uri.toString() == uri }
                    }

                    if (playlistSongs.isEmpty()) {
                        item {
                            EmptyPanel(
                                title = "Esta playlist está vacía",
                                message = "Agrega canciones de tu biblioteca para empezar a escucharla.",
                                action = "AGREGAR CANCIONES",
                                onAction = {
                                    pendingSongUris.clear()
                                    showAddSongsDialog = true
                                }
                            )
                        }
                    } else {
                        items(playlistSongs, key = { it.uri.toString() }) { song ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(17.dp))
                                    .background(Panel)
                                    .border(1.dp, PanelLight, RoundedCornerShape(17.dp))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SongArtwork(song, Modifier.size(48.dp))

                                Spacer(Modifier.width(10.dp))

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            onPlayPlaylistSong(
                                                selectedPlaylist.id,
                                                song.uri.toString()
                                            )
                                        }
                                ) {
                                    Text(
                                        song.title,
                                        color = MainText,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "Toca para reproducir",
                                        color = MutedText,
                                        fontSize = 10.sp
                                    )
                                }

                                Text(
                                    "▶",
                                    color = VioletLight,
                                    modifier = Modifier
                                        .clickable {
                                            onPlayPlaylistSong(
                                                selectedPlaylist.id,
                                                song.uri.toString()
                                            )
                                        }
                                        .padding(8.dp)
                                )

                                Text(
                                    "−",
                                    color = MutedText,
                                    fontSize = 22.sp,
                                    modifier = Modifier
                                        .clickable {
                                            onRemoveSongFromPlaylist(
                                                selectedPlaylist.id,
                                                song.uri.toString()
                                            )
                                        }
                                        .padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionChip(
    text: String,
    onClick: () -> Unit
) {
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Violet)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        color = Background,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    action: String?,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MainText, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MutedText, fontSize = 12.sp)
        }

        if (action != null) {
            Text(
                action,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Violet)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 13.dp, vertical = 10.dp),
                color = Background,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun EmptyPanel(
    title: String,
    message: String,
    action: String?,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Panel)
            .border(1.dp, PanelLight, RoundedCornerShape(20.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("♫", color = VioletLight, fontSize = 40.sp)
        Spacer(Modifier.height(10.dp))
        Text(title, color = MainText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        Text(message, color = MutedText, fontSize = 12.sp)

        if (action != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                action,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Violet)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                color = Background,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun SongRow(
    song: ImportedSong,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavorite: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(17.dp))
            .background(if (isCurrent) Color(0xFF2A1D39) else Panel)
            .border(
                1.dp,
                if (isCurrent) Violet.copy(alpha = 0.6f) else PanelLight,
                RoundedCornerShape(17.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SongArtwork(song, Modifier.size(49.dp))
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
                if (isCurrent && isPlaying) "Reproduciendo ahora" else "Archivo de tu dispositivo",
                color = if (isCurrent) VioletLight else MutedText,
                fontSize = 11.sp
            )
        }

        Text(
            if (isFavorite) "♥" else "♡",
            modifier = Modifier
                .clickable(onClick = onFavorite)
                .padding(horizontal = 9.dp, vertical = 8.dp),
            color = if (isFavorite) VioletLight else MutedText,
            fontSize = 22.sp
        )

        Text(if (isCurrent && isPlaying) "Ⅱ" else "▶", color = VioletLight, fontSize = 18.sp)
    }
}

@Composable
private fun SongArtwork(
    song: ImportedSong,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val artwork by produceState(
        initialValue = SongArtwork(),
        key1 = song.uri
    ) {
        value = withContext(Dispatchers.IO) {
            readSongArtwork(context, song.uri)
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(listOf(Color(0xFFB28AFF), Color(0xFF392064)))
            ),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = artwork.bitmap

        if (bitmap != null && !bitmap.isRecycled) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Portada de ${song.title}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text("♫", color = Color.White, fontSize = 27.sp)
        }
    }
}

private fun readSongArtwork(context: Context, uri: Uri): SongArtwork {
    val retriever = MediaMetadataRetriever()

    return try {
        retriever.setDataSource(context, uri)

        val title = retriever.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_TITLE
        )
        val artist = retriever.extractMetadata(
            MediaMetadataRetriever.METADATA_KEY_ARTIST
        )

        val bytes = retriever.embeddedPicture
        val bitmap = if (bytes != null && bytes.isNotEmpty()) {
            try {
                val bounds = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

                val sample = calculateSampleSize(bounds.outWidth, bounds.outHeight, 1200, 1200)
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }

                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }

        SongArtwork(bitmap, title, artist)
    } catch (_: Exception) {
        SongArtwork()
    } finally {
        try {
            retriever.release()
        } catch (_: Exception) {
            // No se interrumpe la interfaz si falla la liberación.
        }
    }
}

private fun calculateSampleSize(
    width: Int,
    height: Int,
    maxWidth: Int,
    maxHeight: Int
): Int {
    if (width <= 0 || height <= 0) return 1

    var sample = 1
    while (width / (sample * 2) >= maxWidth &&
        height / (sample * 2) >= maxHeight
    ) {
        sample *= 2
    }

    return sample.coerceAtLeast(1)
}

@Composable
private fun MiniPlayer(
    song: ImportedSong,
    isPlaying: Boolean,
    statusMessage: String,
    position: Long,
    duration: Long,
    progress: Float,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF251A33))
            .border(1.dp, Color(0xFF4A315D), RoundedCornerShape(18.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SongArtwork(song, Modifier.size(42.dp))
            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    song.title,
                    color = MainText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(statusMessage, color = MutedText, fontSize = 10.sp, maxLines = 1)
            }

            Text(
                if (isPlaying) "Ⅱ" else "▶",
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onPlayPause)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                color = VioletLight,
                fontSize = 20.sp
            )

            Text(
                "»",
                modifier = Modifier
                    .clickable(onClick = onNext)
                    .padding(horizontal = 5.dp, vertical = 8.dp),
                color = VioletLight,
                fontSize = 25.sp
            )
        }

        Slider(
            value = progress,
            onValueChange = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth().height(14.dp),
            colors = androidx.compose.material3.SliderDefaults.colors(
                disabledThumbColor = VioletLight,
                disabledActiveTrackColor = Violet,
                disabledInactiveTrackColor = Color(0xFF554460)
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatTime(position), color = MutedText, fontSize = 9.sp)
            Text(formatTime(duration), color = MutedText, fontSize = 9.sp)
        }
    }
}

@Composable
private fun FullPlayerScreen(
    song: ImportedSong,
    player: ExoPlayer,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    progress: Float,
    statusMessage: String,
    playbackSpeed: Float,
    speedMenuExpanded: Boolean,
    onSpeedMenuChange: (Boolean) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSeek: (Float) -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRepeat: () -> Unit,
    onShuffle: () -> Unit,
    onMinimize: () -> Unit,
    isFavorite: Boolean,
    onFavorite: () -> Unit
) {
    val context = LocalContext.current

    val artwork by produceState(
        initialValue = SongArtwork(),
        key1 = song.uri
    ) {
        value = withContext(Dispatchers.IO) {
            readSongArtwork(context, song.uri)
        }
    }

    val displayTitle = artwork.metadataTitle
        ?.takeIf { it.isNotBlank() }
        ?: song.title

    var repeatMode by remember { mutableIntStateOf(player.repeatMode) }
    var shuffleEnabled by remember { mutableStateOf(player.shuffleModeEnabled) }

    LaunchedEffect(player) {
        while (true) {
            repeatMode = player.repeatMode
            shuffleEnabled = player.shuffleModeEnabled
            delay(300)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "⌄",
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onMinimize)
                    .padding(10.dp),
                color = VioletLight,
                fontSize = 28.sp
            )

            Spacer(Modifier.weight(1f))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "REPRODUCIENDO",
                    color = VioletLight,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Text("MÚSICA", color = MutedText, fontSize = 9.sp, letterSpacing = 2.sp)
            }

            Spacer(Modifier.weight(1f))

            Text(
                if (isFavorite) "♥" else "♡",
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onFavorite)
                    .padding(10.dp),
                color = if (isFavorite) VioletLight else MutedText,
                fontSize = 25.sp
            )
        }

        Spacer(Modifier.height(18.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true)
                .widthIn(max = 340.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF392052), Color(0xFF21142F), Panel)
                    )
                )
                .border(1.dp, Color(0xFF51336C), RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center
        ) {
            val bitmap = artwork.bitmap

            if (bitmap != null && !bitmap.isRecycled) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Portada de $displayTitle",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("M", color = VioletLight, fontSize = 100.sp, fontWeight = FontWeight.Black)
                    Text(
                        "MÚSICA",
                        color = MainText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 5.sp
                    )
                    Text("TU RITMO", color = VioletLight, fontSize = 10.sp)
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                displayTitle,
                color = MainText,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(5.dp))
            Text(
                artwork.artist?.takeIf { it.isNotBlank() } ?: statusMessage,
                color = MutedText,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(10.dp))

        Slider(
            value = progress,
            onValueChange = onSeek,
            enabled = duration > 0L,
            modifier = Modifier.fillMaxWidth(),
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
            Text(formatTime(position), color = MutedText, fontSize = 11.sp)
            Text(formatTime(duration), color = MutedText, fontSize = 11.sp)
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (shuffleEnabled) "⤨ ACTIVADO" else "⤨ ALEATORIO",
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (shuffleEnabled) PanelLight else Background)
                    .clickable(onClick = onShuffle)
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                color = if (shuffleEnabled) VioletLight else MutedText,
                fontSize = 10.sp
            )

            Text(
                when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> "↻ 1"
                    Player.REPEAT_MODE_ALL -> "↻ TODO"
                    else -> "↻ OFF"
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (repeatMode != Player.REPEAT_MODE_OFF) PanelLight else Background)
                    .clickable(onClick = onRepeat)
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                color = if (repeatMode != Player.REPEAT_MODE_OFF) VioletLight else MutedText,
                fontSize = 10.sp
            )

            Box {
                Text(
                    "${playbackSpeed}×",
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(PanelLight)
                        .clickable { onSpeedMenuChange(true) }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    color = VioletLight,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                DropdownMenu(
                    expanded = speedMenuExpanded,
                    onDismissRequest = { onSpeedMenuChange(false) }
                ) {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { speed ->
                        DropdownMenuItem(
                            text = { Text("${speed}×") },
                            onClick = {
                                onSpeedChange(speed)
                                onSpeedMenuChange(false)
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "⏮",
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onPrevious)
                    .padding(12.dp),
                color = MainText,
                fontSize = 26.sp
            )

            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(VioletLight, Violet)))
                    .clickable(onClick = onPlayPause),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (isPlaying) "Ⅱ" else "▶",
                    color = Background,
                    fontSize = 29.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                "⏭",
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onNext)
                    .padding(12.dp),
                color = MainText,
                fontSize = 26.sp
            )
        }
    }
}

@Composable
private fun BottomNavigationBar(
    selectedTab: MusicTab,
    onSelect: (MusicTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF120E18))
            .border(width = 1.dp, color = Color(0xFF2A2035))
            .padding(horizontal = 4.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        MusicTab.values().forEach { tab ->
            val selected = selectedTab == tab

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) PanelLight else Color.Transparent)
                    .clickable { onSelect(tab) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    tab.symbol,
                    color = if (selected) VioletLight else MutedText,
                    fontSize = 21.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    tab.label,
                    color = if (selected) VioletLight else MutedText,
                    fontSize = 9.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}
