package local.kancolle.kqs;

import java.awt.Desktop;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import logbook.listener.ContentListenerSpi;
import logbook.net.RequestMetaData;
import logbook.net.ResponseMetaData;
import logbook.plugin.lifecycle.StartUp;

/** Independent observer using the existing logbook-kai ContentListenerSpi. */
public final class KqsLogbookPlugin implements ContentListenerSpi, StartUp {
    private static final QuestStore STORE = new QuestStore();
    private static final String BAR_ID = "kancolle-quest-share-toolbar";
    @Override public boolean test(RequestMetaData request) {
        if (request == null) return false;
        String path = request.getRequestURI();
        return path != null && (path.equals("/kcsapi/api_get_member/questlist")
            || path.equals("/kcsapi/api_req_quest/start")
            || path.equals("/kcsapi/api_req_quest/stop")
            || path.equals("/kcsapi/api_req_quest/clearitemget"));
    }
    @Override public void accept(RequestMetaData request, ResponseMetaData response) {
        if (request == null || response == null || response.getStatus() < 200 || response.getStatus() >= 300) return;
        try {
            Optional<InputStream> maybe = response.getResponseBody();
            if (maybe.isEmpty()) return;
            byte[] data;
            try (InputStream in = maybe.get()) { data = in.readNBytes(2_000_001); }
            if (data.length > 2_000_000) return;
            Map<String,String> params = new HashMap<>();
            // Deliberately read only quest parameters. api_token is not accessed or stored.
            params.put("api_page_no", request.getParameter("api_page_no", "1"));
            params.put("api_tab_id", request.getParameter("api_tab_id", "all"));
            params.put("api_quest_id", request.getParameter("api_quest_id", ""));
            STORE.accept(request.getRequestURI(), params, new String(data, StandardCharsets.UTF_8));
        } catch (Exception e) {
            System.err.println("[KQS] malformed quest response: " + e.getClass().getSimpleName());
        }
    }
    @Override public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try { Platform.runLater(KqsLogbookPlugin::installOrRefreshBar); }
            catch (RuntimeException e) { System.err.println("[KQS] UI unavailable: " + e.getClass().getSimpleName()); }
            try { Thread.sleep(2000); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }
    private static void installOrRefreshBar() {
        for (Window window : Window.getWindows()) {
            Scene scene = window.getScene();
            if (scene == null) continue;
            Parent root = scene.getRoot();
            if (!(root instanceof VBox container) || !root.getStyleClass().contains("mainWindow")) continue;
            for (Node node : container.getChildren()) {
                if (BAR_ID.equals(node.getId()) && node instanceof HBox bar) {
                    for (Node child : bar.getChildren()) {
                        if (child instanceof Label label) { label.setText(STORE.status()); return; }
                    }
                }
            }
            HBox bar = new HBox(10);
            bar.setId(BAR_ID);
            bar.setStyle("-fx-padding:4 8 4 8;-fx-background-color:#edf4ff;-fx-border-color:#d3e1f3;-fx-border-width:0 0 1 0;");
            Button share = new Button("KQS：任務をChatGPTへ");
            share.setOnAction(e -> showShareDialog(window));
            Label status = new Label(STORE.status());
            bar.getChildren().addAll(share, status);
            container.getChildren().add(Math.min(1, container.getChildren().size()), bar);
            return;
        }
    }
    private static void showShareDialog(Window owner) {
        CheckBox details = new CheckBox("観測済み未受注任務と任務詳細も含める");
        TextArea text = new TextArea(STORE.prompt(false));
        text.setWrapText(true);
        text.setEditable(false);
        Label feedback = new Label("ゲームの任務一覧を開いて取得してください。未表示ページは不完全です。");
        Button refresh = new Button("更新");
        refresh.setOnAction(e -> text.setText(STORE.prompt(details.isSelected())));
        details.setOnAction(e -> text.setText(STORE.prompt(details.isSelected())));
        Button copy = new Button("コピー");
        copy.setOnAction(e -> {
            ClipboardContent value = new ClipboardContent();
            value.putString(text.getText());
            feedback.setText(Clipboard.getSystemClipboard().setContent(value)
                ? "コピーしました。ChatGPTへ貼り付けてください。" : "コピーに失敗しました。");
        });
        Button open = new Button("ChatGPTを開く");
        open.setOnAction(e -> {
            try {
                if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) throw new UnsupportedOperationException();
                Desktop.getDesktop().browse(URI.create("https://chatgpt.com/"));
            } catch (Exception ex) {
                feedback.setText("https://chatgpt.com/ を手動で開いてください。");
            }
        });
        VBox pane = new VBox(9);
        pane.setStyle("-fx-padding:14;");
        pane.getChildren().addAll(new Label("KQS Logbook Plugin — 艦これ任務共有（ChatGPT）"), details, text,
            new HBox(8, refresh, copy, open), feedback);
        Stage dialog = new Stage();
        if (owner != null) dialog.initOwner(owner);
        dialog.setTitle("KQS Logbook Plugin — 艦これ任務共有");
        dialog.setScene(new Scene(pane, 760, 530));
        dialog.show();
    }
}
