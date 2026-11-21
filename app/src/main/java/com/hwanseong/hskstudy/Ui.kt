package com.hwanseong.hskstudy

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ---------- 색 (UI 초기 확정안)
val Ground = Color(0xFFF6F4F0)
val Surface = Color(0xFFFFFFFF)
val Ink = Color(0xFF1C1B19)
val Secondary = Color(0xFF5E5A54)
val Border = Color(0xFFE4E0D8)
val Track = Color(0xFFECE8E1)
val Accent = Color(0xFFB8322A)
val AccentTint = Color(0xFFF8E7E4)
val AccentDark = Color(0xFF8E2620)
val Success = Color(0xFF1F7A4D)
val Warning = Color(0xFFB07A12)
val Danger = Color(0xFFB3261E)
val Muted = Color(0xFFA8A196)

fun statusColor(s: WordStatus): Color = when (s) {
    WordStatus.KNOWN -> Success
    WordStatus.UNSURE -> Warning
    WordStatus.UNKNOWN -> Danger
    WordStatus.NEW -> Muted
}

// ---------- 선 아이콘 (디자인과 같은 24 그리드 경로)
private fun lineIcon(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.forEach {
            addPath(
                pathData = addPathNodes(it),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

object Icons {
    fun line(name: String, vararg paths: String): ImageVector = lineIcon(name, *paths)
    val Home = lineIcon("home", "M3 10l9-7 9 7v10a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z")
    val Book = lineIcon("book", "M4 19V5a2 2 0 0 1 2-2h14v14H6a2 2 0 0 0-2 2zm0 0a2 2 0 0 0 2 2h14")
    val Game = lineIcon(
        "game",
        "M7.5 7h9a5.5 5.5 0 0 1 0 11h-9a5.5 5.5 0 0 1 0-11z",
        "M7 10.5v4M5 12.5h4",
        "M16 11.5h0.01M18 13.5h0.01",
    )
    val Headphones = lineIcon(
        "headphones",
        "M4 16v-4a8 8 0 0 1 16 0v4",
        "M4.5 14h1A1.5 1.5 0 0 1 7 15.5v3A1.5 1.5 0 0 1 5.5 20h-1A1.5 1.5 0 0 1 3 18.5v-3A1.5 1.5 0 0 1 4.5 14z",
        "M18.5 14h1a1.5 1.5 0 0 1 1.5 1.5v3a1.5 1.5 0 0 1-1.5 1.5h-1a1.5 1.5 0 0 1-1.5-1.5v-3a1.5 1.5 0 0 1 1.5-1.5z",
    )
    val Chart = lineIcon("chart", "M5 20V11M11 20V5M17 20v-6M3 20h18")
    val Volume = lineIcon("volume", "M4 9h4l5-4v14l-5-4H4z", "M16.5 9a4 4 0 0 1 0 6M19 6.5a8 8 0 0 1 0 11")
    val Search = lineIcon("search", "M18 11a7 7 0 1 1-14 0a7 7 0 1 1 14 0z", "M20 20l-4-4")
    val Star = lineIcon("star", "M12 3.5l2.6 5.3 5.9.9-4.3 4.1 1 5.8-5.2-2.7-5.2 2.7 1-5.8-4.3-4.1 5.9-.9z")
    val Flame = lineIcon(
        "flame",
        "M12 21c4 0 7-2.8 7-6.8 0-3.3-2.2-5.3-3.6-7.2-.4 1.9-1.5 3-2.6 3.3.3-2.8-.6-5.6-3.3-7.3.2 3-1.7 4.8-3.2 6.6C5.2 11.1 5 12.3 5 14.2 5 18.2 8 21 12 21z",
    )
    val Close = lineIcon("close", "M6 6l12 12M18 6L6 18")
}

// ---------- 공용 부품
@Composable
fun ProgressBar(fraction: Float, height: Dp = 8.dp, color: Color = Accent) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height))
            .background(Track),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height)
                .clip(RoundedCornerShape(height))
                .background(color),
        )
    }
}

@Composable
fun SpeakerButton(path: String, size: Dp = 44.dp, contentDescription: String = "음성 듣기") {
    val context = LocalContext.current
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .border(1.dp, Border, CircleShape)
            .background(Surface)
            .clickable { AudioPlayer.play(context, path) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Volume, contentDescription, tint = Accent, modifier = Modifier.size(20.dp))
    }
}

// ---------- 음성 재생
/** assets의 음성 파일을 미디어 음량으로 재생한다. 새로 재생하면 이전 재생은 멈춘다. 실패하면 이유를 화면에 띄운다. */
object AudioPlayer {
    private var player: MediaPlayer? = null

    fun play(context: Context, path: String) {
        player?.release()
        player = null
        val am = context.getSystemService(AudioManager::class.java)
        if (am.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) {
            Toast.makeText(context, "미디어 음량이 0이에요. 음량 버튼으로 올려 주세요.", Toast.LENGTH_SHORT).show()
        }
        try {
            val fd = context.assets.openFd(path)
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                fd.close()
                setOnCompletionListener { it.release(); if (player === it) player = null }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "재생 실패: $path (${e.javaClass.simpleName}: ${e.message})", Toast.LENGTH_LONG).show()
        }
    }
}
