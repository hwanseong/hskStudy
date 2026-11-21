"""엑셀(단어·뜻·예문)과 HSK 3.0 대강(품사)을 읽어 앱용 JSON을 만든다.

사용법: python3 tools/build_data.py
입력:  data/hsk30_l1-3_vocab_examples.xlsx, data/syllabus2025_l1-3.tsv
출력:  app/src/main/assets/data/words.json, sentences.json
"""
import csv
import json
import re
from pathlib import Path

from openpyxl import load_workbook

ROOT = Path(__file__).resolve().parent.parent
XLSX = ROOT / "data" / "hsk30_l1-3_vocab_examples.xlsx"
TSV = ROOT / "data" / "syllabus2025_l1-3.tsv"
OUT = ROOT / "app" / "src" / "main" / "assets" / "data"

POS_MAP = {
    "名": "noun", "动": "verb", "形": "adj", "副": "adv", "代": "pron",
    "数": "num", "量": "measure", "数量": "num-measure", "介": "prep",
    "连": "conj", "助": "particle", "叹": "interj", "前缀": "prefix",
    "后缀": "suffix",
}


def parse_pos(raw: str) -> list[str]:
    """'名、（动、量）' → ['noun', 'verb', 'measure'] (괄호 = 부차 품사, 뒤에 둔다)."""
    main, extra = [], []
    for part in re.split(r"、", raw or ""):
        secondary = "（" in part or "）" in part
        key = re.sub(r"[（）()]", "", part).strip()
        if key in POS_MAP:
            (extra if secondary else main).append(POS_MAP[key])
    return main + extra


def tts_text(meaning: str) -> str:
    """뜻을 음성으로 읽기 좋게 다듬는다: 괄호 설명과 ~ 기호를 뺀다."""
    t = re.sub(r"\([^)]*\)", "", meaning)
    t = t.replace("~", "").replace("…", "")
    t = re.sub(r"\s*,\s*", ", ", t)
    t = re.sub(r"\s*;\s*", ", ", t)
    t = re.sub(r"\s+", " ", t).strip(" ,")
    return t


def find_blank(sentence: str, word: str):
    """예문에서 단어 위치 [시작, 끝)을 찾는다. 이합사처럼 떨어져 있으면 None."""
    i = sentence.find(word)
    return [i, i + len(word)] if i >= 0 else None


def main():
    syl = {int(r["index"]): r for r in csv.DictReader(open(TSV, encoding="utf-8"), delimiter="\t")}
    ws = load_workbook(XLSX, read_only=True)["전체(1~3급)"]
    rows = list(ws.iter_rows(min_row=2, values_only=True))
    assert len(rows) == 1000, len(rows)

    words, sentences = [], []
    for r in rows:
        idx, level, hanzi, pinyin, meaning = r[0], int(str(r[1]).rstrip("급")), r[2], r[3], r[4]
        s = syl[idx]
        assert s["word"] == hanzi, (idx, s["word"], hanzi)
        wid = f"w{idx:04d}"
        words.append({
            "id": idx,
            "level": level,
            "hanzi": hanzi,
            "pinyin": pinyin,
            "pos": parse_pos(s["pos"]) or ["phrase"],
            "meaningKo": meaning,
            "meaningTts": tts_text(meaning),
            "audioZh": f"audio/zh/{wid}.ogg",
            "audioKo": f"audio/ko/{wid}.ogg",
        })
        for n, (zh, py, ko) in enumerate([r[5:8], r[8:11]], 1):
            sid = f"{wid}_s{n}"
            sentences.append({
                "id": sid,
                "wordId": idx,
                "level": level,
                "zh": zh,
                "pinyin": py,
                "ko": ko,
                "blank": find_blank(zh, hanzi),
                "audio": f"audio/zh/{sid}.ogg",
            })

    OUT.mkdir(parents=True, exist_ok=True)
    for name, data in (("words.json", words), ("sentences.json", sentences)):
        (OUT / name).write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")
    no_blank = sum(1 for s in sentences if s["blank"] is None)
    print(f"words {len(words)}, sentences {len(sentences)}, 빈칸 불가 예문 {no_blank}")


if __name__ == "__main__":
    main()
