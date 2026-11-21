package com.hwanseong.hskstudy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

const val DAILY_NEW_WORDS = 10

@Composable
fun HomeScreen(
    data: VocabData,
    progress: ProgressStore,
    onStartStudy: () -> Unit,
    onOpenTab: (Tab) -> Unit,
) {
    val review = data.words.count {
        val s = progress.statusOf(it.id)
        s == WordStatus.UNKNOWN || s == WordStatus.UNSURE
    }
    val newLeft = data.words.count { progress.statusOf(it.id) == WordStatus.NEW }
    val newToday = minOf(DAILY_NEW_WORDS, newLeft)
    val firstNew = data.words.firstOrNull { progress.statusOf(it.id) == WordStatus.NEW }

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
            Text("오늘의 학습", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
            Row(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(AccentTint)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Icons.Flame, null, tint = AccentDark, modifier = Modifier.size(16.dp))
                Text("연속 ${progress.streak}일", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = AccentDark)
            }
        }
        Text("HSK 3.0 · 3급 목표", fontSize = 14.sp, color = Secondary, modifier = Modifier.padding(top = 0.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("복습할 단어", "$review", "모름·애매 표시", Modifier.weight(1f))
            MetricCard(
                "새 단어", "$newToday",
                firstNew?.let { "${it.level}급 ${it.id}번부터" } ?: "모두 학습함",
                Modifier.weight(1f),
            )
        }

        Box56Button("학습 시작", onStartStudy)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShortcutCard(Icons.Game, "게임 학습", "랜덤 퀴즈", Modifier.weight(1f)) { onOpenTab(Tab.GAME) }
            ShortcutCard(Icons.Headphones, "듣기 모드", "연속 재생", Modifier.weight(1f)) { onOpenTab(Tab.LISTEN) }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Surface)
                .border(1.dp, Border, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("급수별 진도", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
            for (level in 1..3) {
                val total = data.countByLevel(level)
                val known = data.words.count { it.level == level && progress.statusOf(it.id) == WordStatus.KNOWN }
                val pct = if (total == 0) 0 else known * 100 / total
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${level}급", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Ink)
                        Text("$known / $total · $pct%", fontSize = 14.sp, color = Secondary)
                    }
                    ProgressBar(known.toFloat() / total.coerceAtLeast(1))
                }
            }
            Text("단어 목록에서 \"앎\"으로 표시한 단어가 진도에 들어가요", fontSize = 12.sp, color = Secondary)
        }

        Text(
            "버전 ${BuildConfig.VERSION_NAME}",
            fontSize = 11.sp,
            color = Muted,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp),
        )
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun MetricCard(label: String, value: String, sub: String, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .border(1.dp, Border, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(label, fontSize = 13.sp, color = Secondary)
        Text(value, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text(sub, fontSize = 12.sp, color = Secondary)
    }
}

@Composable
fun Box56Button(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Accent)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = Surface)
    }
}

@Composable
private fun ShortcutCard(icon: ImageVector, title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .height(60.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Surface)
            .border(1.dp, Border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = Accent, modifier = Modifier.size(22.dp))
        Column {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink)
            Text(sub, fontSize = 12.sp, color = Secondary)
        }
    }
}
