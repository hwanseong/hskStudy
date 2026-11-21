package com.hwanseong.hskstudy

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray

private val Ground = Color(0xFFF6F4F0)
private val Ink = Color(0xFF1C1B19)
private val Secondary = Color(0xFF5E5A54)
private val Accent = Color(0xFFB8322A)

data class Word(
    val id: Int, val level: Int, val hanzi: String, val pinyin: String, val meaning: String,
    val audioZh: String, val audioKo: String,
)
data class Sentence(val id: String, val wordId: Int, val zh: String, val pinyin: String, val ko: String, val audio: String)

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
                        .build()
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

/** 0단계 확인용: assets의 JSON을 읽는다. 1단계에서 Room/Repository 구조로 바꾼다. */
fun loadWords(context: Context): List<Word> {
    val json = context.assets.open("data/words.json").bufferedReader().use { it.readText() }
    val arr = JSONArray(json)
    return List(arr.length()) { i ->
        val o = arr.getJSONObject(i)
        Word(
            o.getInt("id"), o.getInt("level"), o.getString("hanzi"), o.getString("pinyin"), o.getString("meaningKo"),
            o.getString("audioZh"), o.getString("audioKo"),
        )
    }
}

fun loadSentences(context: Context): List<Sentence> {
    val json = context.assets.open("data/sentences.json").bufferedReader().use { it.readText() }
    val arr = JSONArray(json)
    return List(arr.length()) { i ->
        val o = arr.getJSONObject(i)
        Sentence(o.getString("id"), o.getInt("wordId"), o.getString("zh"), o.getString("pinyin"), o.getString("ko"), o.getString("audio"))
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = AudioManager.STREAM_MUSIC  // 음량 버튼이 미디어 음량을 조절하도록
        val words = loadWords(this)
        val sentences = loadSentences(this)
        setContent {
            MaterialTheme {
                CheckScreen(words, sentences)
            }
        }
    }
}

@Composable
fun CheckScreen(words: List<Word>, sentences: List<Sentence>) {
    val context = LocalContext.current
    var index by remember { mutableIntStateOf(0) }
    val word = words[index]
    val examples = sentences.filter { it.wordId == word.id }
    val counts = words.groupingBy { it.level }.eachCount()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ground)
            .safeDrawingPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("HSK 단어장", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("버전 ${BuildConfig.VERSION_NAME}", fontSize = 12.sp, color = Secondary)
        Text(
            "데이터 확인: 단어 ${words.size}개 (1급 ${counts[1]} · 2급 ${counts[2]} · 3급 ${counts[3]}), 예문 ${sentences.size}개",
            fontSize = 14.sp,
            color = Secondary,
        )
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(20.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("${word.level}급 · ${word.id}번", fontSize = 12.sp, color = Accent)
            Text(
                word.hanzi, fontSize = 72.sp, color = Ink,
                modifier = Modifier.clickable { AudioPlayer.play(context, word.audioZh) },
            )
            Text(word.pinyin, fontSize = 20.sp, color = Accent)
            Text(
                word.meaning, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Ink,
                modifier = Modifier.clickable { AudioPlayer.play(context, word.audioKo) },
            )
            Text("한자·뜻·예문을 누르면 음성이 나와요", fontSize = 11.sp, color = Secondary)
            examples.forEachIndexed { n, s ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .background(Ground, RoundedCornerShape(14.dp))
                        .clickable { AudioPlayer.play(context, s.audio) }
                        .padding(12.dp),
                ) {
                    Text("예문 ${n + 1}", fontSize = 11.sp, color = Secondary)
                    Text(s.zh, fontSize = 18.sp, color = Ink)
                    Text(s.pinyin, fontSize = 13.sp, color = Secondary)
                    Text(s.ko, fontSize = 14.sp, color = Ink)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    index = (index - 1 + words.size) % words.size
                    AudioPlayer.play(context, words[index].audioZh)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Ink),
                modifier = Modifier.weight(1f),
            ) { Text("이전") }
            Button(
                onClick = {
                    index = (index + 1) % words.size
                    AudioPlayer.play(context, words[index].audioZh)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                modifier = Modifier.weight(1f),
            ) { Text("다음") }
        }
    }
}
