package com.violetmusic.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Background = Color(0xFF0B0910)
private val Panel = Color(0xFF17131F)
private val PanelLight = Color(0xFF211A2D)
private val Violet = Color(0xFFAA72FF)
private val VioletLight = Color(0xFFD4B5FF)
private val MainText = Color(0xFFF7F2FF)
private val MutedText = Color(0xFFA69BAF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = android.graphics.Color.rgb(11, 9, 16)
        window.navigationBarColor = android.graphics.Color.rgb(11, 9, 16)
        window.decorView.systemUiVisibility = 0

        setContent {
            VioletMusicTheme {
                VioletMusicApp()
            }
        }
    }
}

@Composable
private fun VioletMusicTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Violet,
            onPrimary = Background,
            background = Background,
            surface = Panel,
            onSurface = MainText,
            secondary = VioletLight
        ),
        content = content
    )
}

private data class DemoSong(
    val title: String,
    val artist: String,
    val cover: Int,
    val duration: String
)

private val demoSongs = listOf(
    DemoSong("Midnight Dreams", "Luna Rivers", 0, "3:42"),
    DemoSong("Purple Skies", "Nova Echo", 1, "3:18"),
    DemoSong("Afterglow", "The Velvet Room", 2, "4:06"),
    DemoSong("Slow Motion", "Mila Sol", 3, "2:58")
)

@Composable
private fun VioletMusicApp() {
    var selectedTab by remember { mutableIntStateOf(0) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = WindowInsets.statusBars
                        .asPaddingValues().calculateTopPadding()
                )
                .padding(
                    bottom = WindowInsets.navigationBars
                        .asPaddingValues().calculateBottomPadding()
                )
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(16.dp))
                Header()
                Spacer(Modifier.height(26.dp))
                SearchVisual()
                Spacer(Modifier.height(28.dp))
                WelcomeCard()
                Spacer(Modifier.height(28.dp))

                SectionHeading(
                    title = "Tu espacio musical",
                    subtitle = "Todo a tu ritmo"
                )

                Spacer(Modifier.height(15.dp))
                CategoryRow()
                Spacer(Modifier.height(29.dp))

                SectionHeading(
                    title = "Escucha a tu manera",
                    subtitle = "Tu biblioteca aparecerá aquí"
                )

                Spacer(Modifier.height(13.dp))
                EmptyLibraryCard()
                Spacer(Modifier.height(28.dp))

                SectionHeading(
                    title = "Descubre el diseño",
                    subtitle = "Canciones de ejemplo"
                )

                Spacer(Modifier.height(5.dp))

                Text(
                    text = "Contenido ilustrativo · no son archivos reales",
                    color = MutedText,
                    fontSize = 11.sp
                )

                Spacer(Modifier.height(12.dp))

                demoSongs.forEachIndexed { index, song ->
                    SongRow(song)

                    if (index != demoSongs.lastIndex) {
                        Spacer(Modifier.height(8.dp))
                    }
                }

                Spacer(Modifier.height(20.dp))
            }

            MiniPlayer()

            BottomNavigation(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }
    }
}

@Composable
private fun Header() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(47.dp)
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
                text = "V",
                color = Color.White,
                fontSize = 29.sp,
                fontWeight = FontWeight.Black
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "VIOLET",
                color = MainText,
                fontSize = 21.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp
            )

            Text(
                text = "M U S I C",
                color = VioletLight,
                fontSize = 9.sp,
                letterSpacing = 3.sp
            )
        }

        Box(
            modifier = Modifier
                .size(43.dp)
                .clip(CircleShape)
                .background(Panel)
                .border(1.dp, PanelLight, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("♫", color = VioletLight, fontSize = 23.sp)
        }
    }
}

@Composable
private fun SearchVisual() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(53.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(Panel)
            .border(
                1.dp,
                Color(0xFF30253E),
                RoundedCornerShape(17.dp)
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("⌕", color = VioletLight, fontSize = 27.sp)

        Spacer(Modifier.width(12.dp))

        Text(
            text = "Buscar en tu biblioteca",
            color = MutedText,
            fontSize = 14.sp
        )

        Spacer(Modifier.weight(1f))

        Text("♪", color = MutedText, fontSize = 19.sp)
    }
}

@Composable
private fun WelcomeCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(25.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF392052),
                        Color(0xFF21142F),
                        Color(0xFF17101F)
                    )
                )
            )
            .border(
                1.dp,
                Color(0xFF51336C),
                RoundedCornerShape(25.dp)
            )
            .padding(21.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFF5B3975))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        "TU MÚSICA, TU UNIVERSO",
                        color = VioletLight,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.7.sp
                    )
                }

                Spacer(Modifier.weight(1f))

                Text("✦", color = VioletLight, fontSize = 25.sp)
            }

            Spacer(Modifier.height(19.dp))

            Text(
                text = "Siente cada\nnota.",
                color = MainText,
                fontSize = 34.sp,
                lineHeight = 39.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(Modifier.height(9.dp))

            Text(
                text = "Tu próximo universo musical\ncomienza aquí.",
                color = Color(0xFFD1C2DE),
                fontSize = 13.sp,
                lineHeight = 19.sp
            )

            Spacer(Modifier.height(21.dp))

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFB88AFF))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "♫",
                    color = Color(0xFF1B1026),
                    fontSize = 17.sp
                )

                Spacer(Modifier.width(8.dp))

                Text(
                    text = "Tu biblioteca te espera",
                    color = Color(0xFF1B1026),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(19.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("✧", color = Color(0xFF8056B4), fontSize = 31.sp)
                Spacer(Modifier.width(8.dp))
                Text("♫", color = Color(0xFFB88AFF), fontSize = 37.sp)
                Spacer(Modifier.width(8.dp))
                Text("♪", color = Color(0xFF70469A), fontSize = 25.sp)
            }
        }
    }
}

@Composable
private fun SectionHeading(
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MainText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(3.dp))

            Text(
                text = subtitle,
                color = MutedText,
                fontSize = 11.sp
            )
        }

        Text("✦", color = Violet, fontSize = 20.sp)
    }
}

@Composable
private fun CategoryRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        CategoryCard(
            icon = "♡",
            title = "Favoritas",
            subtitle = "Tus elegidas",
            accent = Color(0xFFD18BFF)
        )

        CategoryCard(
            icon = "◷",
            title = "Recientes",
            subtitle = "Últimas escuchas",
            accent = Color(0xFF9F9AFF)
        )

        CategoryCard(
            icon = "▤",
            title = "Playlists",
            subtitle = "Tus colecciones",
            accent = Color(0xFFFFB3D7)
        )
    }
}

@Composable
private fun CategoryCard(
    icon: String,
    title: String,
    subtitle: String,
    accent: Color
) {
    Column(
        modifier = Modifier
            .width(142.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(Panel)
            .border(1.dp, PanelLight, RoundedCornerShape(19.dp))
            .padding(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(39.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = icon,
                color = accent,
                fontSize = 24.sp
            )
        }

        Spacer(Modifier.height(17.dp))

        Text(
            text = title,
            color = MainText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = subtitle,
            color = MutedText,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EmptyLibraryCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF121019))
            .border(
                1.dp,
                Color(0xFF352741),
                RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 20.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(53.dp)
                .clip(CircleShape)
                .background(Color(0xFF2B1D3B)),
            contentAlignment = Alignment.Center
        ) {
            Text("♫", color = VioletLight, fontSize = 29.sp)
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Un espacio para tu música",
            color = MainText,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "Aquí aparecerán tus canciones cuando\nla biblioteca musical esté disponible.",
            color = MutedText,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun AlbumCover(
    index: Int,
    modifier: Modifier = Modifier
) {
    val gradients = listOf(
        listOf(Color(0xFFB28AFF), Color(0xFF392064)),
        listOf(Color(0xFF77C8D8), Color(0xFF27365F)),
        listOf(Color(0xFFFFA7CA), Color(0xFF673A71)),
        listOf(Color(0xFFFFC17A), Color(0xFF75448D))
    )

    val colors = gradients[index % gradients.size]

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(35.dp)
                .clip(CircleShape)
                .border(
                    1.dp,
                    Color.White.copy(alpha = 0.55f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = listOf("✦", "◉", "✧", "♫")[index % 4],
                color = Color.White,
                fontSize = 20.sp
            )
        }
    }
}

@Composable
private fun SongRow(song: DemoSong) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(17.dp))
            .background(Panel)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumCover(
            index = song.cover,
            modifier = Modifier.size(51.dp)
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = MainText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = song.artist,
                color = MutedText,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(8.dp))

        Text(
            text = song.duration,
            color = MutedText,
            fontSize = 11.sp
        )

        Spacer(Modifier.width(12.dp))

        Text(
            text = "•••",
            color = MutedText,
            fontSize = 17.sp
        )
    }
}

@Composable
private fun MiniPlayer() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(Color(0xFF251A33))
            .border(
                1.dp,
                Color(0xFF4A315D),
                RoundedCornerShape(17.dp)
            )
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumCover(
            index = 0,
            modifier = Modifier.size(42.dp)
        )

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Tu música aparecerá aquí",
                color = MainText,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(3.dp))

            Text(
                text = "Reproductor de muestra",
                color = MutedText,
                fontSize = 10.sp
            )
        }

        Text("Ⅱ", color = MutedText, fontSize = 18.sp)

        Spacer(Modifier.width(14.dp))

        Text("»", color = MutedText, fontSize = 24.sp)
    }

    Spacer(Modifier.height(8.dp))
}

@Composable
private fun BottomNavigation(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    val tabs = listOf(
        Triple("⌂", "Inicio", 0),
        Triple("▤", "Biblioteca", 1),
        Triple("♡", "Favoritas", 2),
        Triple("☷", "Playlists", 3)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Background)
            .padding(
                start = 8.dp,
                end = 8.dp,
                top = 7.dp,
                bottom = 5.dp
            ),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { tab ->
            val selected = selectedTab == tab.third

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(13.dp))
                    .clickable { onTabSelected(tab.third) }
                    .padding(vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = tab.first,
                    color = if (selected) VioletLight else MutedText,
                    fontSize = 22.sp
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    text = tab.second,
                    color = if (selected) VioletLight else MutedText,
                    fontSize = 9.sp,
                    fontWeight = if (selected) {
                        FontWeight.Bold
                    } else {
                        FontWeight.Normal
                    },
                    maxLines = 1
                )

                if (selected) {
                    Spacer(Modifier.height(3.dp))

                    Box(
                        modifier = Modifier
                            .size(width = 15.dp, height = 2.dp)
                            .clip(CircleShape)
                            .background(Violet)
                    )
                }
            }
        }
    }
}
