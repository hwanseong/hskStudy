package com.hwanseong.hskstudy

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import kotlinx.coroutines.delay

private val PlayIcon = Icons.line("play", "M7 4.5v15l12.5-7.5z")
private val PauseIcon = Icons.line("pause", "M7 5h3.5v14H7zM13.5 5H17v14h-3.5z")
private val PrevIcon = Icons.line("prev", "M18.5 5L9 12l9.5 7zM5.5 5v14")
private val NextIcon = Icons.line("next", "M5.5 5L15 12l-9.5 7zM18.5 5v14")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ListenScreen(data: VocabData) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(ListenSettings.load(context)) }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var playing by remember { mutableStateOf(false) }
    var extras by remember { mutableStateOf<Bundle?>(null) }
    var hasItems by remember { mutableStateOf(false) }
    var sleepUntil by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    DisposableEffect(Unit) {
        val token = SessionToken(context, ComponentName(context, ListenService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                playing = player.isPlaying
                extras = player.currentMediaItem?.mediaMetadata?.extras
                hasItems = player.mediaItemCount > 0
            }
        }
        future.addListener({
            val c = future.get()
            c.addListener(listener)
            controller = c
            playing = c.isPlaying
            extras = c.currentMediaItem?.mediaMetadata?.extras
            hasItems = c.mediaItemCount > 0
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            controller?.removeListener(listener)
            MediaController.releaseFuture(future)
            controller = null
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            sleepUntil = context.getSharedPreferences("listen", Context.MODE_PRIVATE).getLong("sleepUntil", 0L)
            delay(15_000)
        }
    }

    val wordCount = remember(settings) { listenWords(context, data, settings).size }

    fun update(new: ListenSettings, rebuild: Boolean = true) {
        settings = new
        new.save(context)
        val c = controller ?: return
        if (rebuild && hasItems) {
            c.sendCustomCommand(SessionCommand(CMD_START, Bundle.EMPTY), Bundle().apply { putBoolean("play", c.isPlaying) })
        } else {
            c.playbackParameters = PlaybackParameters(new.speed)
        }
    }

    fun togglePlay() {
        val c = controller ?: return
        when {
            c.isPlaying -> c.pause()
            !hasItems || c.playbackState == Player.STATE_ENDED || c.playbackState == Player.STATE_IDLE ->
                c.sendCustomCommand(SessionCommand(CMD_START, Bundle.EMPTY), Bundle().apply { putBoolean("play", true) })
            else -> c.play()
        }
    }

    fun jumpWord(dir: Int) {
        val c = controller ?: return
        val curPos = c.currentMediaItem?.mediaMetadata?.extras?.getInt("wordPos") ?: return
        val target = curPos + dir
        for (i in 0 until c.mediaItemCount) {
            val e = c.getMediaItemAt(i).mediaMetadata.extras ?: continue
            if (e.getInt("wordPos") == target && e.getString("kind") == "word") {
                c.seekTo(i, 0L)
                if (!c.isPlaying) c.play()
                return
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("듣기 모드", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
            val e = extras
            Text(
                if (e != null && hasItems) "${e.getInt("wordPos")} / ${e.getInt("wordTotal")}" else "단어 ${wordCount}개",
                fontSize = 13.sp, color = Secondary,
            )
        }

        // 지금 재생 중
        NowPlayingCard(extras.takeIf { hasItems }, data, settings, wordCount)

        // 재생 조작
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            RoundButton(PrevIcon, "이전 단어", 52, false) { jumpWord(-1) }
            Box(Modifier.size(28.dp))
            RoundButton(if (playing) PauseIcon else PlayIcon, if (playing) "일시 정지" else "재생", 72, true) { togglePlay() }
            Box(Modifier.size(28.dp))
            RoundButton(NextIcon, "다음 단어", 52, false) { jumpWord(1) }
        }
        Text(
            "정지하기 전까지 다음 단어로 계속 넘어가요. 화면을 꺼도 재생돼요.",
            fontSize = 12.sp, color = Secondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )

        // 설정
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Surface)
                .border(1.dp, Border, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("재생 설정", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink)

            SettingLabel("재생 순서")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val seq = buildList {
                    add("단어"); if (settings.koMeaning) add("한국어 뜻"); add("예문 1"); add("예문 2")
                }
                seq.forEachIndexed { i, t ->
                    Text(t, fontSize = 13.sp, color = Ink, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Ground).padding(horizontal = 10.dp, vertical = 6.dp))
                    if (i < seq.size - 1) Text("→", fontSize = 13.sp, color = Secondary, modifier = Modifier.padding(vertical = 6.dp))
                }
            }

            SettingLabel("범위")
            OptionRow(listOf(1 to "1급", 2 to "2급", 3 to "3급", 0 to "전체"), settings.level) { update(settings.copy(level = it)) }
            OptionRow(listOf("all" to "전체", "weak" to "모름·애매", "fav" to "즐겨찾기"), settings.target) { update(settings.copy(target = it)) }
            if (wordCount == 0) Text("이 범위에는 단어가 없어요.", fontSize = 12.sp, color = Danger)

            SettingLabel("속도")
            OptionRow(listOf(0.8f to "0.8x", 1.0f to "1.0x", 1.2f to "1.2x"), settings.speed) { update(settings.copy(speed = it), rebuild = false) }
            SettingLabel("반복")
            OptionRow(listOf(1 to "1회", 2 to "2회", 3 to "3회"), settings.repeat) { update(settings.copy(repeat = it)) }
            SettingLabel("항목 사이 간격")
            OptionRow(listOf(500 to "0.5초", 1000 to "1초", 2000 to "2초"), settings.gapMs) { update(settings.copy(gapMs = it)) }
            SettingLabel("범위를 다 들으면")
            OptionRow(listOf("stop" to "멈추기", "restart" to "처음부터", "reshuffle" to "섞어서 다시"), settings.endAction) {
                update(settings.copy(endAction = it))
            }

            ToggleRow("한국어 뜻 음성", settings.koMeaning) { update(settings.copy(koMeaning = it)) }
            ToggleRow("순서 섞기", settings.shuffle) { update(settings.copy(shuffle = it)) }

            SettingLabel("수면 타이머")
            val remainMin = if (sleepUntil > now) ((sleepUntil - now + 59_999) / 60_000).toInt() else 0
            OptionRow(listOf(0 to "끔", 15 to "15분", 30 to "30분", 60 to "60분"), if (remainMin > 0) -1 else 0) { m ->
                val c = controller
                if (c == null || !hasItems) return@OptionRow
                c.sendCustomCommand(SessionCommand(CMD_SLEEP, Bundle.EMPTY), Bundle().apply { putInt("minutes", m) })
                sleepUntil = if (m > 0) System.currentTimeMillis() + m * 60_000L else 0L
                now = System.currentTimeMillis()
            }
            Text(
                when {
                    remainMin > 0 -> "약 ${remainMin}분 뒤에 멈춰요"
                    !hasItems -> "재생을 시작한 뒤에 설정할 수 있어요"
                    else -> "꺼져 있어요"
                },
                fontSize = 12.sp, color = Secondary,
            )
        }
        Box(Modifier.heightIn(min = 16.dp))
    }
}

@Composable
private fun NowPlayingCard(e: Bundle?, data: VocabData, settings: ListenSettings, wordCount: Int) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .border(1.dp, Border, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (e == null) {
            Text("재생을 누르면 시작해요", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Ink)
            val scope = (if (settings.level == 0) "전체" else "${settings.level}급") + when (settings.target) {
                "weak" -> " · 모름·애매"
                "fav" -> " · 즐겨찾기"
                else -> ""
            }
            Text("$scope 단어 ${wordCount}개", fontSize = 14.sp, color = Secondary)
            Text("마지막으로 듣던 단어가 있으면 거기서 이어서 들려줘요", fontSize = 12.sp, color = Secondary)
            return@Column
        }
        val kind = e.getString("kind") ?: "word"
        val word = data.words.firstOrNull { it.id == e.getInt("wordId") }
        val label = when (kind) {
            "word" -> "단어"
            "meaning" -> "한국어 뜻"
            "s1" -> "예문 1"
            "s2" -> "예문 2"
            else -> "잠시 쉬는 중"
        }
        Text("지금 재생 중 · $label", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = AccentDark)
        Text(word?.hanzi ?: "", fontSize = 64.sp, fontWeight = FontWeight.Medium, color = Ink)
        Text("${e.getString("pinyin")} · ${e.getString("meaning")}", fontSize = 16.sp, color = Ink, textAlign = TextAlign.Center)
        val zh = e.getString("zh")
        if (zh != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AccentTint)
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(zh, fontSize = 18.sp, color = Ink, textAlign = TextAlign.Center)
                Text("${e.getString("zhPinyin")} · ${e.getString("ko")}", fontSize = 13.sp, color = Secondary, textAlign = TextAlign.Center)
            }
        }
        Box(Modifier.padding(top = 12.dp)) {
            ProgressBar(e.getInt("wordPos").toFloat() / e.getInt("wordTotal").coerceAtLeast(1), height = 4.dp)
        }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, description: String, size: Int, primary: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(if (primary) Accent else Surface)
            .border(if (primary) 0.dp else 1.dp, Border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = if (primary) Surface else Ink, modifier = Modifier.size((size * 0.4f).dp))
    }
}

@Composable
private fun SettingLabel(text: String) {
    Text(text, fontSize = 13.sp, color = Secondary, modifier = Modifier.padding(top = 4.dp))
}

@Composable
fun <T> OptionRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            val shape = RoundedCornerShape(10.dp)
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(shape)
                    .background(if (on) Ink else Surface)
                    .border(1.dp, if (on) Ink else Border, shape)
                    .clickable { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontSize = 13.sp, color = if (on) Surface else Ink)
            }
        }
    }
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 15.sp, color = Ink)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Surface),
        )
    }
}
