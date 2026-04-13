# SecondMemory Technical Documentation

SecondMemory is an offline-first Android app for capturing raw thoughts, then generating and viewing daily markdown summaries.

The current implementation emphasizes:
1. File-based storage as the source of truth.
2. Date-first access to raw and summary data.
3. Optional cloud summarization via Gemini API.
4. Minimal local persistence complexity (DataStore for app settings and operation logs).

## Current Feature Status

Implemented:
1. Adaptive top-level navigation with routes for Raw Thoughts, Daily View, Settings, and Record Thought.
2. Launcher quick action (app icon long-press shortcut) for directly opening Record Thought.
3. Home screen Quick Record widget that opens Record Thought with one tap.
4. Record Thought runs as a dedicated activity without top-level navigation chrome.
5. Record Thought screen with speech-to-text, auto-start listening, visualizer, manual editing, and save.
6. Cursor-aware speech insertion (transcript inserts at current cursor/selection).
7. Raw Thoughts date browsing with edit/delete over daily raw JSON files.
8. Daily View summary cards (one per `data/daily/*.md`) with metadata:
   - raw thought count for the same day key
   - summary word count
   - last summarized timestamp
9. Daily summary detail screen with markdown rendering.
10. Settings with:
   - Google Drive sync toggle (foundation)
   - Gemini API key save/test
   - Cloud summaries toggle visible only when Gemini key exists
11. Gemini-powered summary generation from raw JSON using Daily View FAB actions:
   - `Summarize` FAB generates for today
   - `Calendar` FAB opens date picker for a specific day
12. Google Drive bidirectional sync engine for `data/raw`, `data/daily`, `data/weekly`, and `data/monthly` with conflict resolution.
13. WorkManager background jobs:
   - periodic Drive sync
   - nightly summary regeneration with previous-day targeting
   - shared network constraints and exponential backoff
14. Operation log history screen (accessible from Settings) to audit sync/work/settings events.

Not yet implemented:
1. Weekly and monthly summary generation pipelines.

## Architecture Overview

Layers:
1. `ui`: Compose screens, components, and navigation.
2. `domain`: repository contracts, models, and LLM abstraction.
3. `data`: file repositories, DataStore settings/sync/operation-log repositories, Gemini client.
4. `util`: date/time and data path utilities.

Primary composition and dependency wiring:
1. [app/src/main/java/com/secondmemory/MainActivity.kt](app/src/main/java/com/secondmemory/MainActivity.kt)

Navigation graph:
1. [app/src/main/java/com/secondmemory/ui/navigation/AppNavHost.kt](app/src/main/java/com/secondmemory/ui/navigation/AppNavHost.kt)
2. [app/src/main/java/com/secondmemory/ui/navigation/AppDestination.kt](app/src/main/java/com/secondmemory/ui/navigation/AppDestination.kt)

## Data Model and Storage

Canonical storage root: `Context.filesDir/data`

Directory structure:
1. `data/raw` -> raw thoughts by day as `yyyymmdd.json`
2. `data/daily` -> daily summaries as `yyyymmdd.md`
3. `data/weekly` -> weekly summaries as `yyyymmx.md`
4. `data/monthly` -> monthly summaries as `yyyymm.md`

Directory bootstrap utility:
1. [app/src/main/java/com/secondmemory/util/AppDataPaths.kt](app/src/main/java/com/secondmemory/util/AppDataPaths.kt)

Raw thought JSON schema (current):
1. root object with `schemaVersion`
2. `thoughts` array
3. per item:
   - `id`
   - `timestampMillis`
   - `text`
   - `source` (`SPEECH` or `MANUAL`)

Raw thought repository:
1. [app/src/main/java/com/secondmemory/data/repository/JsonThoughtRepository.kt](app/src/main/java/com/secondmemory/data/repository/JsonThoughtRepository.kt)

Daily summary repository:
1. [app/src/main/java/com/secondmemory/data/repository/FileDailySummaryRepository.kt](app/src/main/java/com/secondmemory/data/repository/FileDailySummaryRepository.kt)

## Cloud Summary Flow (Gemini)

User flow:
1. Open Settings.
2. Enter and save Gemini API key.
3. Test Gemini key.
4. Enable Cloud Summaries toggle.
5. In Daily View, either tap `Summarize` for today or use `Calendar` to pick a specific date.
6. App reads `data/raw/yyyymmdd.json`, calls Gemini, writes `data/daily/yyyymmdd.md`.

LLM abstraction (future-ready for multiple providers):
1. [app/src/main/java/com/secondmemory/domain/llm/LlmSummaryClient.kt](app/src/main/java/com/secondmemory/domain/llm/LlmSummaryClient.kt)

Gemini implementation:
1. [app/src/main/java/com/secondmemory/data/llm/GeminiLlmSummaryClient.kt](app/src/main/java/com/secondmemory/data/llm/GeminiLlmSummaryClient.kt)

Settings repository and model:
1. [app/src/main/java/com/secondmemory/data/repository/DataStoreSettingsRepository.kt](app/src/main/java/com/secondmemory/data/repository/DataStoreSettingsRepository.kt)
2. [app/src/main/java/com/secondmemory/domain/model/AppSettings.kt](app/src/main/java/com/secondmemory/domain/model/AppSettings.kt)

Operation log repository and model:
1. [app/src/main/java/com/secondmemory/data/repository/DataStoreOperationLogRepository.kt](app/src/main/java/com/secondmemory/data/repository/DataStoreOperationLogRepository.kt)
2. [app/src/main/java/com/secondmemory/domain/model/OperationLogEntry.kt](app/src/main/java/com/secondmemory/domain/model/OperationLogEntry.kt)

## Drive Sync Flow

Drive sync engine:
1. [app/src/main/java/com/secondmemory/data/drive/GoogleDriveSyncClient.kt](app/src/main/java/com/secondmemory/data/drive/GoogleDriveSyncClient.kt)

Sync repository integration:
1. [app/src/main/java/com/secondmemory/data/repository/DataStoreSyncRepository.kt](app/src/main/java/com/secondmemory/data/repository/DataStoreSyncRepository.kt)

Current sync behavior (bidirectional sync):
1. Syncs app `data/` folders bidirectionally with Google Drive under `com.secondmemory/data`.
2. Covers `raw`, `daily`, `weekly`, and `monthly` directories.
3. Files only on local device are uploaded to Drive.
4. Files only on Drive are downloaded to local device.
5. Files on both sides use modified-time comparison with a small skew window (last-write-wins).
6. Creates local conflict backups (named `filename.conflict.{timestamp}.ext`) when newer remote content overwrites local files.
7. **Deletion safety**: Files are never deleted from either location based on the state of the other. If a file is deleted locally, it is re-downloaded from Drive on next sync. If a file is deleted on Drive, it is re-uploaded to Drive on next sync. True deletion requires explicit deletion on both the device and Drive.

## Background Jobs (WorkManager)

Work scheduling:
1. [app/src/main/java/com/secondmemory/background/BackgroundWorkScheduler.kt](app/src/main/java/com/secondmemory/background/BackgroundWorkScheduler.kt)

Workers:
1. Drive sync worker: [app/src/main/java/com/secondmemory/background/DriveSyncWorker.kt](app/src/main/java/com/secondmemory/background/DriveSyncWorker.kt)
2. Nightly summary worker: [app/src/main/java/com/secondmemory/background/NightlySummaryWorker.kt](app/src/main/java/com/secondmemory/background/NightlySummaryWorker.kt)
3. Retry classifier: [app/src/main/java/com/secondmemory/background/WorkRetryPolicy.kt](app/src/main/java/com/secondmemory/background/WorkRetryPolicy.kt)

Registration point:
1. Scheduled during app startup in [app/src/main/java/com/secondmemory/MainActivity.kt](app/src/main/java/com/secondmemory/MainActivity.kt)

Job definitions:
1. Periodic Drive Sync
   - Unique name: `periodic_drive_sync`
   - Repeat interval: every 6 hours
   - Runs only when Drive sync is enabled in Settings
2. Nightly Daily Summary
   - Unique name: `nightly_daily_summary`
   - Repeat interval: every 24 hours
   - Initial alignment: next local 01:15
   - Target day: previous local day (for example, run at 01:15 on Apr 14 targets Apr 13)
   - Regenerates summary for that previous day when raw JSON exists

Shared WorkManager policy:
1. Network constraint: connected network required.
2. Backoff policy: exponential.
3. Retry base delay: 30 seconds.

## Screen Responsibilities

Raw Thoughts:
1. Date navigation (`Prev`, `Next`, `Today`).
2. List/edit/delete thoughts in selected day file.
3. FAB to Record Thought.
4. [app/src/main/java/com/secondmemory/ui/screen/rawthoughts/RawThoughtsScreen.kt](app/src/main/java/com/secondmemory/ui/screen/rawthoughts/RawThoughtsScreen.kt)

Record Thought:
1. Auto-start listening on open (permission-aware).
2. Live audio visualizer.
3. Manual edit field.
4. Speech insertion at cursor/selection.
5. Save to today's raw JSON.
6. [app/src/main/java/com/secondmemory/ui/screen/record/RecordThoughtScreen.kt](app/src/main/java/com/secondmemory/ui/screen/record/RecordThoughtScreen.kt)

Daily View:
1. List daily summary markdown files (`data/daily/*.md`) by day key.
2. Show metadata (raw thought count, summary word count, last summarized time).
3. Trigger summarization for today via `Summarize` FAB or for a selected day via `Calendar` FAB.
4. Collapse `Summarize` extended FAB text when the list is scrolled.
5. Open summary detail.
5. [app/src/main/java/com/secondmemory/ui/screen/dailyview/DailyViewScreen.kt](app/src/main/java/com/secondmemory/ui/screen/dailyview/DailyViewScreen.kt)

Daily Summary Detail:
1. Render markdown content.
2. [app/src/main/java/com/secondmemory/ui/screen/dailyview/DailySummaryDetailScreen.kt](app/src/main/java/com/secondmemory/ui/screen/dailyview/DailySummaryDetailScreen.kt)
3. Markdown component: [app/src/main/java/com/secondmemory/ui/component/MarkdownText.kt](app/src/main/java/com/secondmemory/ui/component/MarkdownText.kt)

Settings:
1. Sync toggle.
2. Gemini key save/test.
3. Conditional cloud summary toggle.
4. Operation logs entry point and screen.
5. [app/src/main/java/com/secondmemory/ui/screen/settings/SettingsScreen.kt](app/src/main/java/com/secondmemory/ui/screen/settings/SettingsScreen.kt)
6. [app/src/main/java/com/secondmemory/ui/screen/settings/OperationLogsScreen.kt](app/src/main/java/com/secondmemory/ui/screen/settings/OperationLogsScreen.kt)

## Permissions

Declared in [app/src/main/AndroidManifest.xml](app/src/main/AndroidManifest.xml):
1. `android.permission.RECORD_AUDIO`
2. `android.permission.INTERNET`

## Build and Run

Build debug APK:

```bash
./gradlew :app:assembleDebug
```

## Operational Notes

1. If Gemini key is not saved, cloud summary toggle is hidden.
2. If cloud summaries are disabled, Daily View summarize action will show a guidance message.
3. Daily summary markdown may be empty if generation fails or output is cleared.
4. Date keys are currently formatted as `yyyymmdd`.
5. Nightly background summary generation always targets the previous local day.

## AI/Contributor Guidance

Repository instructions for AI-assisted edits:
1. [AGENTS.md](AGENTS.md)

Important conventions:
1. Keep thought and summary content file-based.
2. Avoid introducing per-thought database storage.
3. Preserve the canonical `data/` directory contract.
4. Add or update KDoc for public Kotlin APIs when changing behavior.

