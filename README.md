# AudioNotes / ЛекторКонспект

Мобильное приложение для Android: записывает лекцию, локально распознаёт речь и превращает её
в структурированный Markdown-конспект с формулами LaTeX через LLM (Groq).

Реализация ведётся по [PRD.md](PRD.md).

---

## Стек

| Слой | Технология |
|------|------------|
| Язык / UI | Kotlin, Jetpack Compose, Material 3 |
| Архитектура | MVVM (`AppViewModel` + `StateFlow`), ручной DI через `ServiceLocator` |
| Хранилище | Room (метаданные и тексты) + файловая система (`files/audio/*.m4a`) |
| Запись | `MediaRecorder`, AAC/M4A, 44.1 кГц, 128 кбит/с — рассчитано на 5-часовые сессии |
| Распознавание | `SpeechRecognizer` (on-device на Android 12+, обычный — ниже) |
| LLM | Groq, OpenAI-совместимый API (`/chat/completions`), Retrofit + kotlinx.serialization |
| Рендеринг `.md` | WebView + локальные `marked.js` и `MathJax` (без интернета) |
| Настройки | DataStore Preferences |

---

## Что установлено на машине

* **JDK 17** (Eclipse Temurin 17.0.21) — `C:\Program Files\Eclipse Adoptium\jdk-17.0.21.9-hotspot`
* **Android SDK** — `C:\Android\Sdk`
  * `platforms;android-35`
  * `build-tools;35.0.0` (+ `34.0.0`, который ставит AGP автоматически)
  * `platform-tools` (adb)
  * `cmdline-tools;latest`
* **Gradle 8.13** — `tools\gradle-8.13` (в проекте используется Gradle Wrapper)
* **Android Studio** — `C:\Program Files\Android\Android Studio`

Переменные окружения (`JAVA_HOME`, `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `Path`) уже прописаны
в пользовательском окружении Windows. Для текущей сессии PowerShell может потребоваться
переоткрыть терминал.

> Проект лежит в папке с кириллицей в пути. AGP на Windows такие пути не поддерживает,
> поэтому в `gradle.properties` включён флаг `android.overridePathCheck=true`.
> Сборка и установка APK при этом работают, а вот Gradle-воркер юнит-тестов
> не может загрузить классы из не-ASCII пути (падает `ClassNotFoundException`).
> Поэтому тесты запускаются из ASCII-пути — см. раздел «Проверка».

---

## Запуск

1. Получить API-ключ Groq: <https://console.groq.com/keys>
2. Вписать ключ в `local.properties`:

   ```properties
   sdk.dir=C\:\\Android\\Sdk
   groq.api.key=gsk_ваш_ключ
   groq.model=llama-3.3-70b-versatile
   groq.base.url=https\://api.groq.com/openai/v1/
   ```

   Файл в `.gitignore`, в репозитории лежит шаблон `local.properties.example`.
   Ключ попадает в `BuildConfig.GROQ_API_KEY` — это прототипный вариант из PRD (раздел 7).

3. Собрать и установить на устройство/эмулятор:

   ```powershell
   cd C:\Users\admin\Documents\ЧЕКТОМОР\AudioNotes
   .\gradlew.bat :app:assembleDebug
   .\gradlew.bat :app:installDebug
   ```

4. В Android Studio: **File → Open** → указать корневую папку проекта.
   Студия подхватит тот же Gradle Wrapper.

> Если `local.properties` оставлен с заглушкой `YOUR_GROQ_API_KEY_HERE`, приложение соберётся и
> запишет лекцию, но на этапе генерации покажет ошибку «Не задан API-ключ Groq».

---

## Сценарий работы

1. **Главный экран** — большая круглая кнопка микрофона, таймер, счётчик распознанных символов.
   Слева вверху — история, справа вверху — настройки.
2. **Стоп** — диалог «Какая это была лекция?»: *Точные науки* или *Гуманитарные науки*
   (влияет на системный промпт).
3. **Обработка** — два шага: «Распознавание аудио…» → «Генерация конспекта…».
   После распознавания можно нажать «Проверить текст», поправить формулы и термины вручную
   (PRD, раздел 10) и продолжить.
4. **Результат** — конспект с отрисованными формулами LaTeX. Панель действий:
   * ⬇ — сохранить `.md` в «Загрузки»,
   * 🎤 — прослушать исходную запись,
   * **Т** — открыть полный текст расшифровки.
5. **История** — список всех записей с переходом к готовому конспекту.
6. **Настройки** — переключатель тёмной темы, версия приложения, используемая модель.

---

## Как устроена генерация конспекта

`SummaryGenerator` (app/src/main/java/com/audionotes/app/data/llm/SummaryGenerator.kt):

1. Текст режется на части по ~6000 символов (границы абзацев и предложений сохраняются).
2. Каждая часть уходит в Groq отдельным запросом с системным промптом из PRD (раздел 9).
3. Части объединяются финальным запросом-промптом `MERGE_SYSTEM_PROMPT`.
4. Если суммарный объём частей слишком велик, объединение выполняется итеративно
   (группы по 4, затем по 6 и т. д.).

Такой подход снимает лимит контекстного окна для 5-часовых лекций.

---

## Проверка

Сборка проходит успешно:

```powershell
.\gradlew.bat :app:assembleDebug
```

Юнит-тесты (4 штуки: чанкование текста, сохранность абзацев, различия промптов) запускаются
из ASCII-пути — воркер Gradle не читает классы из пути с кириллицей:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\run-tests-ascii.ps1
```

Скрипт копирует проект в `C:\AudioNotesVerify`, прогоняет `:app:testDebugUnitTest` и печатает
результат. Последний прогон: `tests="4" failures="0"`.

Готовый APK: `app/build/outputs/apk/debug/app-debug.apk` (~19 МБ, includes `marked.js` и
`MathJax` для офлайн-рендеринга).

Устройств/эмуляторов к машине не подключено (`adb devices` — пусто), поэтому запуск на
реальном устройстве и проверка 5-часовой записи остаются за вами:

```powershell
.\gradlew.bat :app:installDebug
```

---

## Известные ограничения MVP

* **Распознавание речи.** `SpeechRecognizer` работает параллельно с `MediaRecorder`; на части
  устройств второй захват микрофона блокируется системой. Если живой распознаватель не даёт
  текст, после остановки выполняется fallback — разбор уже записанного файла через
  `EXTRA_AUDIO_SOURCE` (Android 12+). Если и он недоступен, приложение сообщает об этом,
  а запись остаётся доступной для прослушивания.
* **Долгие записи** держатся на `FLAG_KEEP_SCREEN_ON`. Foreground-сервис для записи в фоне —
  следующий шаг развития.
* Таблицы в конспектах не поддерживаются (это требование PRD).
* API-ключ вшит в `BuildConfig` — для демонстрации. В плане ввод ключа пользователем в
  настройках либо переход на backend.

---

## Структура проекта

```
app/src/main/java/com/audionotes/app/
├── MainActivity.kt              — Activity, тема, флаг «не гасить экран»
├── AudioNotesApp.kt             — Application
├── core/ServiceLocator.kt       — контейнер зависимостей
├── data/
│   ├── local/                   — Room: сущность, DAO, база
│   ├── model/                   — LectureType, RecordingStatus
│   ├── remote/                  — Groq API, DTO, промпты (PRD, раздел 9)
│   ├── llm/SummaryGenerator.kt  — чанкование, запросы, объединение
│   └── RecordingRepository.kt
├── asr/SpeechTranscriber.kt     — SpeechRecognizer + fallback по файлу
├── audio/AudioRecorder.kt       — MediaRecorder (AAC/M4A)
├── export/MarkdownExporter.kt   — сохранение .md в «Загрузки» (MediaStore)
├── settings/SettingsRepository.kt
└── ui/                          — экраны, навигация, ViewModel, тема
```
