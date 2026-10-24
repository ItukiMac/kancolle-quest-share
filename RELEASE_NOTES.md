# KQS Logbook Plugin v1.0.0

## Purpose
Export all currently available/unlocked KanColle quests known to logbook-kai as JSON, including accepted and unaccepted quests, for analysis in ChatGPT.

## Included
- Right-click quest area to copy JSON, copy JSON with an optional ChatGPT request, preview, or save to a new file.
- Game API ID, name, description, category, type, accepted/unaccepted/unclaimed state, 50%/80% progress stage, and basic material rewards.
- Existing logbook-kai local mission condition data when available, with provenance and conflict warnings.
- `snapshot_verified` completeness evidence (observed ALL-tab API vs. logbook-kai quest IDs, state, metadata, game-day and freshness).
- No change to KLB or Chrome integration; no new network client or automatic ChatGPT submissions.
- Unit tests for API capture, JSON, category handling, time boundaries and mismatches.

## Deliberately out of scope
- Quest ranking, sortie planning, external-guide scraping, reward value judgments or prerequisite-chain calculations.
- The game quest API cannot enumerate locked quests, previously claimed completions, selectable reward details or future event expiration dates. Unknown values remain null. ChatGPT may independently consult current references.
- No wiki content is bundled in this plugin.

## Requirements and install
logbook-kai 25.0.8; a Java runtime with JavaFX; existing KLB plugin may remain installed.
1. Close logbook-kai normally.
2. Back up ONLY the previous `plugins/kqs-logbook-plugin.jar`.
3. Install the published `kqs-logbook-plugin-v1.0.0.jar` as `plugins/kqs-logbook-plugin.jar`, ensuring only one KQS JAR is present.
4. Restart logbook-kai, open KanColle's quest `全て` tab, and right-click the quest area to export JSON.
5. Verify `snapshot_verified=true`. If false, inspect `completeness.status` and reopen the game ALL tab.
6. For rollback, stop logbook-kai and restore only the backed-up KQS JAR.

The published SHA256SUMS authenticates the GitHub-generated JAR byte-for-byte.
