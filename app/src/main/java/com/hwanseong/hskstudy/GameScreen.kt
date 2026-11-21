package com.hwanseong.hskstudy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Heart = Icons.line(
    "heart",
    "M12 20s-7.5-4.6-7.5-10.2A4.3 4.3 0 0 1 12 7.2a4.3 4.3 0 0 1 7.5 2.6C19.5 15.4 12 20 12 20z",
)
private val OkGreen = Color(0xFF1F7A4D)
private val OkBg = Color(0xFFE3F2EA)
private val OkInk = Color(0xFF145C39)
private val BadRed = Color(0xFFB3261E)
private val BadBg = Color(0xFFFBE5E3)
private val BadInk = Color(0xFF8C1D18)

private val TYPE_PROMPT = mapOf(
    "meaning" to "뜻을 고르세요",
    "hanzi" to "한자를 고르세요",
    "listen" to "듣고 한자를 고르세요",
    "pinyin" to "병음을 고르세요",
    "blank" to "빈칸에 알맞은 단어를 고르세요",
    "listenKo" to "듣고 알맞은 해석을 고르세요",
    "koToZh" to "해석에 맞는 문장을 고르세요",
    "order" to "단어를 순서대로 눌러 문장을 만드세요",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameScreen(data: VocabData, settings: GameSettings, onExit: () -> Unit, onFinish: (GameResult) -> Unit) {
    val context = LocalContext.current
    val maker = remember { QuizMaker(data, settings, WrongNote.keys(context)) }
    val startedAt = remember { System.currentTimeMillis() }
    var q by remember { mutableStateOf(maker.next()) }
    var number by remember { mutableIntStateOf(1) }
    var picked by remember { mutableStateOf<Int?>(null) }          // 보기형 선택
    val orderPicked = remember { mutableStateListOf<Int>() }       // 어순: 고른 블록 인덱스
    var orderChecked by remember { mutableStateOf<Boolean?>(null) } // 어순 채점 결과
    var hearts by remember { mutableIntStateOf(3) }
    var combo by remember { mutableIntStateOf(0) }
    var maxCombo by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var gained by remember { mutableIntStateOf(0) }
    var correctCount by remember { mutableIntStateOf(0) }
    val wrongs = remember { mutableStateListOf<Question>() }

    val answered = picked != null || orderChecked != null
    val isCorrect = when {
        picked != null -> picked == q.correct
        orderChecked != null -> orderChecked == true
        else -> false
    }

    // 듣기 유형은 문제가 나올 때 음성을 들려준다 (문제 자체가 소리이므로)
    LaunchedEffect(q) {
        if (q.type == "listen" || q.type == "listenKo") AudioPlayer.play(context, q.audio)
    }

    fun grade(ok: Boolean) {
        if (ok) {
            combo += 1
            maxCombo = maxOf(maxCombo, combo)
            gained = 100 + 10 * minOf(combo - 1, 10)
            score += gained
            correctCount += 1
            WrongNote.remove(context, q.key)
        } else {
            combo = 0
            gained = 0
            hearts -= 1
            wrongs += q
            WrongNote.add(context, q.key)
        }
    }

    fun finish() {
        val r = GameResult(
            settings, number, correctCount, score, maxCombo, hearts,
            ((System.currentTimeMillis() - startedAt) / 1000).toInt(), wrongs.toList(),
        )
        r.saveHistory(context)
        onFinish(r)
    }

    fun nextQuestion() {
        if (hearts <= 0 || (maker.total > 0 && number >= maker.total)) {
            finish()
            return
        }
        number += 1
        q = maker.next()
        picked = null
        orderPicked.clear()
        orderChecked = null
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        // 상단: 그만하기, 하트, 진행, 콤보
        Column(Modifier.padding(start = 8.dp, end = 20.dp, top = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onExit), contentAlignment = Alignment.Center) {
                    Icon(Icons.Close, "게임 그만하기", tint = Ink)
                }
                Row(Modifier.weight(1f).padding(start = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(3) { i -> Icon(Heart, null, tint = if (i < hearts) BadRed else Track, modifier = Modifier.size(20.dp)) }
                }
                Text(
                    if (maker.total > 0) "$number / ${maker.total}" else "${number}번째",
                    fontSize = 14.sp, color = Secondary,
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    if (combo >= 2) "콤보 x$combo" else "${score}점",
                    fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    color = if (combo >= 2) AccentDark else Secondary,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (combo >= 2) AccentTint else Ground)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
            Box(Modifier.padding(start = 12.dp, top = 8.dp)) {
                ProgressBar(if (maker.total > 0) number.toFloat() / maker.total else (hearts / 3f), height = 6.dp)
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val levelTag = "${q.word.level}급"
            Text("${TYPE_PROMPT[q.type]} · $levelTag", fontSize = 14.sp, color = Secondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

            // 문제 본문
            when (q.type) {
                "meaning", "pinyin" -> {
                    Text(q.word.hanzi, fontSize = 76.sp, fontWeight = FontWeight.Medium, color = Ink, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().clickable { AudioPlayer.play(context, q.word.audioZh) })
                }
                "hanzi" -> {
                    Text(q.word.meaning, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = Ink, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp))
                }
                "listen", "listenKo" -> {
                    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                        SpeakerButton(q.audio, 84.dp, "다시 듣기")
                    }
                    Text("스피커를 누르면 다시 들려줘요", fontSize = 12.sp, color = Secondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
                "blank" -> {
                    val sen = q.sentence!!
                    val r = sen.blank!!
                    val shown = sen.zh.substring(0, r.first) + (if (answered) q.word.hanzi else "＿＿") + sen.zh.substring(r.last + 1)
                    PromptCard(shown, sen.ko)
                }
                "koToZh" -> PromptCard(q.sentence!!.ko, null, big = false)
                "order" -> {
                    PromptCard(q.sentence!!.ko, null, big = false)
                    // 고른 순서
                    val built = orderPicked.joinToString("") { q.blocks[it] }
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (orderChecked == null) Surface else if (isCorrect) OkBg else BadBg)
                            .border(1.dp, Border, RoundedCornerShape(12.dp)).padding(12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(if (built.isEmpty()) "아래 블록을 순서대로 눌러 주세요" else built + if (orderPicked.size == q.blocks.size) q.tail else "",
                            fontSize = if (built.isEmpty()) 13.sp else 20.sp, color = if (built.isEmpty()) Muted else Ink)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        q.blocks.forEachIndexed { i, b ->
                            val used = i in orderPicked
                            Box(
                                Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp))
                                    .background(if (used) Ground else Surface)
                                    .border(1.dp, if (used) Ground else Border, RoundedCornerShape(10.dp))
                                    .clickable(enabled = orderChecked == null) {
                                        if (used) orderPicked.remove(i) else orderPicked.add(i)
                                        if (orderPicked.size == q.blocks.size) {
                                            val ok = orderPicked.joinToString("") { q.blocks[it] } == q.answer.joinToString("")
                                            orderChecked = ok
                                            grade(ok)
                                        }
                                    }
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(b, fontSize = 20.sp, color = if (used) Muted else Ink) }
                        }
                    }
                    if (orderChecked == null && orderPicked.isNotEmpty()) {
                        Text("고른 블록을 다시 누르면 빠져요", fontSize = 12.sp, color = Secondary)
                    }
                }
            }

            // 보기
            if (q.type != "order") {
                val grid = q.type == "blank" || q.type == "hanzi" || q.type == "listen"
                val optSize = if (q.type in listOf("hanzi", "listen", "blank")) 24.sp else 16.sp
                if (grid) {
                    q.options.chunked(2).forEachIndexed { row, pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEachIndexed { col, text ->
                                val i = row * 2 + col
                                OptionButton(text, optionState(i, picked, q.correct), optSize, Modifier.weight(1f)) {
                                    if (picked == null) { picked = i; grade(i == q.correct) }
                                }
                            }
                        }
                    }
                } else {
                    q.options.forEachIndexed { i, text ->
                        OptionButton(text, optionState(i, picked, q.correct), optSize, Modifier.fillMaxWidth()) {
                            if (picked == null) { picked = i; grade(i == q.correct) }
                        }
                    }
                }
            }

            // 채점 결과
            if (answered) Feedback(q, isCorrect, gained)
        }

        if (answered) {
            Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp)) {
                val last = hearts <= 0 || (maker.total > 0 && number >= maker.total)
                DarkButton(if (last) "결과 보기" else "다음 문제") { nextQuestion() }
            }
        }
    }
}

private enum class OptState { IDLE, CORRECT, WRONG, DIM }

private fun optionState(i: Int, picked: Int?, correct: Int): OptState = when {
    picked == null -> OptState.IDLE
    i == correct -> OptState.CORRECT
    i == picked -> OptState.WRONG
    else -> OptState.DIM
}

@Composable
private fun OptionButton(text: String, state: OptState, size: androidx.compose.ui.unit.TextUnit, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val (bg, border, fg) = when (state) {
        OptState.CORRECT -> Triple(OkBg, OkGreen, OkInk)
        OptState.WRONG -> Triple(BadBg, BadRed, BadInk)
        OptState.DIM -> Triple(Surface, Border, Muted)
        OptState.IDLE -> Triple(Surface, Border, Ink)
    }
    Row(
        modifier
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(bg)
            .border(if (state == OptState.CORRECT || state == OptState.WRONG) 2.dp else 1.dp, border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = size, color = fg, modifier = Modifier.weight(1f))
        when (state) {
            OptState.CORRECT -> Text("✓ 정답", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = OkInk)
            OptState.WRONG -> Text("✕", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = BadInk)
            else -> {}
        }
    }
}

@Composable
private fun PromptCard(main: String, sub: String?, big: Boolean = true) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface)
            .border(1.dp, Border, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(main, fontSize = if (big) 24.sp else 19.sp, color = Ink, textAlign = TextAlign.Center, lineHeight = if (big) 36.sp else 28.sp)
        if (sub != null) Text(sub, fontSize = 15.sp, color = Secondary, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Feedback(q: Question, ok: Boolean, gained: Int) {
    val sen = q.sentence
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (ok) OkBg else BadBg)
            .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            val head = if (ok) "정답! +${gained}점" else "아쉬워요 · 정답은 ${if (q.type == "order") sen!!.zh else q.options.getOrNull(q.correct) ?: ""}"
            Text(head, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (ok) OkInk else BadInk)
            Text("${q.word.hanzi} ${q.word.pinyin} · ${q.word.meaning}", fontSize = 14.sp, color = Ink)
            if (sen != null) {
                Text(sen.zh, fontSize = 16.sp, color = Ink)
                Text("${sen.pinyin} · ${sen.ko}", fontSize = 13.sp, color = Secondary)
            }
            if (!ok) Text("오답 노트에 추가됐어요", fontSize = 12.sp, color = BadInk)
        }
        SpeakerButton(q.audio, 40.dp, if (sen != null) "예문 듣기" else "발음 듣기")
    }
}

@Composable
private fun DarkButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(14.dp)).background(Ink).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Surface) }
}

// ---------------------------------------------------------------- 결과

@Composable
fun ResultScreen(r: GameResult, onReview: (List<Int>) -> Unit, onAgain: () -> Unit, onClose: () -> Unit) {
    val rate = if (r.answered == 0) 0 else r.correct * 100 / r.answered
    val modeLabel = if (r.settings.mode == "word") "단어 게임" else "예문 게임"
    val levelLabel = if (r.settings.level == 0) "전체" else "${r.settings.level}급"
    val countLabel = if (r.settings.count == 0) "무제한" else "${r.settings.count}문제"
    val wrongWordIds = r.wrong.map { it.word.id }.distinct()

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 8.dp)) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                Icon(Icons.Close, "닫기", tint = Ink)
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$modeLabel · $levelLabel · $countLabel", fontSize = 14.sp, color = Secondary)
                Text(if (r.hearts <= 0) "하트를 모두 썼어요" else "라운드 완료", fontSize = 15.sp, color = Secondary, modifier = Modifier.padding(top = 4.dp))
                Text("${r.correct} / ${r.answered}", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text("정답률 $rate%", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = OkInk)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("점수", "%,d".format(r.score), Modifier.weight(1f))
                Metric("최고 콤보", "${r.maxCombo}", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("남은 하트", "${r.hearts.coerceAtLeast(0)}", Modifier.weight(1f))
                Metric("걸린 시간", "${r.seconds / 60}분 ${r.seconds % 60}초", Modifier.weight(1f))
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface)
                    .border(1.dp, Border, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Text(
                    if (r.wrong.isEmpty()) "틀린 문제가 없어요" else "틀린 문제 ${r.wrong.size}개 · 오답 노트에 저장됨",
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink,
                )
                r.wrong.forEachIndexed { i, q ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Border))
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(q.word.hanzi, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = Ink)
                        Column(Modifier.weight(1f)) {
                            Text(q.word.pinyin, fontSize = 13.sp, color = Secondary)
                            Text(q.sentence?.zh ?: q.word.meaning, fontSize = 14.sp, color = Ink)
                        }
                        Text(
                            if (q.sentence != null) "예문" else "단어", fontSize = 12.sp, color = Secondary,
                            modifier = Modifier.border(1.dp, Border, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(14.dp)).background(Surface)
                    .border(1.dp, Border, RoundedCornerShape(14.dp))
                    .clickable(enabled = wrongWordIds.isNotEmpty()) { onReview(wrongWordIds) },
                contentAlignment = Alignment.Center,
            ) { Text("오답 복습", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = if (wrongWordIds.isEmpty()) Muted else Ink) }
            Box(
                Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(14.dp)).background(Accent).clickable(onClick = onAgain),
                contentAlignment = Alignment.Center,
            ) { Text("다시 하기", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Surface) }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(Surface).border(1.dp, Border, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(label, fontSize = 13.sp, color = Secondary)
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
    }
}
