# hskStudy

HSK 3.0 1~3급(1,000단어·예문 2,000개) 중국어 단어장 안드로이드 앱. Kotlin + Jetpack Compose, 완전 오프라인, 원어민 음성(TTS).

## 구성
- `data/` — 원본: 단어·뜻·예문 엑셀, HSK 3.0 시험대강(2025) 1~3급 목록
- `tools/build_data.py` — 원본 → `app/src/main/assets/data/*.json` 변환
- `app/` — 안드로이드 앱
- `.github/workflows/build-apk.yml` — main에 올리면 서명된 APK를 빌드해 Releases에 등록

## 필요한 Secrets
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
(없으면 컴파일 확인용 debug 빌드만 하고 Releases에는 올리지 않는다)

## 설치
갤럭시에서 Releases의 최신 APK를 받아 설치한다. 처음 한 번 "출처를 알 수 없는 앱 설치"를 허용해야 한다.
