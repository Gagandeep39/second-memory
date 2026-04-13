## Plan: Next Steps Only

This file now tracks only remaining work. Completed phases (foundation, file storage, core screens, recording, and Gemini summary trigger) are intentionally removed.

## Phase 4: Sync and Background

1. Harden the Google Drive directory mirror for local `data/`.
2. Sync scope must continue to include:
   1. `data/raw`
   2. `data/daily`
   3. `data/weekly`
   4. `data/monthly`
3. Harden the deterministic sync strategy:
   1. Compare by relative path + modified time + size/hash.
   2. Use last-write-wins for v1.
4. Finish sync diagnostics and recovery UX in Settings:
    1. Last sync time.
    2. Last sync status/error.
    3. File counts uploaded/downloaded.
    4. Consent and re-auth recovery for Google Drive access.
5. Add WorkManager jobs:
    1.  Periodic background sync.
    2.  Nightly daily-summary generation trigger.
    3.  Add retry/backoff and network constraints for background jobs.

## Phase 5: Hardening and Release Readiness

1. Reliability and edge cases:
1. Microphone unavailable/denied behavior.
2. Gemini/network failure handling.
3. Offline app behavior and user guidance.
2. UX polish:
1. Loading/empty/error states across screens.
2. Better status surfaces for summarize/sync jobs.
3. Performance for large daily files.
3. Testing:
1. Unit tests for repositories and formatting utilities.
2. Instrumentation tests for record/edit/summarize/open flows.
3. Background worker tests for scheduling/retry paths.
4. Release prep:
1. Crash-safe writes for JSON/markdown outputs.
2. Privacy policy alignment for microphone + cloud LLM + sync.
3. Observability/logging for sync and summary pipelines.

## Immediate Sprint (Recommended)

1. Add background periodic sync worker and verify with emulator tests.
2. Add retry/backoff and network constraints to sync and summary jobs.
3. Tighten sync conflict handling and diagnostics after real-world testing.

## Current Non-Goals

1. Per-thought database indexing.
2. Advanced merge UI.
3. On-device LLM summarization.
4. Home screen widget.
