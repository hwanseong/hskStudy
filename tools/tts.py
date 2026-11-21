"""Google Cloud Text-to-Speech로 음성 파일을 만든다.

사용법:
  GOOGLE_TTS_API_KEY=... python3 tools/tts.py sample   # 여성 음성 후보별 샘플(mp3) → dist/samples/
  GOOGLE_TTS_API_KEY=... python3 tools/tts.py all      # 전체 4,000개(ogg) → app/src/main/assets/audio/ (있는 파일은 건너뜀)
API 키는 GitHub Secrets의 GOOGLE_TTS_API_KEY로 넣는다. 코드나 저장소에 키를 적지 않는다.
"""
import base64
import json
import os
import re
import sys
import time
import unicodedata
from concurrent.futures import ThreadPoolExecutor
import urllib.error
import urllib.request
from pathlib import Path

API = "https://texttospeech.googleapis.com/v1"
ROOT = Path(__file__).resolve().parent.parent
KEY = os.environ.get("GOOGLE_TTS_API_KEY", "")

# 우선 시도할 여성 음성 이름. 실제로 있는 음성만 쓴다(voices.list로 확인).
PREFERRED_FEMALE = ["Aoede", "Kore", "Leda", "Zephyr", "Despina", "Sulafat", "Achernar", "Gacrux"]


def call(path: str, body: dict | None = None) -> dict:
    url = f"{API}/{path}{'&' if '?' in path else '?'}key={KEY}"
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return json.load(r)
    except urllib.error.HTTPError as e:
        raise RuntimeError(f"{e.code}: {e.read().decode()[:500]}") from None


def female_chirp_voices(lang: str, limit: int = 4) -> list[str]:
    voices = call(f"voices?languageCode={lang}").get("voices", [])
    names = [v["name"] for v in voices if "Chirp3-HD" in v["name"] and v.get("ssmlGender") == "FEMALE"]
    ordered = [n for p in PREFERRED_FEMALE for n in names if n.endswith(p)]
    ordered += [n for n in names if n not in ordered]
    return ordered[:limit]


def synth(voice: str, lang: str, *, text: str | None = None, ssml: str | None = None,
          encoding: str = "MP3") -> bytes:
    inp = {"ssml": ssml} if ssml else {"text": text}
    body = {"input": inp, "voice": {"languageCode": lang, "name": voice},
            "audioConfig": {"audioEncoding": encoding}}
    return base64.b64decode(call("text:synthesize", body)["audioContent"])


def sample():
    out = ROOT / "dist" / "samples"
    out.mkdir(parents=True, exist_ok=True)
    zh_items = [
        ("word_ai", {"text": "爱"}),
        ("word_hai_phoneme", {"ssml": '<speak><phoneme alphabet="pinyin" ph="hai2">还</phoneme></speak>'}),
        ("word_pianyi", {"text": "便宜"}),
        ("sent_1", {"text": "我爱我的家人。"}),
        ("sent_2", {"text": "现在三点半。"}),
        ("sent_3", {"text": "因为下雨，所以我没去。"}),
    ]
    ko_items = [
        ("meaning_1", {"text": "사랑하다, 좋아하다"}),
        ("meaning_2", {"text": "싸다, 저렴하다"}),
    ]
    report = []
    for lang, items in (("cmn-CN", zh_items), ("ko-KR", ko_items)):
        voices = female_chirp_voices(lang)
        report.append(f"{lang} 여성 Chirp3-HD 음성: {', '.join(voices) or '없음'}")
        for v in voices:
            short = v.split("-")[-1]
            for name, inp in items:
                try:
                    audio = synth(v, lang, **inp)
                    (out / f"{lang}_{short}_{name}.mp3").write_bytes(audio)
                except RuntimeError as e:
                    report.append(f"실패 {v} {name}: {e}")
    (out / "README.txt").write_text("\n".join(report) + "\n", encoding="utf-8")
    print("\n".join(report))
    print(f"샘플 {len(list(out.glob('*.mp3')))}개 생성")

# ---------------------------------------------------------------- 전체 생성
VOICE_ZH = ("cmn-CN", "cmn-CN-Chirp3-HD-Aoede")
VOICE_KO = ("ko-KR", "ko-KR-Chirp3-HD-Aoede")

# 1~3급에서 읽는 법이 둘 이상인 글자. 이 글자가 들어간 단어와 한 글자 단어는 병음을 지정해 읽힌다.
POLYPHONIC = set("还长得地了着行重只都好少种教便发为差干空数调假场处更中应当和会要分看正相觉乐量传结朝背难划似尽片冲大么哪那这什没的一不")

_INITIALS = ["zh", "ch", "sh", "b", "p", "m", "f", "d", "t", "n", "l", "g", "k", "h", "j", "q", "x", "r", "z", "c", "s", "y", "w", ""]
_FINALS = ["iang", "iong", "uang", "ueng", "ang", "eng", "ing", "ong", "ian", "iao", "uai", "uan", "üan", "ai", "ei", "ao", "ou",
           "an", "en", "er", "in", "ia", "ie", "iu", "ua", "uo", "ui", "un", "üe", "ün", "ue", "a", "o", "e", "i", "u", "ü"]
# 성모 없는 음절은 a·o·e로 시작하는 것만 있다 (i·u·ü로 시작하면 y·w를 붙여 쓴다)
_SYLLABLES = {i + f for i in _INITIALS for f in _FINALS if i or f[0] in "aoe"}
_TONE = {}
for base, marks in {"a": "āáǎà", "e": "ēéěè", "i": "īíǐì", "o": "ōóǒò", "u": "ūúǔù", "ü": "ǖǘǚǜ"}.items():
    for t, ch in enumerate(marks, 1):
        _TONE[ch] = (base, t)


def _split(letters: str, tones: list[int], first: bool = True):
    """성조를 뗀 글자열을 음절로 나눈다(긴 음절 우선, 안 되면 되돌아감).
    a·o·e로 시작하는 음절은 덩어리 맨 앞에만 온다 (중간이면 병음에 ’가 붙기 때문)."""
    if not letters:
        return []
    for n in range(min(6, len(letters)), 0, -1):
        head = letters[:n]
        if head in _SYLLABLES and (first or head[0] not in "aoe"):
            rest = _split(letters[n:], tones[n:], False)
            if rest is not None:
                tone = next((t for t in tones[:n] if t), 5)
                return [f"{head}{tone}"] + rest
    return None


def pinyin_to_numbered(py: str) -> str | None:
    """'bú kèqi' → 'bu2 ke4 qi5'. 나눌 수 없으면 None."""
    py = py.split("/")[0].lower()
    py = unicodedata.normalize("NFC", py).replace("’", " ").replace("'", " ").replace("-", " ")
    out = []
    for chunk in py.split():
        letters, tones = "", []
        for ch in chunk:
            if ch in _TONE:
                b, t = _TONE[ch]
                letters += b
                tones.append(t)
            elif ch.isalpha():
                letters += ch
                tones.append(0)
        parts = _split(letters, tones)
        if parts is None:
            return None
        out += parts
    return " ".join(out)


def word_input(w: dict) -> dict:
    hz, py = w["hanzi"], w["pinyin"]
    needs = len(hz) == 1 or any(c in POLYPHONIC for c in hz)
    if needs and "儿" not in hz:
        num = pinyin_to_numbered(py)
        if num and len(num.split()) == len(hz):
            return {"ssml": f'<speak><phoneme alphabet="pinyin" ph="{num}">{hz}</phoneme></speak>'}
    return {"text": hz}


def synth_retry(voice, lang, inp, tries=8):
    for k in range(tries):
        try:
            return synth(voice, lang, encoding="OGG_OPUS", **inp)
        except RuntimeError as e:
            msg = str(e)
            if k == tries - 1:
                raise
            if "ssml" in inp and msg.startswith("400"):
                inp = {"text": re.sub(r"<[^>]+>", "", inp["ssml"])}  # 병음 지정이 거부되면 글자 그대로 읽힌다
                continue
            # 429(분당 한도)는 길게 쉬었다가 다시 시도한다
            time.sleep(min(60, 5 * 2 ** k) if msg.startswith("429") else 2 ** k)


def generate_all():
    data = ROOT / "app" / "src" / "main" / "assets" / "data"
    assets = ROOT / "app" / "src" / "main" / "assets"
    words = json.loads((data / "words.json").read_text(encoding="utf-8"))
    sents = json.loads((data / "sentences.json").read_text(encoding="utf-8"))
    jobs = []
    for w in words:
        jobs.append((assets / w["audioZh"], VOICE_ZH, word_input(w)))
        jobs.append((assets / w["audioKo"], VOICE_KO, {"text": w["meaningTts"]}))
    for s in sents:
        jobs.append((assets / s["audio"], VOICE_ZH, {"text": s["zh"]}))
    todo = [j for j in jobs if not j[0].exists() or j[0].stat().st_size == 0]
    print(f"전체 {len(jobs)}개 중 생성할 파일 {len(todo)}개")
    failures = []

    def run(job):
        path, (lang, voice), inp = job
        try:
            audio = synth_retry(voice, lang, inp)
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(audio)
        except Exception as e:  # noqa: BLE001
            failures.append(f"{path.name}: {e}")

    with ThreadPoolExecutor(max_workers=3) as ex:
        list(ex.map(run, todo))
    small = [str(p.relative_to(assets)) for p, _, _ in jobs if p.exists() and p.stat().st_size < 800]
    missing = sum(1 for p, _, _ in jobs if not p.exists())
    phon = sum(1 for w in words if "ssml" in word_input(w))
    print(f"병음 지정 단어 {phon}개, 이번 실패 {len(failures)}개, 남은 빈 파일 {missing}개, 800B 미만 의심 파일 {len(small)}개")
    for f in [x.splitlines()[0] for x in failures[:10]] + small[:20]:
        print(" -", f)
    if missing:
        print(f"::warning::음성 {missing}개가 아직 없습니다. generate-audio all을 다시 실행하면 빈 파일만 채웁니다.")


if __name__ == "__main__":
    if not KEY:
        sys.exit("GOOGLE_TTS_API_KEY가 없습니다.")
    {"sample": sample, "all": generate_all}[sys.argv[1] if len(sys.argv) > 1 else "sample"]()
