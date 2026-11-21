package com.hwanseong.hskstudy

import android.content.Context
import org.json.JSONArray
import java.text.Normalizer

data class Word(
    val id: Int,
    val level: Int,
    val hanzi: String,
    val pinyin: String,
    val meaning: String,
    val audioZh: String,
    val audioKo: String,
    val pos: List<String>,
) {
    /** 품사 한국어 표기 (예: ["noun","verb"] → "명사·동사") */
    val posKo: String = pos.mapNotNull { POS_KO[it] }.joinToString("·")

    /** 검색용: 성조·공백·기호를 뺀 병음 (예: "bāozi" → "baozi") */
    val pinyinPlain: String = plainPinyin(pinyin)
}

data class Sentence(
    val id: String,
    val wordId: Int,
    val zh: String,
    val pinyin: String,
    val ko: String,
    val audio: String,
    /** 예문 안 단어 위치 [시작, 끝). 이합사처럼 떨어져 있으면 null */
    val blank: IntRange?,
)

val POS_KO = mapOf(
    "noun" to "명사", "verb" to "동사", "adj" to "형용사", "adv" to "부사", "pron" to "대명사",
    "num" to "수사", "measure" to "양사", "num-measure" to "수량사", "prep" to "개사", "conj" to "접속사",
    "particle" to "조사", "interj" to "감탄사", "prefix" to "접두사", "suffix" to "접미사", "phrase" to "구",
)

fun plainPinyin(s: String): String =
    Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}"), "")
        .replace(Regex("[^a-z]"), "")

/** assets/data의 단어·예문을 한 번 읽어 둔다. */
class VocabData(context: Context) {
    val words: List<Word>
    val sentences: List<Sentence>
    private val sentencesByWord: Map<Int, List<Sentence>>

    init {
        val w = JSONArray(context.assets.open("data/words.json").bufferedReader().use { it.readText() })
        words = List(w.length()) { i ->
            val o = w.getJSONObject(i)
            Word(
                o.getInt("id"), o.getInt("level"), o.getString("hanzi"), o.getString("pinyin"),
                o.getString("meaningKo"), o.getString("audioZh"), o.getString("audioKo"),
                o.getJSONArray("pos").let { a -> List(a.length()) { j -> a.getString(j) } },
            )
        }
        val s = JSONArray(context.assets.open("data/sentences.json").bufferedReader().use { it.readText() })
        sentences = List(s.length()) { i ->
            val o = s.getJSONObject(i)
            Sentence(
                o.getString("id"), o.getInt("wordId"), o.getString("zh"), o.getString("pinyin"),
                o.getString("ko"), o.getString("audio"),
                if (o.isNull("blank")) null else o.getJSONArray("blank").let { b -> b.getInt(0) until b.getInt(1) },
            )
        }
        sentencesByWord = sentences.groupBy { it.wordId }
    }

    fun examplesOf(wordId: Int): List<Sentence> = sentencesByWord[wordId].orEmpty()

    fun countByLevel(level: Int): Int = words.count { it.level == level }
}
