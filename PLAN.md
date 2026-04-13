## Plan: SecondMemory MVP Delivery

Build an offline-first Android app with Compose where thoughts are captured quickly (speech + edit), stored locally as daily JSON, browsed in Raw Thoughts and Daily View, then synced to Google Drive and summarized via cloud LLM. Keep v1 architecture simple but production-safe: ViewModel + repository + Room index + file storage + WorkManager.

**Steps**
1. Phase 1 - Product/Architecture Baseline
1. Confirm and document v1 scope from decisions: include Raw Thoughts CRUD, Daily View + markdown render, speech recording, Drive sync, cloud LLM summaries; defer homescreen widget and advanced conflict UI.
2. Refactor top-level navigation from enum-switch scaffold to route-based NavHost while preserving adaptive NavigationSuite UI container.
3. Introduce base app layers: ui, domain, data, worker, and dependency wiring (manual DI first, Hilt optional later).
4. Phase 2 - Local Data Foundation (*depends on Phase 1*)
5. Define domain models for Thought, DailyNote, SyncState, and Summary.
6. Implement local persistence split:
7. Room for indexes/metadata (id, timestamps, file/date, sync status).
8. JSON files in app storage for canonical daily content (one file per date).
9. DataStore for settings (Drive enabled, sync cadence, summary options).
10. Add repository interfaces and use-cases for create/update/delete/list and daily-file resolution.
11. Add migration-friendly schema/versioning for JSON and Room.
12. Phase 3 - Core Screens and Flows (*depends on Phase 2*)
13. Raw Thoughts screen: list today thoughts, create/edit/delete, source tag (speech/manual), optimistic updates.
14. Record Thought screen: mic control, SpeechRecognizer streaming transcript, editable text field, save to current day JSON + Room index update.
15. Daily View screen: list daily files (date + preview), open detail screen that renders markdown/plain content.
16. Settings screen: Google sign-in/connect state, manual sync, summary trigger, sync diagnostics.
17. Wire FAB from Raw Thoughts to Record Thought route.
18. Phase 4 - Cloud and Background (*depends on Phase 3*)
19. Google Drive integration (app-folder strategy): upload/download daily files and metadata checkpoints.
20. Define deterministic sync algorithm: change detection by checksum + modified time, conflict policy for v1 (last-write-wins with backup copy).
21. Add WorkManager jobs for periodic sync and nightly summary generation.
22. Add Cloud LLM summary pipeline: send day content, store generated summary locally, and sync to Drive.
23. Add retry/backoff, network constraints, idempotency keys, and user-visible failure states.
24. Phase 5 - Hardening and Release Readiness (*parallel with late Phase 4 polish*)
25. Permissions and edge-case handling: microphone denial, no speech service, offline mode, auth expiry.
26. Performance and UX pass: loading states, empty/error states, large-day file handling.
27. Test pyramid: unit tests for repositories/use-cases, instrumentation for record/save/view/sync happy path, worker tests.
28. Add release checklist and observability: structured logs, crash-safe file writes, backup/restore validation.

**Relevant files**
- d:/workspaces/android_workspace/SecondMemory/app/src/main/java/com/secondmemory/MainActivity.kt - replace placeholder destination logic with NavHost routes and app shell composition.
- d:/workspaces/android_workspace/SecondMemory/app/build.gradle.kts - add dependencies for navigation-compose, room, datastore, workmanager, auth/drive, markdown renderer.
- d:/workspaces/android_workspace/SecondMemory/gradle/libs.versions.toml - centralize new dependency versions and aliases.
- d:/workspaces/android_workspace/SecondMemory/app/src/main/AndroidManifest.xml - permissions, services, and worker-related declarations where needed.
- d:/workspaces/android_workspace/SecondMemory/README.md - update roadmap into MVP + post-MVP milestones after alignment.

**Verification**
1. Run unit tests for data/domain layers and ensure CRUD + JSON serialization round-trips pass.
2. Run instrumentation test for speech fallback path (manual text entry) and normal speech capture save flow.
3. Validate daily file creation/opening across day boundaries and timezone edge around midnight.
4. Validate Drive sign-in, first sync, re-sync with modified local and remote files, and conflict backup generation.
5. Validate WorkManager scheduled jobs under network available/unavailable and app restart scenarios.
6. Perform manual UX checklist on phone + emulator for navigation, FAB entry, edit/delete, markdown view, and settings actions.

**Decisions**
- Included in MVP: Raw Thoughts CRUD, Daily View + markdown, SpeechRecognizer-based capture, Google Drive sync, cloud LLM summaries.
- Excluded from MVP: home screen widget, advanced merge UI, on-device LLM summaries.
- Recommended architectural change: keep JSON as source-of-truth for your original vision, but add Room index for fast list/search and sync bookkeeping.
- Recommended product change: ship cloud summary behind a setting toggle with cost/privacy notice and fallback to no-summary when API unavailable.

**Further Considerations**
1. Min SDK is currently 33; if you want broader adoption, consider lowering to 26-28 before building complex features, because migration later can be painful.
2. For Drive conflicts, add a silent backup file strategy in v1 (for example date.conflict.timestamp.json) to avoid accidental data loss.
3. Capture a privacy policy early since microphone + cloud summary + Drive sync materially affects user trust and Play Store review.
