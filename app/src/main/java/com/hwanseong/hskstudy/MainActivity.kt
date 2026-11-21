package com.hwanseong.hskstudy

import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("홈", Icons.Home),
    WORDS("단어", Icons.Book),
    GAME("게임", Icons.Game),
    LISTEN("듣기", Icons.Headphones),
    STATS("통계", Icons.Chart),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 앱 바탕이 밝은색이라 기기가 다크 모드여도 상태바·내비게이션 아이콘은 어둡게 둔다
        val light = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = light, navigationBarStyle = light)
        super.onCreate(savedInstanceState)
        volumeControlStream = AudioManager.STREAM_MUSIC  // 음량 버튼이 미디어 음량을 조절하도록
        val data = VocabData(this)
        val progress = ProgressStore(this)
        setContent {
            MaterialTheme {
                App(data, progress)
            }
        }
    }
}

@Composable
fun App(data: VocabData, progress: ProgressStore) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var studying by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ground)
            .statusBarsPadding(),
    ) {
        Box(Modifier.weight(1f)) {
            if (studying) {
                BackHandler { studying = false }
                StudyPreviewScreen(data) { studying = false }
            } else {
                when (tab) {
                    Tab.HOME -> HomeScreen(data, progress, onStartStudy = { studying = true }, onOpenTab = { tab = it })
                    Tab.WORDS -> WordsScreen(data, progress)
                    Tab.GAME -> ComingSoon("게임 학습", "단어·예문 퀴즈는 2단계에서 만들어요.")
                    Tab.LISTEN -> ComingSoon("듣기 모드", "연속 재생 듣기 모드는 2단계에서 만들어요.")
                    Tab.STATS -> ComingSoon("통계", "통계 화면은 3단계에서 만들어요.")
                }
                if (tab != Tab.HOME) BackHandler { tab = Tab.HOME }
            }
        }
        if (!studying) BottomBar(tab) { tab = it }
    }
}

@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    Column(Modifier.background(Surface).navigationBarsPadding()) {
        HorizontalDivider(color = Border)
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { t ->
                val color = if (t == current) Accent else Secondary
                Column(
                    Modifier
                        .widthIn(min = 56.dp)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Tab) { onSelect(t) }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(t.icon, null, tint = color, modifier = Modifier.size(22.dp))
                    Text(
                        t.label, fontSize = 12.sp, color = color,
                        fontWeight = if (t == current) FontWeight.Medium else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun ComingSoon(title: String, message: String) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("준비 중", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Accent, modifier = Modifier.padding(top = 24.dp))
        Text(message, fontSize = 14.sp, color = Secondary)
    }
}

/** 학습 카드 화면이 나오기 전까지 쓰는 미리보기. 1번 단어부터 넘겨 본다. */
@Composable
private fun StudyPreviewScreen(data: VocabData, onClose: () -> Unit) {
    val context = LocalContext.current
    val words = data.words
    var index by remember { mutableIntStateOf(0) }
    val word = words[index]
    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(Icons.Close, "닫기", tint = Ink)
            }
            Text("학습 카드 미리보기 · ${index + 1} / ${words.size}", fontSize = 13.sp, color = Secondary)
        }
        Text("학습 카드 화면은 다음 작업에서 완성돼요.", fontSize = 12.sp, color = Secondary)
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(Surface)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("${word.level}급 · ${word.id}번", fontSize = 12.sp, color = Accent)
            Text(word.hanzi, fontSize = 72.sp, color = Ink, modifier = Modifier.clickable { AudioPlayer.play(context, word.audioZh) })
            Text(word.pinyin, fontSize = 20.sp, color = Accent)
            Text(
                word.meaning, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Ink,
                modifier = Modifier.clickable { AudioPlayer.play(context, word.audioKo) },
            )
            data.examplesOf(word.id).forEachIndexed { n, s ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Ground)
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
            listOf("이전" to -1, "다음" to 1).forEach { (label, step) ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (step > 0) Accent else Surface)
                        .clickable {
                            index = (index + step + words.size) % words.size
                            AudioPlayer.play(context, words[index].audioZh)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = if (step > 0) Surface else Ink)
                }
            }
        }
    }
}
