# KQS – KanColle Quest Share

KQS Logbook Plugin is an unofficial independent logbook-kai plugin for exporting **all currently available quests known to logbook-kai**, not only accepted quests.

## Architecture

KanColle (Chrome) → existing KLB Chrome Extension → KLB Logbook Plugin → logbook-kai (existing UI and KQS independent observer).

No second Chrome debugger connection, no changes to KLB, and **no additional toolbar or button in the main window**.

## How to use

1. Open the game quest list, preferably **全て (ALL)**. This refreshes available, accepted and completed-but-unclaimed quest data.
2. Right-click a quest entry or any part of the **任務** region in logbook-kai's main/mother-port quest panel.
3. Select one of: **KQS：現在の全任務をJSONコピー**, **KQS：任務消化計画用のJSON＋依頼文をコピー**, **KQS：全任務JSONを別ウィンドウで表示**, **KQS：全任務JSONをファイル保存...**.
4. Paste the JSON/prompt into ChatGPT. The plugin never sends it automatically.

Right-click within the quest area opens the KQS context menu. When right-clicking a quest item, the original **全て除去** action remains available under **航海日誌改（元の操作）**, now protected by a confirmation dialog; other context menus are unaffected. JSON generation runs on a daemon worker so it does not block JavaFX rendering. Saving a JSON file requires an explicit path choice and refuses to overwrite an existing file.

## Information contained in the JSON

- ID, name, description, quest category, recurrence/API type, state (available/unaccepted, accepted, completed/unclaimed), 50%/80% progress *stage*, basic material rewards, bonus/invalid flags, reset-date estimate.
- Where logbook-kai has a quest resource: objective sea areas, nodes, boss and victory ranks, counts, expedition names, fleet ship types/names, fleet ordering/composition filters, recurrence overrides and optional hints.
- Timestamp and source, full-list ALL-tab observation and page counts, game-reported total and accepted count, coverage limitations.
- **snapshot_verified** and **completeness.status**: latest ALL-tab API quest IDs, states and progress must match logbook-kai, API count must match, capture must be within 15 minutes and within the same 05:00-JST game day, and no quest mutations may have followed capture.
- **condition_conflict_codes** flags API/local reset-period differences and possible mismatching fleet names. Local quest conditions are *advisory*, not authoritative; externally verify warnings.
- **condition_coverage** summarizes how many of the currently visible quest records have local structured conditions, which IDs still lack them, and which ones need a condition check. This reports data availability rather than inventing missing objective or reward details.
- Fleet-name warnings compare API-explicitly enumerated names (including recursively nested conditions), not generic prose such as “米英艦艇”. Remodel aliases separated by a slash are treated as alternatives instead of independent missing ships.
- A verified snapshot also checks API quest **title, description, category and recurrence type**, whenever those fields are present, in addition to IDs/state/progress. Verification expires at 15 minutes and at the next 05:00 JST game-day boundary; clock rollback is treated as stale. If API reports the page count, every page must be observed.
- This release candidate includes deterministic regression tests simulating both sides of the 05:00 JST boundary, the 15-minute expiration and stale title/category data.
- Explicit null for unknown prerequisites, unlock chains, exact counters, choice reward items, and event deadlines.

**All** means all game quests *currently available/unlocked and observed* in the local logbook-kai collection, not every quest in KanColle. Not-yet-unlocked quests and historically claimed rewards cannot be reconstructed from the current game API. Cache could be stale unless the current session observes the ALL tab. Conditions absent in logbook-kai's internal files must be looked up in wikiwiki.jp/kancolle and zekamashi.net.

The optional ChatGPT prompt requests parallel sorties organized by area, overlapping quest requirements and compatible fleets; prioritizes limited-time and rare rewards; investigates follow-on quests; treats resource-only and rank-points-only rewards as low priority; and calls out missing information.

## Build and test

Java 21+ and logbook-kai 25.0.8:

    export LOGBOOK_JAR=/path/to/logbook-kai.jar
    bash scripts/build.sh
    bash scripts/test.sh

CI compiles using the verified logbook-kai v25.0.8 distribution and provides an artifact. The standalone JAR is named **kqs-logbook-plugin-v1.0.0.jar**; installed filename **kqs-logbook-plugin.jar**.

## Safe installation

Obtain the release JAR and its `SHA256SUMS` from [Releases](https://github.com/ItukiMac/kancolle-quest-share/releases/tag/v1.0.0); verify its SHA256 before installation.

v1.0.0 is the first stable release after Java 25/Linux Mint GUI and game API verification (54/54 currently available quests matching). Keep existing KLB and Java backups. Exit logbook-kai normally before adding the JAR in plugins, without overwriting anything. Do not activate more than one version of KQS. If the GUI does not work, exit logbook-kai, move only the newly installed KQS JAR aside, and restart normally. Do not remove the working KLB.

License: MIT. See NOTICE.md for independent-project acknowledgements.

## Project boundary

KQS only exports observed quest data (game API plus explicitly labeled logbook-kai local conditions). Quest priorities, sortie plans, following prerequisites and current reward assessments belong in ChatGPT. No wiki database, reward database, quest planner, scraper or automatic data upload is included.
