package local.kancolle.kqs;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import logbook.listener.ContentListenerSpi;
import logbook.net.RequestMetaData;
import logbook.net.ResponseMetaData;
import logbook.plugin.lifecycle.StartUp;

/**
 * KQS shares the full available quest snapshot without adding controls to
 * logbook-kai's main layout. Right-click any quest pane / quest area.
 */
public final class KqsLogbookPlugin implements ContentListenerSpi, StartUp {
    private static final QuestStore STORE = new QuestStore();
    private static final String FILTER_PROPERTY = "kqs.quest.contextMenuFilter.v2";

    @Override
    public boolean test(RequestMetaData request) {
        if (request == null) return false;
        String path = request.getRequestURI();
        return path != null && (path.equals("/kcsapi/api_get_member/questlist")
            || path.equals("/kcsapi/api_req_quest/start")
            || path.equals("/kcsapi/api_req_quest/stop")
            || path.equals("/kcsapi/api_req_quest/clearitemget"));
    }

    @Override
    public void accept(RequestMetaData request, ResponseMetaData response) {
        if (request == null || response == null || response.getStatus() < 200 || response.getStatus() >= 300) return;
        try {
            Optional<InputStream> body = response.getResponseBody();
            if (body.isEmpty()) return;
            byte[] bytes;
            try (InputStream in = body.get()) { bytes = in.readNBytes(2_000_001); }
            if (bytes.length > 2_000_000) return;

            // Only the whitelisted quest parameters: no api_token or raw POST body.
            Map<String, String> params = new HashMap<>();
            params.put("api_page_no", request.getParameter("api_page_no", "1"));
            params.put("api_tab_id", request.getParameter("api_tab_id", "unknown"));
            params.put("api_quest_id", request.getParameter("api_quest_id", ""));
            STORE.accept(request.getRequestURI(), params, new String(bytes, StandardCharsets.UTF_8));
        } catch (Exception e) {
            System.err.println("[KQS] Quest response rejected: " + e.getClass().getSimpleName());
        }
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Platform.runLater(KqsLogbookPlugin::installFilters);
            } catch (RuntimeException e) {
                System.err.println("[KQS] UI not ready: " + e.getClass().getSimpleName());
            }
            try { Thread.sleep(2000); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    private static void installFilters() {
        for (Window window : Window.getWindows()) {
            Scene scene = window.getScene();
            if (scene == null || scene.getRoot() == null) continue;
            Parent root = scene.getRoot();
            if (Boolean.TRUE.equals(root.getProperties().get(FILTER_PROPERTY))) continue;
            if (!root.getStyleClass().contains("mainWindow")) continue;
            root.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
                if (!(event.getTarget() instanceof Node node) || !insideQuestArea(node)) return;
                // QuestPane contains an upstream "全て除去" context menu. Consume only
                // quest-area events, preventing destructive operations by mistake.
                event.consume();
                showMenu(window, event.getScreenX(), event.getScreenY());
            });
            root.getProperties().put(FILTER_PROPERTY, true);
        }
    }

    private static boolean insideQuestArea(Node node) {
        for (Node n = node; n != null; n = n.getParent()) {
            String id = n.getId();
            if ("questPane".equals(id) || "questbox".equals(id)) return true;
            if (n.getClass().getName().equals("logbook.internal.gui.QuestPane")) return true;
            if (n instanceof TitledPane pane && "任務".equals(pane.getText())) return true;
        }
        return false;
    }

    private static void showMenu(Window window, double x, double y) {
        MenuItem copyJson = new MenuItem("KQS：現在の全任務をJSONコピー");
        copyJson.setOnAction(e -> copy(QuestExporter.json(STORE), window));

        MenuItem copyPrompt = new MenuItem("KQS：任務消化計画用のJSON＋依頼文をコピー");
        copyPrompt.setOnAction(e -> copy(QuestExporter.prompt(STORE), window));

        MenuItem preview = new MenuItem("KQS：全任務JSONを別ウィンドウで表示");
        preview.setOnAction(e -> preview(window));

        MenuItem save = new MenuItem("KQS：全任務JSONをファイル保存...");
        save.setOnAction(e -> save(window));

        new ContextMenu(copyJson, copyPrompt, preview, save).show(window, x, y);
    }

    private static void copy(String text, Window owner) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        if (!Clipboard.getSystemClipboard().setContent(content)) {
            message(owner, "KQS", "コピーに失敗しました。", Alert.AlertType.ERROR);
        }
    }

    private static void preview(Window owner) {
        Stage stage = new Stage();
        if (owner != null) stage.initOwner(owner);
        stage.setTitle("KQS Logbook Plugin | 全任務JSON");
        TextArea area = new TextArea(QuestExporter.json(STORE));
        area.setEditable(false);
        area.setWrapText(false);
        Button copy = new Button("JSONをコピー");
        copy.setOnAction(e -> copy(area.getText(), stage));
        Button refresh = new Button("最新状態を再取得（表示更新）");
        refresh.setOnAction(e -> area.setText(QuestExporter.json(STORE)));
        VBox layout = new VBox(8, new javafx.scene.layout.HBox(8, copy, refresh), area);
        layout.setStyle("-fx-padding:12");
        VBox.setVgrow(area, javafx.scene.layout.Priority.ALWAYS);
        stage.setScene(new Scene(layout, 900, 640));
        stage.show();
    }

    private static void save(Window owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("KQS 全任務JSONの保存先を指定");
        chooser.setInitialFileName("kqs-quests-" +
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".json");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        java.io.File destination = chooser.showSaveDialog(owner);
        if (destination == null) return;
        Path path = destination.toPath();
        try {
            // Explicitly refuse overwriting an existing file.
            Files.writeString(path, QuestExporter.json(STORE), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        } catch (java.nio.file.FileAlreadyExistsException e) {
            message(owner, "KQS", "既存ファイルは上書きしません。別のファイル名を指定してください。", Alert.AlertType.WARNING);
        } catch (Exception e) {
            message(owner, "KQS", "保存に失敗しました: " + e.getClass().getSimpleName(), Alert.AlertType.ERROR);
        }
    }

    private static void message(Window owner, String title, String msg, Alert.AlertType type) {
        Alert alert = new Alert(type, msg);
        alert.setTitle(title);
        if (owner != null) alert.initOwner(owner);
        alert.show();
    }
}
