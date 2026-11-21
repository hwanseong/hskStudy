package com.hwanseong.hskstudy

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
    /** 알림을 눌러 들어왔을 때 열 탭 (예: 듣기 모드 알림 → 듣기 탭) */
    private val requestedTab = mutableStateOf<Tab?>(null)

    private fun readTab(intent: Intent?) {
        intent?.getStringExtra("tab")?.let { name -> requestedTab.value = Tab.entries.firstOrNull { it.name == name } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readTab(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 앱 바탕이 밝은색이라 기기가 다크 모드여도 상태바·내비게이션 아이콘은 어둡게 둔다
        val light = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = light, navigationBarStyle = light)
        super.onCreate(savedInstanceState)
        volumeControlStream = AudioManager.STREAM_MUSIC  // 음량 버튼이 미디어 음량을 조절하도록
        readTab(intent)
        // 듣기 모드 알림(잠금 화면 조작)을 보여 주려면 Android 13부터 알림 권한이 필요하다
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        val data = VocabData(this)
        val progress = ProgressStore(this)
        setContent {
            MaterialTheme {
                App(data, progress, requestedTab)
            }
        }
    }
}

@Composable
fun App(data: VocabData, progress: ProgressStore, requestedTab: MutableState<Tab?>) {
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var studying by rememberSaveable { mutableStateOf(false) }
    var studyQueue by remember { mutableStateOf<List<Int>?>(null) }   // 오답 복습처럼 정해진 단어로 학습할 때
    var game by remember { mutableStateOf<GameSettings?>(null) }      // 진행 중인 게임
    var gameKey by remember { mutableIntStateOf(0) }                  // 다시 하기 때 새 게임으로
    var result by remember { mutableStateOf<GameResult?>(null) }
    LaunchedEffect(requestedTab.value) {
        requestedTab.value?.let { tab = it; studying = false; requestedTab.value = null }
    }
    val fullScreen = studying || game != null || result != null

    Column(
        Modifier
            .fillMaxSize()
            .background(Ground)
            .statusBarsPadding(),
    ) {
        Box(Modifier.weight(1f)) {
            when {
                studying -> {
                    val close = { studying = false; studyQueue = null }
                    BackHandler(onBack = close)
                    StudyScreen(data, progress, studyQueue, close)
                }
                game != null -> {
                    BackHandler { game = null }
                    key(gameKey) {
                        GameScreen(data, game!!, onExit = { game = null }) { r -> result = r; game = null }
                    }
                }
                result != null -> {
                    val r = result!!
                    BackHandler { result = null }
                    ResultScreen(
                        r,
                        onReview = { ids -> result = null; studyQueue = ids; studying = true },
                        onAgain = { result = null; gameKey += 1; game = r.settings },
                        onClose = { result = null },
                    )
                }
                else -> {
                    when (tab) {
                        Tab.HOME -> HomeScreen(data, progress, onStartStudy = { studyQueue = null; studying = true }, onOpenTab = { tab = it })
                        Tab.WORDS -> WordsScreen(data, progress)
                        Tab.GAME -> GameSetupScreen(data) { s -> gameKey += 1; game = s }
                        Tab.LISTEN -> ListenScreen(data)
                        Tab.STATS -> StatsScreen(data, progress)
                    }
                    if (tab != Tab.HOME) BackHandler { tab = Tab.HOME }
                }
            }
        }
        if (!fullScreen) BottomBar(tab) { tab = it }
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
