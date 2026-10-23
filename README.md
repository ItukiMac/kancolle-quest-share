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

Right-click within the quest area replaces the original upstream destructive **全て除去** right-click menu; other context menus are unaffected. Saving a JSON file requires an explicit path choice and refuses to overwrite an existing file.

## Information contained in the JSON

- ID, name, description, quest category, recurrence/API type, state (available/unaccepted, accepted, completed/unclaimed), 50%/80% progress *stage*, basic material rewards, bonus/invalid flags, reset-date estimate.
- Where logbook-kai has a quest resource: objective sea areas, nodes, boss and victory ranks, counts, expedition names, fleet ship types/names, fleet ordering/composition filters, recurrence overrides and optional hints.
- Timestamp and source, full-list ALL-tab observation and page counts, game-reported total and accepted count, coverage limitations.
- Explicit null for unknown prerequisites, unlock chains, exact counters, choice reward items, and event deadlines.

**All** means all game quests *currently available/unlocked and observed* in the local logbook-kai collection, not every quest in KanColle. Not-yet-unlocked quests and historically claimed rewards cannot be reconstructed from the current game API. Cache could be stale unless the current session observes the ALL tab. Conditions absent in logbook-kai's internal files must be looked up in wikiwiki.jp/kancolle and zekamashi.net.

The optional ChatGPT prompt requests parallel sorties organized by area, overlapping quest requirements and compatible fleets; prioritizes limited-time and rare rewards; investigates follow-on quests; treats resource-only and rank-points-only rewards as low priority; and calls out missing information.

## Build and test

Java 21+ and logbook-kai 25.0.8:

    export LOGBOOK_JAR=/path/to/logbook-kai.jar
    bash scripts/build.sh
    bash scripts/test.sh

CI compiles using the verified logbook-kai v25.0.8 distribution and provides an artifact. The standalone JAR is named **kqs-logbook-plugin-v0.2.0-rc.1.jar**; installed filename **kqs-logbook-plugin.jar**.

## Safe installation

This is a **release candidate** pending actual GUI and game API verification. Keep existing KLB and Java backups. Exit logbook-kai normally before adding the JAR in plugins, without overwriting anything. Do not activate more than one version of KQS. If the GUI does not work, exit logbook-kai, move only the newly installed KQS JAR aside, and restart normally. Do not remove the working KLB.

License: MIT. See NOTICE.md for independent-project acknowledgements.
