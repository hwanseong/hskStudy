"""Google Cloud Text-to-Speech로 음성 파일을 만든다.

사용법:
  GOOGLE_TTS_API_KEY=... python3 tools/tts.py sample   # 여성 음성 후보별 샘플(mp3) → dist/samples/
API 키는 GitHub Secrets의 GOOGLE_TTS_API_KEY로 넣는다. 코드나 저장소에 키를 적지 않는다.
"""
import base64
import json
import os
import sys
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


if __name__ == "__main__":
    if not KEY:
        sys.exit("GOOGLE_TTS_API_KEY가 없습니다.")
    {"sample": sample}[sys.argv[1] if len(sys.argv) > 1 else "sample"]()
