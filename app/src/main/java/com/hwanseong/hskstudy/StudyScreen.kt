package com.hwanseong.hskstudy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

/** 오늘 학습할 단어: 복습(모름·애매) 먼저, 그다음 새 단어를 하루 개수만큼. */
fun todayQueue(data: VocabData, progress: ProgressStore): List<Int> {
    val review = data.words.filter {
        val s = progress.statusOf(it.id)
        s == WordStatus.UNKNOWN || s == WordStatus.UNSURE
    }.map { it.id }
    val fresh = data.words.filter { progress.statusOf(it.id) == WordStatus.NEW }.take(DAILY_NEW_WORDS).map { it.id }
    return review + fresh
}

@Composable
fun StudyScreen(data: VocabData, progress: ProgressStore, customQueue: List<Int>? = null, onClose: () -> Unit) {
    val context = LocalContext.current
    val byId = remember(data) { data.words.associateBy { it.id } }
    // 세션 동안 고정되는 학습 순서. "모름"으로 답한 단어는 끝에 한 번 더 붙는다.
    val queue = remember { mutableStateListOf<Int>().apply { addAll(customQueue ?: todayQueue(data, progress)) } }
    var pos by remember { mutableIntStateOf(0) }
    var flipped by remember { mutableStateOf(false) }
    val answered = remember { mutableStateListOf<Pair<Int, WordStatus>>() }

    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        // 상단: 닫기, 진행 막대, 개수
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Close, "학습 닫기", tint = Ink) }
            Box(Modifier.weight(1f)) {
                ProgressBar(if (queue.isEmpty()) 1f else pos.toFloat() / queue.size, height = 6.dp)
            }
            Text("${minOf(pos + 1, queue.size)} / ${queue.size}", fontSize = 13.sp, color = Secondary)
        }

        when {
            queue.isEmpty() -> EmptyToday(onClose)
            pos >= queue.size -> SessionDone(answered, onClose)
            else -> {
                val word = byId.getValue(queue[pos])
                val examples = data.examplesOf(word.id)
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Surface)
                        .border(1.dp, Border, RoundedCornerShape(20.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { flipped = !flipped }
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        val tag = if (flipped && word.posKo.isNotEmpty()) "${word.level}급 · ${word.posKo}" else "${word.level}급"
                        Text(
                            tag, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = AccentDark,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(AccentTint).padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                        val fav = progress.isFavorite(word.id)
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).clickable { progress.toggleFavorite(word.id) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Star, if (fav) "즐겨찾기 해제" else "즐겨찾기", tint = if (fav) Warning else Muted) }
                    }

                    // 한자: 누르면 발음 (카드 뒤집기와 별개)
                    Text(
                        word.hanzi,
                        fontSize = 88.sp,
                        fontWeight = FontWeight.Medium,
                        color = Ink,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = if (flipped) 0.dp else 72.dp)
                            .clickable { AudioPlayer.play(context, word.audioZh) },
                    )

                    if (!flipped) {
                        Text(
                            "한자를 누르면 발음을 들을 수 있어요\n카드를 누르면 뜻이 보여요",
                            fontSize = 13.sp, color = Secondary, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                        )
                    } else {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(word.pinyin, fontSize = 22.sp, color = Accent)
                                SpeakerButton(word.audioZh, 40.dp, "${word.hanzi} 발음 듣기")
                            }
                            Row(
                                Modifier.padding(top = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(word.meaning, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Ink, textAlign = TextAlign.Center)
                                SpeakerButton(word.audioKo, 36.dp, "뜻 듣기")
                            }
                        }
                        examples.forEachIndexed { n, s ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Ground)
                                    .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("예문 ${n + 1}", fontSize = 11.sp, color = Secondary)
                                    Text(s.zh, fontSize = 19.sp, color = Ink)
                                    Text(s.pinyin, fontSize = 13.sp, color = Secondary)
                                    Text(s.ko, fontSize = 14.sp, color = Ink)
                                }
                                SpeakerButton(s.audio, 40.dp, "예문 ${n + 1} 듣기")
                            }
                        }
                    }
                }

                // 하단: 앞면은 "뜻 보기", 뒷면은 모름·애매·앎
                Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp)) {
                    if (!flipped) {
                        Box56Button("뜻 보기") { flipped = true }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AnswerButton("모름", "이번에 다시", Color(0xFF8C1D18), Color(0xFFFBE5E3), Modifier.weight(1f)) {
                                progress.setStatus(word.id, WordStatus.UNKNOWN)
                                answered.add(word.id to WordStatus.UNKNOWN)
                                if (queue.count { it == word.id } < 2) queue.add(word.id)
                                pos += 1; flipped = false
                            }
                            AnswerButton("애매", "복습에 추가", Color(0xFF7A4F00), Color(0xFFFBF0D9), Modifier.weight(1f)) {
                                progress.setStatus(word.id, WordStatus.UNSURE)
                                answered.add(word.id to WordStatus.UNSURE)
                                pos += 1; flipped = false
                            }
                            AnswerButton("앎", "진도에 반영", Color(0xFF145C39), Color(0xFFE3F2EA), Modifier.weight(1f)) {
                                progress.setStatus(word.id, WordStatus.KNOWN)
                                answered.add(word.id to WordStatus.KNOWN)
                                pos += 1; flipped = false
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnswerButton(label: String, sub: String, fg: Color, bg: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .height(64.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = fg)
        Text(sub, fontSize = 12.sp, color = fg)
    }
}

@Composable
private fun EmptyToday(onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("오늘 학습할 단어가 없어요", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("복습할 단어도, 새 단어도 남아 있지 않아요.", fontSize = 14.sp, color = Secondary)
        Spacer(Modifier.height(8.dp))
        Box56Button("홈으로", onClose)
    }
}

@Composable
private fun SessionDone(answered: List<Pair<Int, WordStatus>>, onClose: () -> Unit) {
    // 같은 단어를 두 번 본 경우 마지막 답만 센다
    val last = answered.associate { it }
    val known = last.values.count { it == WordStatus.KNOWN }
    val unsure = last.values.count { it == WordStatus.UNSURE }
    val unknown = last.values.count { it == WordStatus.UNKNOWN }
    Column(
        Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("오늘 학습 완료", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text("단어 ${last.size}개를 학습했어요.", fontSize = 15.sp, color = Secondary)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DoneMetric("앎", known, Color(0xFF145C39), Color(0xFFE3F2EA), Modifier.weight(1f))
            DoneMetric("애매", unsure, Color(0xFF7A4F00), Color(0xFFFBF0D9), Modifier.weight(1f))
            DoneMetric("모름", unknown, Color(0xFF8C1D18), Color(0xFFFBE5E3), Modifier.weight(1f))
        }
        Text("애매·모름 단어는 다음 학습 때 복습으로 먼저 나와요.", fontSize = 13.sp, color = Secondary)
        Spacer(Modifier.height(8.dp))
        Box56Button("홈으로", onClose)
    }
}

@Composable
private fun DoneMetric(label: String, value: Int, fg: Color, bg: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(bg).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, fontSize = 13.sp, color = fg)
        Text("$value", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}
