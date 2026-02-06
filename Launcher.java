import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

/**
 * ImLang IDE Launcher.
 * Connects the JavaFX GUI to the refactored Scanner, Parser, AND Semantic Analyzer phases.
 * This class handles the initialization of the UI, file operations, and the execution
 * of the compiler pipeline.
 * @author John Seibert, Dylan Kauffman, Jack Norfolk
 */
public class Launcher extends Application {

    private TextArea codeArea;
    private TextArea lineNumArea;
    private TextArea consoleArea;

    private Label lblCursorPos;
    private Label lblStatus;

    private File currentFile;
    private boolean isDirty = false;

    // Buffered console output stream for performance
    private ConsoleOutputStream consoleStream;

    /**
     * The main entry point for the Java application, launching the JavaFX GUI.
     * @param args Command line arguments.
     */
    public static void main(String[] args) {
        launch(args);
    }

    /**
     * The primary entry point for the JavaFX application.
     * Initializes the main window (Stage), sets up the user interface components
     * (editor, console, menus), and establishes event handlers.
     *
     * @param primaryStage The primary stage for this application,
     * onto which the application scene is set.
     */
    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("ImLang IDE - Professional Build");
        primaryStage.setOnCloseRequest(e -> {
            if (!checkUnsavedChanges(primaryStage)) {
                e.consume();
            }
        });

        codeArea = new TextArea();
        codeArea.setPromptText("Enter ImLang source code here...");
        codeArea.setWrapText(false);
        codeArea.setStyle("-fx-font-family: 'Consolas', 'Monospace'; -fx-font-size: 14;");

        lineNumArea = new TextArea("1");
        lineNumArea.setEditable(false);
        lineNumArea.setPrefWidth(50);
        lineNumArea.setWrapText(false);
        lineNumArea.setStyle("-fx-font-family: 'Consolas', 'Monospace'; -fx-font-size: 14; " +
                "-fx-text-fill: gray; -fx-control-inner-background: #f4f4f4;");
        lineNumArea.setFocusTraversable(false);

        consoleArea = new TextArea();
        consoleArea.setEditable(false);
        consoleArea.setPromptText("Console Output (Trace & Errors)...");
        consoleArea.setPrefHeight(300);
        consoleArea.setStyle("-fx-font-family: 'Consolas', 'Monospace'; -fx-font-size: 12;");

        redirectSystemStreams();

        setupLineNumberSync();
        setupCursorTracking();

        HBox editorContainer = new HBox(lineNumArea, codeArea);
        HBox.setHgrow(codeArea, Priority.ALWAYS);

        HBox statusBar = createStatusBar();
        VBox bottomContainer = new VBox(consoleArea, statusBar);
        VBox topContainer = new VBox(createMenuBar(primaryStage),
                createToolbar(primaryStage));

        BorderPane root = new BorderPane();
        root.setTop(topContainer);
        root.setCenter(editorContainer);
        root.setBottom(bottomContainer);

        updateTitle(primaryStage);

        Scene scene = new Scene(root, 1000, 750);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    /**
     * Buffered OutputStream implementation used to redirect System.out/err
     * into the consoleArea TextArea efficiently.
     */
    private static class ConsoleOutputStream extends OutputStream {
        private final TextArea consoleArea;
        private final StringBuilder buffer = new StringBuilder();
        // Threshold at which we flush batched output to the UI
        private static final int FLUSH_THRESHOLD = 4096;

        ConsoleOutputStream(TextArea consoleArea) {
            this.consoleArea = consoleArea;
        }

        @Override
        public void write(int b) {
            // Buffer single characters; don't push every char to the UI
            buffer.append((char) b);
            if (buffer.length() >= FLUSH_THRESHOLD) {
                flushBufferInternal();
            }
        }

        @Override
        public void write(byte[] b, int off, int len) {
            buffer.append(new String(b, off, len));
            if (buffer.length() >= FLUSH_THRESHOLD) {
                flushBufferInternal();
            }
        }

        private void flushBufferInternal() {
            if (buffer.length() == 0) return;
            String text = buffer.toString();
            buffer.setLength(0);
            Platform.runLater(() -> consoleArea.appendText(text));
        }

        public void flushBuffer() {
            flushBufferInternal();
        }
    }

    /**
     * Redirects console output to the GUI's console area using a buffered
     * {@code ConsoleOutputStream}. This drastically reduces the number of
     * UI updates and makes parsing/printing much faster.
     */
    private void redirectSystemStreams() {
        consoleStream = new ConsoleOutputStream(consoleArea);
        PrintStream ps = new PrintStream(consoleStream, true);
        System.setOut(ps);
        System.setErr(ps);
    }

    /**
     * Sets up listeners to synchronize the scrolling of the line number area
     * with the code editor. Also sets a listener on the code area's text
     * property to detect changes and call {@code updateLineNumbers()}.
     */
    private void setupLineNumberSync() {
        lineNumArea.scrollTopProperty().bindBidirectional(codeArea.scrollTopProperty());
        codeArea.textProperty().addListener((_, oldVal, newVal) -> {
            if (!oldVal.equals(newVal)) setIsDirty(true);
            updateLineNumbers();
        });
        updateLineNumbers();
    }

    /**
     * Recalculates and updates the text in the lineNumArea to match
     * the current number of lines in the codeArea.
     */
    private void updateLineNumbers() {
        int lines = codeArea.getText().split("\n", -1).length;
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= lines; i++) sb.append(i).append("\n");
        lineNumArea.setText(sb.toString());
    }

    /**
     * Sets up a listener on the code area's caret position to track
     * and display the current line and column number in the status bar label.
     */
    private void setupCursorTracking() {
        codeArea.caretPositionProperty().
                addListener((_, _, newVal) -> {
                    int pos = newVal.intValue();
                    String text = codeArea.getText();
                    if (pos > text.length()) pos = text.length();

                    int line = 1;
                    int col = 1;
                    for (int i = 0; i < pos; i++) {
                        if (text.charAt(i) == '\n') {
                            line++;
                            col = 1;
                        } else {
                            col++;
                        }
                    }
                    lblCursorPos.setText("Ln " + line + ", Col " + col);
                });
    }

    /**
     * Creates and configures the status bar component for the IDE's bottom section.
     * It includes labels for cursor position and the current status.
     *
     * @return An {@code HBox} representing the status bar.
     */
    private HBox createStatusBar() {
        HBox statusBar = new HBox(20);
        statusBar.setPadding(new Insets(5, 10, 5, 10));
        statusBar.setStyle("-fx-background-color: #e0e0e0; " +
                "-fx-border-color: #bfbfbf; -fx-border-width: 1 0 0 0;");
        lblCursorPos = new Label("Ln 1, Col 1");
        lblStatus = new Label("Status: Ready");
        lblStatus.setStyle("-fx-font-weight: bold;");

        statusBar.getChildren().addAll(lblCursorPos, new Separator(), lblStatus);
        return statusBar;
    }

    /**
     * Updates the main window's title to reflect the name of the currently open file
     * and whether the file has unsaved changes (marked with an asterisk '*').
     *
     * @param stage The primary stage whose title needs updating.
     */
    private void updateTitle(Stage stage) {
        String fileName = (currentFile == null) ? "Untitled" : currentFile.getName();
        String dirtyMarker = isDirty ? " *" : "";
        stage.setTitle(fileName + dirtyMarker + " - ImLang IDE");
    }

    /**
     * Sets the dirty state of the editor (whether there are unsaved changes).
     * If the state changes, it updates the window title to show/hide the dirty marker.
     * @param dirty The new dirty state: true if there are unsaved changes,
     * false otherwise.
     */
    private void setIsDirty(boolean dirty) {
        if (isDirty != dirty) {
            isDirty = dirty;
            if (codeArea.getScene() != null && codeArea.getScene().
                    getWindow() instanceof Stage) {
                updateTitle((Stage) codeArea.getScene().getWindow());
            }
        }
    }

    /**
     * Creates and populates the application's menu bar with "File",
     * "Edit", and "Run" menus, linking menu items to their respective
     * actions (New, Open, Save, Undo, Run Parser, etc.).
     * @param primaryStage The stage used for file dialogs and window management.
     * @return The configured {@code MenuBar} object.
     */
    private MenuBar createMenuBar(Stage primaryStage) {
        MenuBar menuBar = new MenuBar();

        Menu fileMenu = new Menu("File");
        MenuItem newFile = new MenuItem("New");
        newFile.setOnAction(_ -> onNewFile(primaryStage));
        MenuItem openFile = new MenuItem("Open...");
        openFile.setOnAction(_ -> onOpenFile(primaryStage));
        MenuItem saveFile = new MenuItem("Save");
        saveFile.setOnAction(_ -> onSaveFile(primaryStage));
        MenuItem exit = new MenuItem("Exit");
        exit.setOnAction(_ -> Platform.exit());
        fileMenu.getItems().addAll(newFile, openFile, saveFile,
                new SeparatorMenuItem(), exit);

        Menu editMenu = new Menu("Edit");
        MenuItem undo = new MenuItem("Undo");
        undo.setOnAction(_ -> codeArea.undo());
        MenuItem redo = new MenuItem("Redo");
        redo.setOnAction(_ -> codeArea.redo());
        MenuItem cut = new MenuItem("Cut");
        cut.setOnAction(_ -> codeArea.cut());
        MenuItem copy = new MenuItem("Copy");
        copy.setOnAction(_ -> codeArea.copy());
        MenuItem paste = new MenuItem("Paste");
        paste.setOnAction(_ -> codeArea.paste());
        editMenu.getItems().addAll(undo, redo, new SeparatorMenuItem(), cut, copy, paste);

        Menu imLangMenu = new Menu("Run");
        MenuItem runParser = new MenuItem("Run Parser (F5)");
        runParser.setOnAction(_ -> onRunParser());
        imLangMenu.getItems().add(runParser);

        menuBar.getMenus().addAll(fileMenu, editMenu, imLangMenu);
        return menuBar;
    }

    /**
     * Creates and configures the application's toolbar with
     * buttons for common operations (New, Open, Save, Undo/Redo, Cut/Copy/Paste)
     * and the main "Run Parser" action.
     * @param primaryStage The stage used for file dialogs.
     * @return An {@code HBox} representing the toolbar.
     */
    private HBox createToolbar(Stage primaryStage) {
        HBox toolbar = new HBox(5);
        toolbar.setPadding(new Insets(10));
        toolbar.setStyle("-fx-background-color: #f4f4f4; -fx-border-color: gray; -" +
                "fx-border-width: 0 0 1 0;");
        Button newBtn = new Button("New");
        newBtn.setOnAction(_ -> onNewFile(primaryStage));
        Button openBtn = new Button("Open");
        openBtn.setOnAction(_ -> onOpenFile(primaryStage));
        Button saveBtn = new Button("Save");
        saveBtn.setOnAction(_ -> onSaveFile(primaryStage));

        Button cutBtn = new Button("Cut");
        cutBtn.setOnAction(_ -> codeArea.cut());
        Button copyBtn = new Button("Copy");
        copyBtn.setOnAction(_ -> codeArea.copy());
        Button pasteBtn = new Button("Paste");
        pasteBtn.setOnAction(_ -> codeArea.paste());

        Button undoBtn = new Button("↶");
        undoBtn.setTooltip(new Tooltip("Undo"));
        undoBtn.setOnAction(_ -> codeArea.undo());
        Button redoBtn = new Button("↷");
        redoBtn.setTooltip(new Tooltip("Redo"));
        redoBtn.setOnAction(_ -> codeArea.redo());

        Separator separator1 = new Separator();
        separator1.setOrientation(javafx.geometry.Orientation.VERTICAL);
        Separator separator2 = new Separator();
        separator2.setOrientation(javafx.geometry.Orientation.VERTICAL);

        Button runBtn = new Button("Run Parser ▶");
        runBtn.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
        runBtn.setOnAction(_ -> onRunParser());

        toolbar.getChildren().addAll(
                newBtn, openBtn, saveBtn,
                separator1,
                undoBtn, redoBtn, cutBtn, copyBtn, pasteBtn,
                separator2,
                runBtn
        );
        return toolbar;
    }

    /**
     * Executes the compiler pipeline: Lexer -> Parser -> Semantic Analyzer.
     * This method runs in a separate thread to prevent blocking the GUI.
     */
    private void onRunParser() {
        // Clear console on the JavaFX Application Thread
        Platform.runLater(() -> consoleArea.clear());
        String sourceCode = codeArea.getText();

        if (sourceCode.trim().isEmpty()) {
            consoleArea.setText("No code to process.");
            return;
        }

        new Thread(() -> {
            try {
                System.out.println("--- STEP 1: SCANNING ---");

                // STEP 1: Lexical Analysis
                List<Token> tokens = ImLang.scan(sourceCode);

                // Print Token Table
                StringBuilder table = new StringBuilder();
                table.append(String.format("%-20s | %-20s | %s\n", "TYPE", "LEXEME", "LINE"));
                table.append("------------------------------------------------------------\n");

                boolean hasLexError = false;
                StringBuilder commaSeparatedList = new StringBuilder("Token List (Comma-separated): ");

                for (Token t : tokens) {
                    table.append(String.format("%-20s | %-20s | %d\n", t.type(), t.lexeme(), t.line()));
                    if (t.type() == TokenType.ERROR) hasLexError = true;
                    commaSeparatedList.append(t.type().name()).append(", ");
                }

                if (!tokens.isEmpty()) {
                    commaSeparatedList.setLength(commaSeparatedList.length() - 2);
                }

                System.out.println(commaSeparatedList);
                System.out.println(table);

                if (hasLexError) {
                    System.err.println("\n[!] Scanning Failed. Fix errors above.");
                    Platform.runLater(() -> {
                        lblStatus.setText("Status: Lexical Error");
                        lblStatus.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                    });
                    return;
                }

                System.out.println("\n--- STEP 2: PARSING (LL(1) with Line Numbers) ---");

                // STEP 2: Parsing
                boolean parsed = ImLangParser.parse(tokens);

                if (parsed) {
                    // Only run Semantic Analysis if the Syntax is valid
                    System.out.println("\n--- STEP 3: SEMANTIC ANALYSIS ---");

                    // Call the Semantic Analyzer
                    List<String> semanticErrors = ImLangSemanticAnalyzer.analyze(tokens);

                    // Print the Symbol Table
                    System.out.println(ImLangSemanticAnalyzer.getSymbolTableOutput());

                    Platform.runLater(() -> {
                        if (semanticErrors.isEmpty()) {
                            System.out.println(">>> SUCCESS: No Semantic Errors.");
                            lblStatus.setText("Status: Valid Syntax & Semantics");
                            lblStatus.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                        } else {
                            System.err.println(">>> SEMANTIC ERRORS FOUND (" + semanticErrors.size() + "):");
                            for (String error : semanticErrors) {
                                System.err.println(error);
                            }
                            lblStatus.setText("Status: Semantic Error");
                            lblStatus.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                        }
                    });

                } else {
                    Platform.runLater(() -> {
                        lblStatus.setText("Status: Syntax Error");
                        lblStatus.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                    });
                }

            } catch (Exception e) {
                System.err.println("CRITICAL ERROR: " + e.getMessage());
                e.printStackTrace();
            } finally {
                // Make sure any buffered text is flushed to the console
                if (consoleStream != null) {
                    consoleStream.flushBuffer();
                }
            }
        }).start();
    }

    /**
     * Handles the "New" file action. Clears the editor if there are no unsaved changes,
     * or prompts the user to save first. Resets the current file and dirty state.
     *
     * @param stage The primary stage, used for checking unsaved changes.
     */
    private void onNewFile(Stage stage) {
        if (checkUnsavedChanges(stage)) {
            codeArea.clear();
            currentFile = null;
            setIsDirty(false);
            consoleArea.setText("New document started.");
        }
    }

    /**
     * Handles the "Open" file action. Prompts the user to save changes
     * if the current file is dirty. Opens a file chooser dialog and, if a file
     * is selected, reads its contents into the editor.
     *
     * @param stage The primary stage, used to show the file chooser
     * and check for unsaved changes.
     */
    private void onOpenFile(Stage stage) {
        if (!checkUnsavedChanges(stage)) return;
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open ImLang Source File");
        File file = fileChooser.showOpenDialog(stage);
        if (file != null) {
            try {
                codeArea.setText(Files.readString(file.toPath()));
                currentFile = file;
                setIsDirty(false);
                consoleArea.setText("File loaded: " + file.getName());
            } catch (Exception ex) {
                consoleArea.setText("Error loading file: " + ex.getMessage());
            }
        }
    }

    /**
     * Handles the "Save" file action. If currentFile is null (it's a new file),
     * it opens a "Save As" file chooser. Otherwise, it calls performSave()
     * on the current file.
     *
     * @param stage The primary stage, used to show the file chooser.
     */
    private void onSaveFile(Stage stage) {
        if (currentFile == null) {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setInitialFileName("Untitled.imlang");
            File file = fileChooser.showSaveDialog(stage);
            if (file != null) {
                currentFile = file;
                performSave(currentFile);
            }
        } else {
            performSave(currentFile);
        }
    }

    /**
     * Writes the current content of the @code codeArea to the
     * specified file on the disk. If successful, resets the dirty flag
     * and updates the console.
     *
     * @param file The {@code File} object representing the target file location.
     */
    private void performSave(File file) {
        try {
            Files.writeString(file.toPath(), codeArea.getText());
            setIsDirty(false);
            consoleArea.setText("Saved: " + file.getName());
        } catch (Exception ex) {
            consoleArea.setText("Error saving: " + ex.getMessage());
        }
    }

    /**
     * Checks if the editor has unsaved changes. If so, it presents a confirmation dialog
     * to the user asking whether to save the changes, discard them, or cancel the action.
     *
     * @param stage The stage, used to invoke the file save dialog if necessary.
     * @return true if the current operation can proceed (changes saved or discarded),
     * false if the operation was cancelled.
     */
    private boolean checkUnsavedChanges(Stage stage) {
        if (!isDirty) return true;
        Alert alert = new Alert(AlertType.CONFIRMATION, "Save changes?",
                ButtonType.YES, ButtonType.NO, ButtonType.CANCEL);
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            onSaveFile(stage);
            return !isDirty; // Return true only if the save was successful
        }
        return result.isPresent() && result.get() == ButtonType.NO;
    }

}
