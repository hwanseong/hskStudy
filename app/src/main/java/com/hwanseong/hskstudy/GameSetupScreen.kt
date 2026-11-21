package com.hwanseong.hskstudy

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 게임 설정. 퀴즈 화면(다음 작업)도 같은 값("game")을 읽는다. */
data class GameSettings(
    val mode: String = "word",       // word | sentence
    val level: Int = 1,              // 0 = 전체
    val count: Int = 20,             // 0 = 무제한
    val wordTypes: Set<String> = WORD_TYPES.map { it.first }.toSet(),
    val sentenceTypes: Set<String> = SENTENCE_TYPES.map { it.first }.toSet(),
    val wrongOnly: Boolean = false,
) {
    fun save(context: Context) {
        context.getSharedPreferences("game", Context.MODE_PRIVATE).edit()
            .putString("mode", mode).putInt("level", level).putInt("count", count)
            .putStringSet("wordTypes", wordTypes).putStringSet("sentenceTypes", sentenceTypes)
            .putBoolean("wrongOnly", wrongOnly).apply()
    }

    companion object {
        val WORD_TYPES = listOf("meaning" to "뜻 고르기", "hanzi" to "한자 고르기", "listen" to "듣고 고르기", "pinyin" to "병음 고르기")
        val SENTENCE_TYPES = listOf("blank" to "빈칸 채우기", "listenKo" to "듣고 해석 고르기", "koToZh" to "해석 보고 문장 고르기", "order" to "어순 맞추기")

        fun load(context: Context): GameSettings {
            val p = context.getSharedPreferences("game", Context.MODE_PRIVATE)
            val d = GameSettings()
            return GameSettings(
                p.getString("mode", d.mode) ?: d.mode,
                p.getInt("level", d.level),
                p.getInt("count", d.count),
                p.getStringSet("wordTypes", d.wordTypes)?.toSet() ?: d.wordTypes,
                p.getStringSet("sentenceTypes", d.sentenceTypes)?.toSet() ?: d.sentenceTypes,
                p.getBoolean("wrongOnly", d.wrongOnly),
            )
        }
    }
}

/** 오답 노트 개수 (게임 화면에서 채운다). 키: "w_<단어 id>" 또는 "s_<예문 id>" */
fun wrongNoteCount(context: Context, mode: String): Int =
    context.getSharedPreferences("wrong", Context.MODE_PRIVATE).all.keys.count { it.startsWith(if (mode == "word") "w_" else "s_") }

private val ShuffleIcon = Icons.line(
    "shuffle",
    "M3 7h3.5c2 0 3.2 1 4.3 2.6l2.4 4.8c1.1 1.6 2.3 2.6 4.3 2.6H21M3 17h3.5c1.4 0 2.4-.5 3.2-1.4M14.3 8.4C15.1 7.5 16.1 7 17.5 7H21M18 4l3 3-3 3M18 14l3 3-3 3",
)
private val CheckIcon = Icons.line("check", "M5 12.5l4.5 4.5L19 7.5")
private val NoteIcon = Icons.line("note", "M6 3h9l4 4v14H6z", "M9 12h7M9 16h5")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameSetupScreen(data: VocabData) {
    val context = LocalContext.current
    var s by remember { mutableStateOf(GameSettings.load(context)) }
    fun update(n: GameSettings) { s = n; n.save(context) }

    val wordCount = data.words.count { s.level == 0 || it.level == s.level }
    val pool = if (s.mode == "word") wordCount else wordCount * 2
    val wrong = wrongNoteCount(context, s.mode)
    val types = if (s.mode == "word") GameSettings.WORD_TYPES else GameSettings.SENTENCE_TYPES
    val chosen = if (s.mode == "word") s.wordTypes else s.sentenceTypes

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("게임 학습", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.padding(top = 20.dp))

        Section("모드") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ModeCard(Icons.Book, "단어 게임", "${data.words.size.fmt()}문제", s.mode == "word", Modifier.weight(1f)) {
                    update(s.copy(mode = "word"))
                }
                ModeCard(NoteIcon, "예문 게임", "${data.sentences.size.fmt()}문제", s.mode == "sentence", Modifier.weight(1f)) {
                    update(s.copy(mode = "sentence"))
                }
            }
        }

        Section("범위") {
            OptionRow(listOf(1 to "1급", 2 to "2급", 3 to "3급", 0 to "전체"), s.level) { update(s.copy(level = it)) }
            Text(
                "${if (s.level == 0) "전체" else "${s.level}급"}에서 ${pool.fmt()}문제 중 랜덤으로 나와요",
                fontSize = 12.sp, color = Secondary, modifier = Modifier.padding(top = 6.dp),
            )
        }

        Section("문제 수") {
            OptionRow(listOf(10 to "10문제", 20 to "20문제", 0 to "무제한"), s.count) { update(s.copy(count = it)) }
            if (s.count == 0) Text("하트 3개를 다 잃을 때까지 계속돼요", fontSize = 12.sp, color = Secondary, modifier = Modifier.padding(top = 6.dp))
        }

        Section("문제 유형 · 섞어서 출제") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                types.forEach { (key, label) ->
                    val on = key in chosen
                    TypeChip(label, on) {
                        val next = if (on) chosen - key else chosen + key
                        if (next.isEmpty()) {
                            Toast.makeText(context, "유형을 하나 이상 골라 주세요", Toast.LENGTH_SHORT).show()
                        } else {
                            update(if (s.mode == "word") s.copy(wordTypes = next) else s.copy(sentenceTypes = next))
                        }
                    }
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Surface)
                .border(1.dp, Border, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            ToggleRow("오답 노트에서만 출제", s.wrongOnly && wrong > 0) {
                if (it && wrong == 0) {
                    Toast.makeText(context, "오답 노트가 아직 비어 있어요", Toast.LENGTH_SHORT).show()
                } else {
                    update(s.copy(wrongOnly = it))
                }
            }
            Text("오답 노트 ${wrong}개", fontSize = 12.sp, color = Secondary, modifier = Modifier.padding(bottom = 10.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(ShuffleIcon, null, tint = Secondary, modifier = Modifier.size(16.dp))
            Text("문제 순서, 보기, 정답 위치는 매번 랜덤이에요", fontSize = 12.sp, color = Secondary)
        }

        Box(Modifier.padding(bottom = 20.dp)) {
            Box56Button("게임 시작") {
                Toast.makeText(context, "설정을 저장했어요. 퀴즈 화면은 다음 작업에서 연결돼요.", Toast.LENGTH_LONG).show()
            }
        }
    }
}

private fun Int.fmt(): String = "%,d".format(this)

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
}

@Composable
private fun ModeCard(icon: ImageVector, title: String, sub: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) AccentTint else Surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) Accent else Border, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = if (selected) AccentDark else Ink, modifier = Modifier.size(22.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (selected) AccentDark else Ink)
        Text(sub, fontSize = 12.sp, color = if (selected) AccentDark else Secondary)
    }
}

@Composable
private fun TypeChip(label: String, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(Surface)
            .border(1.dp, if (on) Ink else Border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (on) Icon(CheckIcon, null, tint = Ink, modifier = Modifier.size(16.dp))
        Text(label, fontSize = 14.sp, color = if (on) Ink else Secondary)
    }
}
