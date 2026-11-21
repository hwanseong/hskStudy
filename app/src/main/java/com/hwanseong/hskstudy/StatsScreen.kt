package com.hwanseong.hskstudy

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 게임 기록 한 줄: 시각,모드,급수,푼 문제,맞힌 문제,점수,최고 콤보,초 */
data class GameRecord(val at: Long, val mode: String, val level: Int, val answered: Int, val correct: Int, val score: Int) {
    val rate: Int get() = if (answered == 0) 0 else correct * 100 / answered
}

fun loadGameRecords(c: Context): List<GameRecord> =
    (c.getSharedPreferences("history", Context.MODE_PRIVATE).getString("games", "") ?: "")
        .lines().mapNotNull { line ->
            val f = line.split(",")
            if (f.size < 6) null else runCatching {
                GameRecord(f[0].toLong(), f[1], f[2].toInt(), f[3].toInt(), f[4].toInt(), f[5].toInt())
            }.getOrNull()
        }

private val KnownC = Color(0xFF1F7A4D)
private val UnsureC = Color(0xFFD9A12B)
private val UnknownC = Color(0xFFC2453B)
private val NewC = Color(0xFFE4E0D8)

@Composable
fun StatsScreen(data: VocabData, progress: ProgressStore) {
    val context = LocalContext.current
    val games = loadGameRecords(context)
    val wrongCount = WrongNote.keys(context).size
    val studied = data.words.count { progress.statusOf(it.id) != WordStatus.NEW }
    val known = data.words.count { progress.statusOf(it.id) == WordStatus.KNOWN }
    val today = LocalDate.now()
    val days = (13 downTo 0).map { today.minusDays(it.toLong()) }
    val daily = days.map { progress.answeredOn(it) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("통계", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.padding(top = 20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("연속 학습", "${progress.streak}일", Modifier.weight(1f))
            Stat("학습한 단어", "$studied / ${data.words.size}", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("아는 단어 (앎)", "$known", Modifier.weight(1f))
            Stat("오답 노트", "$wrongCount", Modifier.weight(1f))
        }

        // 진단: 급수별 상태 분포
        Card("급수별 진단") {
            Text("3급 목표까지 아는 단어 $known / ${data.words.size}", fontSize = 13.sp, color = Secondary)
            for (level in 1..3) {
                val ws = data.words.filter { it.level == level }
                val k = ws.count { progress.statusOf(it.id) == WordStatus.KNOWN }
                val u = ws.count { progress.statusOf(it.id) == WordStatus.UNSURE }
                val x = ws.count { progress.statusOf(it.id) == WordStatus.UNKNOWN }
                val n = ws.size - k - u - x
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${level}급", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Ink)
                        Text("앎 ${k * 100 / ws.size.coerceAtLeast(1)}%", fontSize = 14.sp, color = Secondary)
                    }
                    StackedBar(listOf(k to KnownC, u to UnsureC, x to UnknownC, n to NewC))
                    Text("앎 $k · 애매 $u · 모름 $x · 새 단어 $n", fontSize = 12.sp, color = Secondary)
                }
            }
            Legend()
        }

        // 최근 14일 학습량
        Card("최근 14일 학습량") {
            val total = daily.sum()
            Text(
                if (total == 0) "아직 기록이 없어요. 학습 카드에서 답하면 날짜별로 쌓여요." else "14일 동안 ${total}번 답했어요",
                fontSize = 13.sp, color = Secondary,
            )
            BarChart(daily, highlightLast = true)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val f = DateTimeFormatter.ofPattern("M/d")
                Text(days.first().format(f), fontSize = 11.sp, color = Secondary)
                Text("오늘", fontSize = 11.sp, color = Secondary)
            }
        }

        // 게임 정답률 추이
        Card("게임 정답률") {
            if (games.isEmpty()) {
                Text("아직 게임 기록이 없어요.", fontSize = 13.sp, color = Secondary)
            } else {
                val recent = games.takeLast(20)
                val avg = games.sumOf { it.correct } * 100 / games.sumOf { it.answered }.coerceAtLeast(1)
                Text("게임 ${games.size}판 · 평균 정답률 $avg% · 최고 점수 ${"%,d".format(games.maxOf { it.score })}", fontSize = 13.sp, color = Secondary)
                RateChart(recent.map { it.rate })
                Text("최근 ${recent.size}판, 왼쪽이 오래된 판 · 점선은 80%", fontSize = 11.sp, color = Secondary)
            }
        }

        if (games.isNotEmpty()) {
            Card("최근 게임") {
                val f = DateTimeFormatter.ofPattern("M/d HH:mm")
                games.takeLast(5).reversed().forEach { g ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            Instant.ofEpochMilli(g.at).atZone(ZoneId.systemDefault()).format(f),
                            fontSize = 13.sp, color = Secondary, modifier = Modifier.weight(1.1f),
                        )
                        Text(
                            "${if (g.mode == "word") "단어" else "예문"} · ${if (g.level == 0) "전체" else "${g.level}급"}",
                            fontSize = 13.sp, color = Ink, modifier = Modifier.weight(1f),
                        )
                        Text("${g.correct}/${g.answered}", fontSize = 13.sp, color = Ink, modifier = Modifier.weight(0.6f), textAlign = TextAlign.End)
                        Text("${g.rate}%", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Ink, modifier = Modifier.weight(0.6f), textAlign = TextAlign.End)
                    }
                }
            }
        }
        Box(Modifier.height(16.dp))
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Surface).border(1.dp, Border, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(label, fontSize = 13.sp, color = Secondary)
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
    }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface)
            .border(1.dp, Border, RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
        content()
    }
}

@Composable
private fun StackedBar(parts: List<Pair<Int, Color>>) {
    val total = parts.sumOf { it.first }.coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp))) {
        var x = 0f
        parts.forEach { (n, c) ->
            val w = size.width * n / total
            if (w > 0f) drawRect(c, topLeft = Offset(x, 0f), size = Size(w, size.height))
            x += w
        }
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("앎" to KnownC, "애매" to UnsureC, "모름" to UnknownC, "새 단어" to NewC).forEach { (l, c) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(c))
                Text(l, fontSize = 12.sp, color = Secondary)
            }
        }
    }
}

@Composable
private fun BarChart(values: List<Int>, highlightLast: Boolean) {
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(96.dp)) {
        val gap = 6.dp.toPx()
        val w = (size.width - gap * (values.size - 1)) / values.size
        values.forEachIndexed { i, v ->
            val h = if (v == 0) 2.dp.toPx() else size.height * v / max
            val color = if (highlightLast && i == values.lastIndex) Accent else if (v == 0) Track else Color(0xFFD9A79F)
            drawRoundRect(
                color, topLeft = Offset(i * (w + gap), size.height - h), size = Size(w, h),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
        }
    }
}

@Composable
private fun RateChart(rates: List<Int>) {
    Canvas(Modifier.fillMaxWidth().height(110.dp)) {
        val y80 = size.height * (1 - 0.8f)
        drawLine(
            Secondary.copy(alpha = 0.5f), Offset(0f, y80), Offset(size.width, y80), strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
        )
        drawLine(Track, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        if (rates.isEmpty()) return@Canvas
        val step = if (rates.size == 1) 0f else size.width / (rates.size - 1)
        val pts = rates.mapIndexed { i, r -> Offset(if (rates.size == 1) size.width / 2 else i * step, size.height * (1 - r / 100f)) }
        for (i in 1 until pts.size) drawLine(Accent, pts[i - 1], pts[i], strokeWidth = 2.dp.toPx())
        pts.forEach { drawCircle(Accent, radius = 3.5.dp.toPx(), center = it) }
    }
}
