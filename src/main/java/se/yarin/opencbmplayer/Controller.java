package se.yarin.opencbmplayer;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Transform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import yarin.cbhlib.*;
import yarin.cbhlib.Date;
import yarin.cbhlib.annotations.*;
import yarin.cbhlib.exceptions.CBHException;
import yarin.cbhlib.exceptions.CBHFormatException;
import yarin.chess.GamePosition;
import yarin.chess.Move;
import yarin.chess.Piece;

import java.io.IOException;
import java.net.URL;
import java.util.*;

public class Controller implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(Controller.class);

    private static Image chessImages = new Image("/images/pieces.png");
    private static Image boardBackground = new Image("/images/wooden-background.jpg");

    private final double BOARD_EDGE_SIZE = 0.15; // Size of board edge relative to the size of a square
    private final double MOVE_BOX_RIGHT_MARGIN = 25; // Compensate for the padding and some extra space to be safe

    private final double GRAPHICAL_ARROW_OPACITY = 0.6;
    private final double GRAPHICAL_SQUARE_OPACITY = 0.4;
    private final int GRAPHICAL_COLOR_INTENSITY = 220;

    private double squareSize, boardSize, xMargin, yMargin, edgeSize;

    @FXML
    private Canvas board;

    @FXML
    private TilePane leftPane;

    @FXML
    private ScrollPane movePane;

    @FXML
    private SplitPane leftSplitter;

    @FXML
    private SplitPane rightSplitter;

    @FXML
    private VBox moveBox;

    @FXML
    private VBox notationBox;

    @FXML
    private TextFlow playerNames;

    @FXML
    private TextFlow gameDetails;


    private GameHeader gameHeader;
    private AnnotatedGame game;
    private GamePosition gameCursor;
    private Map<GamePosition, MoveLabel> positionLabelMap = new HashMap<>();
    private boolean gameHasVariations; // TODO: This should be in the game model

    public Controller() {
    }

    private void initBoardSize() {
        double w = board.getWidth(), h = board.getHeight();
        squareSize = Math.min(w, h) / (8 + BOARD_EDGE_SIZE * 2);
        boardSize = (8 + BOARD_EDGE_SIZE * 2) * squareSize;
        xMargin = (w - boardSize) / 2;
        yMargin = (h - boardSize) / 2;
        edgeSize = BOARD_EDGE_SIZE * squareSize;
    }

    private Point2D getSquareMidpoint(int x, int y) {
        return new Point2D(
                xMargin + squareSize * (x + BOARD_EDGE_SIZE) + squareSize / 2,
                yMargin + squareSize * (7 - y + BOARD_EDGE_SIZE) + squareSize / 2);
    }

    private Rectangle getSquareRect(int x, int y) {
        return new Rectangle(
                xMargin + squareSize * (x + BOARD_EDGE_SIZE),
                yMargin + squareSize * (7 - y + BOARD_EDGE_SIZE),
                squareSize,
                squareSize);
    }


    private void drawBoard() {
        log.debug("starting to draw board");
        long start = System.currentTimeMillis();
//        log.debug("rightSplitter width: " + rightSplitter.getWidth());
//        log.debug("movePane width: " + movePane.getWidth());
//        log.debug("moveBox width: " + moveBox.getWidth());

        GraphicsContext gc = board.getGraphicsContext2D();

        // Overwrite entire canvas is necessary when resizing
        board.getParent().setStyle("-fx-background-color: darkgray;");
        gc.setFill(Color.DARKGRAY);
        gc.fillRect(0, 0, board.getWidth(), board.getHeight());

        initBoardSize();

//        gc.drawImage(boardBackground, 0, 0, 2000, 1500, xMargin, yMargin, boardSize, boardSize);
        gc.drawImage(boardBackground, 0, 0, 3800, 2900, xMargin, yMargin, boardSize, boardSize);

        gc.setFill(Color.rgb(128, 0, 0, 0.5));
        gc.fillRect(xMargin, yMargin, boardSize, edgeSize);
        gc.fillRect(xMargin, yMargin + boardSize - edgeSize, boardSize, edgeSize);
        gc.fillRect(xMargin, yMargin + edgeSize, edgeSize, boardSize - edgeSize * 2);
        gc.fillRect(xMargin + boardSize - edgeSize , yMargin + edgeSize, edgeSize, boardSize - edgeSize * 2);
        gc.setLineWidth(1.0);
        gc.setStroke(Color.rgb(64, 0, 0, 0.3));
        gc.strokeRect(xMargin, yMargin, boardSize, boardSize);
        gc.strokeRect(xMargin + edgeSize , yMargin + edgeSize, boardSize - 2 * edgeSize, boardSize - 2 * edgeSize);

        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                Rectangle sq = getSquareRect(x, y);
                if ((x+y)%2 == 0) {
                    gc.setFill(Color.rgb(139, 69, 19, 0.6));
//                    gc.setFill(Color.rgb(160, 82, 45, 0.6));
                    gc.fillRect(sq.getX(), sq.getY(), sq.getWidth(), sq.getHeight());
                }
            }
        }

        drawGraphicalSquares(gc);

        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                Piece piece = gameCursor.getPosition().pieceAt(y, x);
                drawPiece(gc, x, y, piece);
            }
        }

        drawGraphicalArrows(gc);

        long stop = System.currentTimeMillis();
        log.debug("done in " + (stop-start) + " ms");
    }

    private void drawGraphicalSquares(GraphicsContext gc) {
        GraphicalSquaresAnnotation gsa = game.getAnnotation(gameCursor, GraphicalSquaresAnnotation.class);
        if (gsa == null) {
            return;
        }
        for (GraphicalSquaresAnnotation.GraphicalSquare gsq : gsa.getSquares()) {
            int xy = gsq.getSquare();
            int x = xy / 8, y = xy % 8;
            Paint p;
            switch (gsq.getColor()) {
                case GREEN:
                    p = Color.rgb(255-GRAPHICAL_COLOR_INTENSITY, GRAPHICAL_COLOR_INTENSITY, 255-GRAPHICAL_COLOR_INTENSITY, GRAPHICAL_SQUARE_OPACITY);
                    break;
                case YELLOW:
                    p = Color.rgb(GRAPHICAL_COLOR_INTENSITY, GRAPHICAL_COLOR_INTENSITY, 255-GRAPHICAL_COLOR_INTENSITY, GRAPHICAL_SQUARE_OPACITY);
                    break;
                case RED:
                    p = Color.rgb(GRAPHICAL_COLOR_INTENSITY, 255-GRAPHICAL_COLOR_INTENSITY, 255-GRAPHICAL_COLOR_INTENSITY, GRAPHICAL_SQUARE_OPACITY);
                    break;
                default:
                    continue;
            }
            gc.setFill(p);
            Rectangle sq = getSquareRect(x, y);
            gc.fillRect(sq.getX(), sq.getY(), sq.getWidth(), sq.getHeight());
        }
    }

    private void drawGraphicalArrows(GraphicsContext gc) {
        GraphicalArrowsAnnotation gsa = game.getAnnotation(gameCursor, GraphicalArrowsAnnotation.class);
        if (gsa == null) {
            return;
        }
        for (GraphicalArrowsAnnotation.GraphicalArrow ga : gsa.getArrows()) {
            int src = ga.getFromSquare(), dest = ga.getToSquare();
            int x1 = src / 8, y1 = src % 8, x2 = dest / 8, y2 = dest % 8;
            Paint p;
            switch (ga.getColor()) {
                case GREEN:
                    p = Color.rgb(255-GRAPHICAL_COLOR_INTENSITY, GRAPHICAL_COLOR_INTENSITY, 255-GRAPHICAL_COLOR_INTENSITY, GRAPHICAL_ARROW_OPACITY);
                    break;
                case YELLOW:
                    p = Color.rgb(GRAPHICAL_COLOR_INTENSITY, GRAPHICAL_COLOR_INTENSITY, 255-GRAPHICAL_COLOR_INTENSITY, GRAPHICAL_ARROW_OPACITY);
                    break;
                case RED:
                    p = Color.rgb(GRAPHICAL_COLOR_INTENSITY, 255-GRAPHICAL_COLOR_INTENSITY, 255-GRAPHICAL_COLOR_INTENSITY, GRAPHICAL_ARROW_OPACITY);
                    break;
                default:
                    continue;
            }
            gc.setFill(p);
            Point2D p1 = getSquareMidpoint(x1, y1);
            Point2D p2 = getSquareMidpoint(x2, y2);
            drawArrow(gc, p1.getX(), p1.getY(), p2.getX(), p2.getY(), p);
        }
    }

    void drawArrow(GraphicsContext gc, double x1, double y1, double x2, double y2, Paint color) {
        gc.setFill(color);

        double dx = x2 - x1, dy = y2 - y1;
        double angle = Math.atan2(dy, dx);
        double len = Math.sqrt(dx * dx + dy * dy);

        Transform transform = Transform.translate(x1, y1);
        transform = transform.createConcatenation(Transform.rotate(Math.toDegrees(angle), 0, 0));
        gc.setTransform(new Affine(transform));

        double arrowStart = squareSize * 0.05;
        double arrowLength = len - squareSize * 0.2;
        double arrowHeadWidth  = squareSize * 0.22;
        double arrowHeadHeight = squareSize * 0.45;
        double arrowHeadHeight2 = arrowHeadHeight * 0.8;
        double arrowWidth = squareSize * 0.05;
        gc.fillPolygon(
                new double[] {arrowLength, arrowLength - arrowHeadHeight, arrowLength - arrowHeadHeight2, arrowStart, arrowStart, arrowLength - arrowHeadHeight2, arrowLength-arrowHeadHeight, arrowLength},
                new double[] {0, -arrowHeadWidth, -arrowWidth, -arrowWidth, arrowWidth, arrowWidth, arrowHeadWidth, 0},
                8);
        gc.setTransform(new Affine());
    }

    private void drawPiece(GraphicsContext gc, int x, int y, Piece piece) {
        if (piece.isEmpty()) return;

        int sx = 0, sy = piece.getColor() == Piece.PieceColor.WHITE ? 0 : 1;

        switch (piece.getPiece()) {
            case PAWN:   sx = 5; break;
            case KNIGHT: sx = 1; break;
            case BISHOP: sx = 2; break;
            case ROOK:   sx = 0; break;
            case QUEEN:  sx = 3; break;
            case KING:   sx = 4; break;
        }

        Rectangle sq = getSquareRect(x, y);

        gc.drawImage(chessImages, sx * 132, sy * 132, 132, 132,
                sq.getX(), sq.getY(), sq.getWidth(), sq.getHeight());
    }

    private double currentRowWidth;
    private HBox currentRow;
    private boolean pullDownLastIfEOL;
    private HashMap<String, Double> labelWidthCache = new HashMap<>(); // TODO: Make real cache

    private void addNewRow(int level) {
        currentRow = new HBox();
        currentRowWidth = 16 * level;
        currentRow.setPadding(new Insets(2, 0, 2, currentRowWidth));
        moveBox.getChildren().add(currentRow);
//        log.debug("new row");
    }

    // Determines the width of a label based ONLY on the text and it's styleclasses (not padding)
    private double getLabelWidth(Label label, String... extraStyleClasses) {
        Text text = new Text(label.getText());
        text.getStyleClass().addAll(label.getStyleClass());
        text.getStyleClass().addAll(extraStyleClasses);

        String cacheId = text.getStyleClass() + "#" + label.getText();
        if (labelWidthCache.containsKey(cacheId)) {
            return labelWidthCache.get(cacheId);
        }

        HBox hbox = new HBox(text);
        hbox.setId("moveBox");
        Scene scene = new Scene(hbox);
        scene.getStylesheets().add("/styles/styles.css");
//        log.info("without css: " + text.getLayoutBounds().getWidth());
        text.applyCss();
//        log.info("with css: " + text.getLayoutBounds().getWidth());
        double width = text.getLayoutBounds().getWidth();
        labelWidthCache.put(cacheId, width);
        return width;
    }

    private void addImage(Image image) {
        // TODO: This is ugly (and not correct), should be same method as addControl if possible to avoid code duplication.

        double width = image.getWidth();

        currentRow.getChildren().add(new ImageView(image));
        currentRowWidth += width;
    }

    private void addControl(Control node, int level, double leftPadding, double rightPadding, String... styleClass) {
        node.getStyleClass().addAll(styleClass);

        if (!(node instanceof Label)) throw new RuntimeException("Not supported yet");
        double width = getLabelWidth((Label) node);
        boolean singleCharacter = ((Label) node).getText().length() == 1;
//        log.debug(((Label) control).getText() + " " + leftPadding + " " + currentRow.getChildren().size());

//        log.debug("currentRowWidth = " + currentRowWidth + ", controlWidth = " + width + ", moveBox width = " + moveBox.getWidth());
        // Force single characters to be on same line; we give enough margin to make this possible
        if (!singleCharacter && currentRowWidth + width + leftPadding + rightPadding >
                moveBox.getWidth() - MOVE_BOX_RIGHT_MARGIN) {
            Node last = null;
            if (pullDownLastIfEOL) {
                int lastIndex = currentRow.getChildren().size() - 1;
                last = currentRow.getChildren().get(lastIndex);
                currentRow.getChildren().remove(lastIndex);
            }
            addNewRow(level);
            if (last != null) {
                currentRow.getChildren().add(last);
                if (last instanceof Label) {
                    currentRowWidth += getLabelWidth((Label) last);
                }
            }
        }

        pullDownLastIfEOL = false;

        if (currentRow.getChildren().size() == 0) {
            leftPadding = 0;
        }

        node.setPadding(new Insets(0, rightPadding, 0, leftPadding));
        currentRow.getChildren().add(node);
        currentRowWidth += width + leftPadding + rightPadding;
    }

    private boolean fitsOnRow(String text, double leftPadding, double rightPadding, String... styleClass) {
        Label label = new Label(text);
        double width = getLabelWidth(label, styleClass);
        return currentRowWidth + width + leftPadding + rightPadding <= moveBox.getWidth() - MOVE_BOX_RIGHT_MARGIN;
    }

    private void addText(String text, int level, double leftPadding, double rightPadding, String... styleClass) {
        if (text.trim().length() == 0) return;

        // Check if the entire text fits on the row
        if (fitsOnRow(text, leftPadding, rightPadding, styleClass)) {
            addControl(new Label(text), level, leftPadding, rightPadding, styleClass);
        } else {
//            log.debug("Doesn't fit: " + text);
            // Check if we can split it
            int cur = text.indexOf(' ');
            if (cur < 0) {
                // If there are no spaces, we can't do so much
                addControl(new Label(text), level, leftPadding, rightPadding, styleClass);
            } else {
                int best = cur; // Always take the first word, even if it's too long (can't do better)
                while (cur != -1 && fitsOnRow(text.substring(0, cur), leftPadding, 0, styleClass)) {
                    best = cur;
                    cur = text.indexOf(' ', cur + 1);
                }
//                log.debug("DID FIT: " + text.substring(0, best));
                addControl(new Label(text.substring(0, best)), level, leftPadding, 0, styleClass);
                addNewRow(level);
                // Recursively split the next part, if needed
                addText(text.substring(best + 1), level, 0, rightPadding, styleClass);
            }
        }
    }

    private void addText(String text, int level, String... styleClass) {
        addText(text, level, 0.0, 0.0, styleClass);
    }

    private void addPreMoveAnnotations(GamePosition node, int level) {
        // TODO: This won't work properly in case of multiple languages
        TextBeforeMoveAnnotation beforeMoveAnnotation = game.getAnnotation(node, TextBeforeMoveAnnotation.class);
        if (beforeMoveAnnotation != null) {
            addText(beforeMoveAnnotation.getText(), level, "comment-label");
        }
    }

    private void addPostMoveAnnotations(GamePosition node, int level) {
        boolean hasGraphicalAnnotations = game.getAnnotation(node, GraphicalArrowsAnnotation.class) != null ||
                game.getAnnotation(node, GraphicalSquaresAnnotation.class) != null;
        if (hasGraphicalAnnotations) {
            addImage(new Image("images/graphical-annotation.png", 16, 16, true, true));
        }

        // TODO: This won't work properly in case of multiple languages
        TextAfterMoveAnnotation afterMoveAnnotation = game.getAnnotation(node, TextAfterMoveAnnotation.class);
        if (afterMoveAnnotation != null) {
            addText(afterMoveAnnotation.getText(), level, "comment-label");
        }
    }

    /**
     * Output the last move made with annotations
     * @param node the position after the last move (can be the start position)
     */
    private void addMove(GamePosition node,
                         boolean showMoveNumber,
                         int level,
                         boolean inlineVariation,
                         boolean headOfVariation) {
        Move move = node.getLastMove();
        addPreMoveAnnotations(node, level);

        // Move is null if node is the start position of the game
        if (move != null) {
            // This assumes there can only be on symbol annotation per move
            SymbolAnnotation symbols = game.getAnnotation(node, SymbolAnnotation.class);
            MovePrefix movePrefix = symbols == null ? MovePrefix.Nothing : symbols.getMovePrefix();
            MoveComment moveComment = symbols == null ? MoveComment.Nothing : symbols.getMoveComment();
            LineEvaluation lineEvaluation = symbols == null ? LineEvaluation.NoEvaluation : symbols.getPositionEval();

            CriticalPositionAnnotation criticalPosition = game.getAnnotation(node, CriticalPositionAnnotation.class);

            // Add move, symbols and move number
            MoveLabel lbl = new MoveLabel(move, node);
            String moveText = movePrefix.getSymbol();
            Piece.PieceColor moveColor = node.getBackPosition().getPlayerToMove();
            if (showMoveNumber || moveColor == Piece.PieceColor.WHITE) {
                moveText += String.format("%d.", node.getBackPosition().getMoveNumber());
                if (moveColor == Piece.PieceColor.BLACK) moveText += "..";
            }

            moveText += move.toString(node.getBackPosition().getPosition());
            moveText += moveComment.getSymbol();
            moveText += lineEvaluation.getSymbol();

            lbl.setText(moveText);
            lbl.setOnMouseClicked(Controller.this::handleMoveSelected);
            List<String> styles = new ArrayList<>();
            double leftPadding = 4, rightPadding = 4;
            if (headOfVariation && game.getAnnotation(node, TextBeforeMoveAnnotation.class) == null) {
                leftPadding = 0;
            }
            if (node.isEndOfVariation() && game.getAnnotation(node, TextAfterMoveAnnotation.class) == null) {
                rightPadding = 0;
            }
            if (level == 0 && this.gameHasVariations) {
                styles.add("main-line");
            } else if (inlineVariation) {
                styles.add("last-line");
            }
            if (headOfVariation && !inlineVariation && level > 1) {
                styles.add("variation-head");
                leftPadding = 4;
            }
            if (criticalPosition != null) {
                // TODO: These styles don't look as nice when the move is the selected move
                switch (criticalPosition.getType()) {
                    case OPENING:
                        styles.add("critical-opening-position");
                        break;
                    case MIDDLEGAME:
                        styles.add("critical-middlegame-position");
                        break;
                    case ENDGAME:
                        styles.add("critical-endgame-position");
                        break;
                }
            }

            lbl.getStyleClass().addAll(styles);
            positionLabelMap.put(node, lbl);
            addControl(lbl, level, leftPadding, rightPadding);
        }

        addPostMoveAnnotations(node, level);
    }

    private boolean allVariationsAreSingleLine(GamePosition position) {
        return position.getMoves()
                .stream()
                .skip(1) // Skip the main variation
                .allMatch(move -> position.getForwardPosition(move).isSingleLine());
    }

    private void generateMoveControls(GamePosition position, boolean showMoveNumber,
                                      int level, boolean inlineVariation, String linePrefix) {
        // TODO: Try and make this cleaner by using TextFlow, so we don't have to calculate the width of everything manually
        if (position.getLastMove() != null) {
            addMove(position, showMoveNumber, level, inlineVariation, true);
            showMoveNumber = false;
        }

        while (!position.isEndOfVariation()) {
            List<Move> moves = position.getMoves();
            if (moves.size() == 1) {
                addMove(position.getForwardPosition(), showMoveNumber, level, inlineVariation, false);
                showMoveNumber = false;
            } else {
                if (inlineVariation) throw new RuntimeException("Found variations in an inline variation");
                if (level == 0) {
                    // Show main move on existing line, but then one new paragraph per sub-line,
                    // each paragraph starting with [ and ending with ]
                    addMove(position.getForwardPosition(), showMoveNumber, level, false, false);

                    for (int i = 1; i < moves.size(); i++) {
                        addNewRow(level + 1);
                        addText("[", level + 1);
                        Move subMove = moves.get(i);
                        generateMoveControls(position.getForwardPosition(subMove), true, level + 1, false, linePrefix);
                        addText("]", level + 1);
                    }
                    addNewRow(level);
                    showMoveNumber = true;
                } else if (allVariationsAreSingleLine(position)) {
                    // Show the alternatives inline, within () and separated by ;
                    addMove(position.getForwardPosition(), showMoveNumber, level, false, false);

                    addText("(", level, 6.0, 0.0, "last-line");
                    pullDownLastIfEOL = true;
                    for (int i = 1; i < moves.size(); i++) {
                        if (i > 1) addText(";", level, 0.0, 3.0, "last-line");
                        generateMoveControls(position.getForwardPosition(moves.get(i)), true, level, true, linePrefix);
                    }
                    addText(")", level, "last-line");
                    showMoveNumber = true;
                } else {
                    // Subvariations are marked with letters and digits alternatively for each level, e.g. "B1c"
                    // The order is: [A-Z], [1-9], [a-z], [1-9], [1-9] and repeat digits
                    // But replace [1-9] with [a-z] if there are 10 or more lines at that level
                    // It won't look pretty if there are more than 26 lines, but CB has the same issue
                    char startChar = '1';
                    if (level == 1) startChar = 'A';
                    if (level == 3) startChar = 'a';
                    if (startChar == '1' && moves.size() >= 10) startChar = 'a';

                    for (int i = 0; i < moves.size(); i++) {
                        if (i > 0) addText(";", level + 1);
                        addNewRow(level + 1);

                        String newLinePrefix = linePrefix + (char) (startChar+i);
                        addText(String.format("%s)", newLinePrefix), level + 1, "variation-name");
                        // The main line goes last
                        Move subMove = moves.get((i+1) % moves.size());
                        generateMoveControls(position.getForwardPosition(subMove), true, level + 1, false, newLinePrefix);

                    }
                    break;
                }
            }
            position = position.getForwardPosition();
        }
    }

    private void drawMoves() {
        if (moveBox.getWidth() == 0) {
            log.info("Can't generate moves because moveBox width is not known");
            return;
        }
        log.debug("starting to generate move controls");
        long start = System.currentTimeMillis();
        positionLabelMap = new HashMap<>();
        moveBox.getChildren().clear();
        addNewRow(0);

        addMove(game, false, 0, false, false);
        generateMoveControls(game, true, 0, false, "");

        addNewRow(0);
        if (gameHeader.getResult() != GameResult.Line && gameHeader.getResult() != GameResult.BothLost) {
            addText(gameHeader.getResultString(), 0, "main-line");
        }

        if (gameCursor != null) {
            selectPosition(gameCursor);
        }

        long stop = System.currentTimeMillis();
        log.debug("done in " + (stop-start) + " ms");
    }

    private void drawGameHeader() {
        drawGameHeaderFirstRow();
        drawGameHeaderSecondRow();
    }

    private void drawGameHeaderFirstRow() {
        playerNames.getChildren().clear();
        try {
            String white = gameHeader.getWhitePlayerString();
            String black = gameHeader.getBlackPlayerString();
            int whiteRating = gameHeader.getWhiteElo();
            int blackRating = gameHeader.getBlackElo();
            String result;

            switch (gameHeader.getResult()) {
                case WhiteWon:
                case WhiteWonOnForfeit:
                    result = "1-0";
                    break;
                case Draw:
                case DrawOnForfeit:
                    result = "½-½";
                    break;
                case BlackWon:
                case BlackWonOnForfeit:
                    result = "0-1";
                    break;
                default:
                    result = "";
            }

            if (white.length() > 0) {
                Text txtWhite = new Text(white);
                txtWhite.getStyleClass().add("player-name");
                playerNames.getChildren().add(txtWhite);
                if (whiteRating > 0) {
                    txtWhite = new Text(" " + whiteRating);
                    txtWhite.getStyleClass().add("player-rating");
                    playerNames.getChildren().add(txtWhite);
                }
            }

            if (white.length() > 0 && black.length() > 0) {
                Text txtVs = new Text(" - ");
                txtVs.getStyleClass().add("player-name");
                playerNames.getChildren().add(txtVs);
            }

            if (black.length() > 0) {
                Text txtBlack = new Text(black);
                txtBlack.getStyleClass().add("player-name");
                playerNames.getChildren().add(txtBlack);
                if (blackRating > 0) {
                    txtBlack = new Text(" " + blackRating);
                    txtBlack.getStyleClass().add("player-rating");
                    playerNames.getChildren().add(txtBlack);
                }
            }

            if (result.length() > 0) {
                Text txtResult = new Text(" " + result);
                txtResult.getStyleClass().add("game-result");
                playerNames.getChildren().add(txtResult);
            }
        } catch (IOException e) {
            Text txtError = new Text("Failed to load player data");
            txtError.getStyleClass().add("load-error");
            playerNames.getChildren().add(txtError);
        }
    }

    private void drawGameHeaderSecondRow() {
        gameDetails.getChildren().clear();

        try {
            String eco = gameHeader.getECO();
            String tournament = gameHeader.getTournamentString();
            String annotator = gameHeader.getAnnotatorString();
            int round = gameHeader.getRound();
            int subRound = gameHeader.getSubRound();
            Date playedDate = gameHeader.getPlayedDate();
            String whiteTeam = gameHeader.getWhiteTeamString();
            String blackTeam = gameHeader.getBlackTeamString();

            if (eco.length() > 0) {
                Text txtECO = new Text(eco + " ");
                txtECO.getStyleClass().add("eco");
                gameDetails.getChildren().add(txtECO);
            }
            if (tournament.length() > 0) {
                Text txtTournament = new Text(tournament + " ");
                txtTournament.getStyleClass().add("tournament");
                gameDetails.getChildren().add(txtTournament);
            }
            if (whiteTeam.length() > 0 && blackTeam.length() > 0) {
                Text txtTeams = new Text(String.format("[%s-%s] ", whiteTeam, blackTeam));
                txtTeams.getStyleClass().add("team");
                gameDetails.getChildren().add(txtTeams);
            }
            if (round > 0 || subRound > 0) {
                String roundString;
                if (round > 0 && subRound > 0) {
                    roundString = String.format("(%d.%d)", round, subRound);
                } else if (round > 0) {
                    roundString = String.format("(%d)", round);
                } else {
                    roundString = String.format("(%d)", subRound);
                }
                Text txtRound = new Text(roundString + " ");
                txtRound.getStyleClass().add("round");
                gameDetails.getChildren().add(txtRound);
            }
            if (playedDate.toString().length() > 0) {
                Text txtDate = new Text(playedDate.toString() + " ");
                txtDate.getStyleClass().add("date");
                gameDetails.getChildren().add(txtDate);
            }
            if (annotator.length() > 0) {
                Text txtAnnotator = new Text(String.format("[%s]", annotator));
                txtAnnotator.getStyleClass().add("annotator");
                gameDetails.getChildren().add(txtAnnotator);
            }
        } catch (IOException | CBHException e) {
            Text txtError = new Text("Failed to game details data");
            txtError.getStyleClass().add("load-error");
            gameDetails.getChildren().add(txtError);
        }
    }

    private void selectPosition(GamePosition position) {
        if (gameCursor != null) {
            // Deselect previous selection
            MoveLabel moveLabel = positionLabelMap.get(gameCursor);
            if (moveLabel != null) {
                moveLabel.getStyleClass().remove("selected-move");
            }
        }

        gameCursor = position;

        // Highlight the selected position
        MoveLabel moveLabel = positionLabelMap.get(gameCursor);
        if (moveLabel != null) {
            moveLabel.getStyleClass().add("selected-move");
        }

        drawBoard();
    }

    private void handleMoveSelected(MouseEvent mouseEvent) {
        MoveLabel source = (MoveLabel) mouseEvent.getSource();
        log.debug("Clicked on " + source.getMove() + ", node " + source.getNode());
        selectPosition(source.getNode());
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        reloadGame(null);

        board.widthProperty().bind(leftPane.widthProperty().subtract(20));
        board.heightProperty().bind(leftPane.heightProperty().subtract(20));

        movePane.prefWidthProperty().bind(rightSplitter.widthProperty());
        movePane.prefHeightProperty().bind(rightSplitter.heightProperty());
        moveBox.prefWidthProperty().bind(movePane.widthProperty().subtract(20)); // Compensate for vertical scrollbar
        //notationBox.prefWidthProperty().bind(rightSplitter.widthProperty());

        moveBox.widthProperty().addListener(observable -> drawMoves());

        board.widthProperty().addListener(observable -> drawBoard());
        board.heightProperty().addListener(observable -> drawBoard());

    }

    public void reloadGame(ActionEvent actionEvent) {
        //        String cbhFile = "/Users/yarin/Dropbox/ChessBase/My Games/My White Openings.cbh";
//        String cbhFile = "/Users/yarin/Dropbox/ChessBase/My Games/jimmy.cbh";
//        String cbhFile = "/Users/yarin/src/cbhlib/src/test/java/yarin/cbhlib/databases/cbhlib_test.cbh";
        String cbhFile = "/Users/yarin/src/opencbmplayer/src/main/resources/cbmplayertest.cbh";

        try {
            Database db = Database.open(cbhFile);
            this.gameHeader = db.getGameHeader(8);
            this.game = this.gameHeader.getGame();
            this.gameCursor = this.game;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load the game", e);
        } catch (CBHFormatException e) {
            throw new RuntimeException("Failed to load the game", e);
        } catch (CBHException e) {
            throw new RuntimeException("Failed to load the game", e);
        }

        // Figure out if the game has any variations at all
        GamePosition cur = this.game;
        this.gameHasVariations = false;

        while (!cur.isEndOfVariation() && !this.gameHasVariations) {
            if (cur.getMoves().size() > 1) {
                this.gameHasVariations = true;
            }
            cur = cur.getForwardPosition();
        }

        drawGameHeader();
        drawMoves();
        drawBoard();
    }
}
