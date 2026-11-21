package com.hwanseong.hskstudy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val levelTabs = listOf(1 to "1급", 2 to "2급", 3 to "3급", 0 to "전체")
private enum class Filter(val label: String) { ALL("전체"), UNKNOWN("모름"), UNSURE("애매"), FAVORITE("즐겨찾기") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordsScreen(data: VocabData, progress: ProgressStore) {
    var level by rememberSaveable { mutableStateOf(1) }
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var openId by rememberSaveable { mutableStateOf<Int?>(null) }

    val q = query.trim()
    val qPlain = plainPinyin(q)
    val shown = data.words.filter { w ->
        (level == 0 || w.level == level) &&
            when (filter) {
                Filter.ALL -> true
                Filter.UNKNOWN -> progress.statusOf(w.id) == WordStatus.UNKNOWN
                Filter.UNSURE -> progress.statusOf(w.id) == WordStatus.UNSURE
                Filter.FAVORITE -> progress.isFavorite(w.id)
            } &&
            (q.isEmpty() || w.hanzi.contains(q) || w.meaning.contains(q) ||
                (qPlain.isNotEmpty() && w.pinyinPlain.contains(qPlain)))
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("단어", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text("${shown.size}개", fontSize = 13.sp, color = Secondary)
        }
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("爱, ai, 사랑하다", color = Muted) },
                leadingIcon = { Icon(Icons.Search, "단어 검색", tint = Secondary, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        Icon(
                            Icons.Close, "검색어 지우기", tint = Secondary,
                            modifier = Modifier.size(20.dp).clickable { query = "" },
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Surface,
                    unfocusedContainerColor = Surface,
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Border,
                    cursorColor = Accent,
                ),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                levelTabs.forEach { (lv, label) ->
                    Chip(label, selected = level == lv, modifier = Modifier.weight(1f)) { level = lv }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Filter.entries.forEach { f ->
                    Chip(f.label, selected = filter == f, small = true) { filter = f }
                }
            }
        }

        if (shown.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (filter == Filter.ALL) "검색 결과가 없어요" else "아직 \"${filter.label}\"로 표시한 단어가 없어요",
                    fontSize = 14.sp, color = Secondary,
                )
            }
        } else {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp),
            ) {
                items(shown, key = { it.id }) { w ->
                    WordRow(w, progress.statusOf(w.id), progress.isFavorite(w.id)) { openId = w.id }
                    HorizontalDivider(color = Border)
                }
            }
        }
    }

    val open = openId?.let { id -> data.words.firstOrNull { it.id == id } }
    if (open != null) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { openId = null },
            sheetState = sheet,
            containerColor = Surface,
        ) {
            WordDetail(open, data.examplesOf(open.id), progress)
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, modifier: Modifier = Modifier, small: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(if (selected) Ink else Surface)
            .border(1.dp, if (selected) Ink else Border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = if (small) 12.dp else 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = if (small) 13.sp else 14.sp, color = if (selected) Surface else Ink)
    }
}

@Composable
private fun WordRow(w: Word, status: WordStatus, favorite: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Ground)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor(status)))
        Text(w.hanzi, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = Ink, modifier = Modifier.widthIn(min = 56.dp))
        Column(Modifier.weight(1f)) {
            Text(w.pinyin, fontSize = 13.sp, color = Secondary)
            Text(w.meaning, fontSize = 14.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (favorite) Icon(Icons.Star, "즐겨찾기", tint = Warning, modifier = Modifier.size(18.dp))
        SpeakerButton(w.audioZh, contentDescription = "${w.hanzi} 발음 듣기")
    }
}

@Composable
private fun WordDetail(w: Word, examples: List<Sentence>, progress: ProgressStore) {
    val context = LocalContext.current
    val status = progress.statusOf(w.id)
    val favorite = progress.isFavorite(w.id)
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${w.level}급 · ${w.id}번", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = AccentDark,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(AccentTint).padding(horizontal = 8.dp, vertical = 3.dp),
            )
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable { progress.toggleFavorite(w.id) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Star, if (favorite) "즐겨찾기 해제" else "즐겨찾기", tint = if (favorite) Warning else Muted)
            }
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                w.hanzi, fontSize = 72.sp, color = Ink,
                modifier = Modifier.clickable { AudioPlayer.play(context, w.audioZh) },
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(w.pinyin, fontSize = 22.sp, color = Accent)
                SpeakerButton(w.audioZh, 40.dp, "${w.hanzi} 발음 듣기")
            }
            Row(
                Modifier.padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(w.meaning, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Ink)
                SpeakerButton(w.audioKo, 36.dp, "뜻 듣기")
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
        Text("이 단어를 얼마나 아나요?", fontSize = 13.sp, color = Secondary)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusButton("모름", Color(0xFF8C1D18), Color(0xFFFBE5E3), status == WordStatus.UNKNOWN, Modifier.weight(1f)) {
                progress.setStatus(w.id, WordStatus.UNKNOWN)
            }
            StatusButton("애매", Color(0xFF7A4F00), Color(0xFFFBF0D9), status == WordStatus.UNSURE, Modifier.weight(1f)) {
                progress.setStatus(w.id, WordStatus.UNSURE)
            }
            StatusButton("앎", Color(0xFF145C39), Color(0xFFE3F2EA), status == WordStatus.KNOWN, Modifier.weight(1f)) {
                progress.setStatus(w.id, WordStatus.KNOWN)
            }
        }
    }
}

@Composable
private fun StatusButton(label: String, fg: Color, bg: Color, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .height(56.dp)
            .clip(shape)
            .background(bg)
            .border(if (selected) 2.dp else 0.dp, if (selected) fg else bg, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (selected) "✓ $label" else label, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}
