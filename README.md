# KQS – KanColle Quest Share

**KQS Logbook Plugin** は、艦これの受注任務を航海日誌改（logbook-kai）からChatGPTへ共有する、非公式の独立プラグインです。

## 構成

```text
艦これ (Chrome) → KLB Chrome Extension → KLB Logbook Plugin (127.0.0.1:8891)
                                           ↓
                              航海日誌改 ContentListenerSpi
                                ├ 既存の処理
                                └ KQS Logbook Plugin → コピー → ChatGPT
```

KLB Chrome拡張の追加インストールは不要で、CDP接続も増やしません。KLB本体・航海日誌改本体を変更せず、別JARとして導入します。

## 対応・制限
- 航海日誌改 **25.0.8** と既存 KLB **1.0.0** を想定しています。
- Java 21以上、JavaFX環境が必要です。Java 25.0.5 Fullは検証対象です。
- 任務一覧、受注、解除、報酬受領のAPI通信のみを監視し、任務名・番号・段階進捗を整形します。
- 任務一覧を未表示のページ・古い情報は不完全です。50%/80%はゲームの段階表示です。
- Cookie、認証トークン、APIの生データはディスクへ保存しません。任務状態はメモリのみで、再起動により消えます。
- ゲームサーバーへの追加リクエスト・自動操作・通信改変は行いません。
- ChatGPTを開くボタンは情報をコピーするのみです。貼り付け・送信はユーザーが行います。

## ビルド・テスト

```sh
export LOGBOOK_JAR=/path/to/logbook-kai.jar
bash scripts/build.sh
bash scripts/test.sh
```

配布JAR名：`kqs-logbook-plugin-v0.1.0-rc.1.jar`（インストール時の推奨名：`kqs-logbook-plugin.jar`）。

GitHub Actionsは固定の航海日誌改25.0.8をSHA-256照合してビルドし、テストを実施します。ビルド結果はActions artifactに保存されます。GitHub Releaseへの公開は未設定です。

## 導入・復旧

**現時点ではRelease Candidateであり、実機検証は未完了です。** CI成功だけで現用環境への導入安全性を保証しません。

航海日誌改を通常終了してから、既存のKLB JARを保持したまま、プラグインディレクトリへ別名のKQS JARを新規追加します。同名ファイルがある場合は上書きせず停止してください。

問題が発生した場合、新規追加したKQS JARのみを別フォルダーへ退避し、航海日誌改を再起動して元の環境に戻してください。旧KLB・旧Java・バックアップを削除しないでください。

## プロジェクト情報

- Product: **KQS – KanColle Quest Share**
- Plugin: **KQS Logbook Plugin**
- Repository: **ItukiMac/kancolle-quest-share**
- Build filename: `kqs-logbook-plugin-v0.1.0-rc.1.jar`
- Runtime filename: `kqs-logbook-plugin.jar`
- License: MIT

本プロジェクトは艦これ、DMM GAMES、航海日誌改、KLBの公式プロジェクトではありません。詳細は[NOTICE.md](NOTICE.md)を参照してください。
