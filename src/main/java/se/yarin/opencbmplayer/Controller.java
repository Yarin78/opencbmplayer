package se.yarin.opencbmplayer;

import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import yarin.cbhlib.AnnotatedGame;
import yarin.cbhlib.Database;
import yarin.cbhlib.GameHeader;
import yarin.cbhlib.annotations.Annotation;
import yarin.cbhlib.exceptions.CBHException;
import yarin.cbhlib.exceptions.CBHFormatException;
import yarin.chess.GamePosition;
import yarin.chess.Move;
import yarin.chess.Piece;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class Controller implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(Controller.class);

    private static Image chessImages = new Image("/images/pieces.png");
    private static Image boardBackground = new Image("/images/wooden-background.jpg");

    private final double BOARD_EDGE_SIZE = 0.15; // Size of board edge relative to the size of a square
    private double squareSize, boardSize, xMargin, yMargin, edgeSize;

    @FXML
    private Canvas board;

    @FXML
    private TilePane leftPane;

//    @FXML
//    private FlowPane moveBox;

    @FXML
    private ScrollPane movePane;

    @FXML
    private SplitPane rightSplitter;

    @FXML
    private VBox moveBox2;

    private AnnotatedGame game;
    private GamePosition gameCursor;

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

    private Rectangle getSquareRect(int x, int y) {
        return new Rectangle(
                xMargin + squareSize * (x + BOARD_EDGE_SIZE),
                yMargin + squareSize * (7 - y + BOARD_EDGE_SIZE),
                squareSize,
                squareSize);
    }


    private void drawBoard() {
//        log.debug("drawing board at node " + game.getCurNode());
//        log.debug("rightSplitter width = " + rightSplitter.getWidth() + ", moveBox width " + moveBox.getWidth());
//        log.debug("rightSplitter height " + rightSplitter.getHeight());

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
                if ((x+y)%2 == 0) {
                    Rectangle sq = getSquareRect(x, y);
                    gc.setFill(Color.rgb(139, 69, 19, 0.6));
//                    gc.setFill(Color.rgb(160, 82, 45, 0.6));
                    gc.fillRect(sq.getX(), sq.getY(), sq.getWidth(), sq.getHeight());
                }
                //gc.setGlobalBlendMode(BlendMode.ADD);

                Piece piece = gameCursor.getPosition().pieceAt(y, x);

                drawPiece(gc, x, y, piece);
            }
        }

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

    private double getLabelLength(Label lbl) {
        Text text = new Text(lbl.getText());
        new Scene(new Group(text));
        text.applyCss();
        return text.getLayoutBounds().getWidth();
    }

    private void addNewMoveLine(int level) {
        HBox moveLine = new HBox();
        moveLine.setPadding(new Insets(0, 0, 0, 8 * level));
        moveBox2.getChildren().add(moveLine);
    }

    private void addMoveBox(GamePosition preNode, Move move, boolean showMoveNumber, int level, boolean inlineVariation) {
        GamePosition postNode = preNode.getForwardPosition(move);
        List<Annotation> annotations = game.getAnnotations(postNode);

        ObservableList<Node> noRows = moveBox2.getChildren();
        if (noRows.size() == 0 || ((HBox) moveBox2.getChildren().get(noRows.size() - 1)).getChildren().size() == 60) {
            addNewMoveLine(level);
        }
        HBox lastRow = (HBox) moveBox2.getChildren().get(noRows.size() - 1);

        // Add pre-move comments
        for (Annotation annotation : annotations) {
            String preText = annotation.getPreText();
            if (preText != null) {
                Label commentLbl = new Label(preText);
                commentLbl.getStyleClass().add("comment-label");
//                log.debug("width " + getLabelLength(commentLbl));
                lastRow.getChildren().add(commentLbl);
            }
        }

        // Add, if needed, move number
        if (showMoveNumber || preNode.getPlayerToMove() == Piece.PieceColor.WHITE) {
            Label moveNoLbl = new Label();
            if (level == 0) {
                moveNoLbl.getStyleClass().add("main-line");
            } else if (inlineVariation) {
                moveNoLbl.getStyleClass().add("last-line");
            }
            moveNoLbl.getStyleClass().add("move-no-label");
            String s = String.format("%d.", preNode.getMoveNumber());
            if (preNode.getPlayerToMove() == Piece.PieceColor.BLACK) s += "..";
            moveNoLbl.setText(s);
            lastRow.getChildren().add(moveNoLbl);
        }

        // Add move
        MoveLabel lbl = new MoveLabel(move, postNode, "move-label");
        lbl.setText(move.toString(preNode.getPosition()));
        lbl.setOnMouseClicked(Controller.this::handleMoveSelected);
        if (level == 0) {
            lbl.getStyleClass().add("main-line");
        } else if (inlineVariation) {
            lbl.getStyleClass().add("last-line");
        }
        lastRow.getChildren().add(lbl);

        // Add post-move comments
        for (Annotation annotation : annotations) {
            String postText = annotation.getPostText();
            if (postText != null) {
                Label commentLbl = new Label(postText);
                commentLbl.getStyleClass().add("comment-label");
//                log.debug("width " + getLabelLength(commentLbl));
                lastRow.getChildren().add(commentLbl);
            }
        }
    }

    private void addDelimiter(String text, int level) {
        ObservableList<Node> noRows = moveBox2.getChildren();
        if (noRows.size() == 0 || ((HBox) moveBox2.getChildren().get(noRows.size() - 1)).getChildren().size() == 60) {
            addNewMoveLine(level);
        }
        HBox lastRow = (HBox) moveBox2.getChildren().get(noRows.size() - 1);

        Label lbl = new Label(text);
        lbl.getStyleClass().add("move-delimiter");
        lastRow.getChildren().add(lbl);
    }

    private boolean allSingleLines(GamePosition position) {
        return position.getMoves().stream().allMatch(move -> position.getForwardPosition(move).isSingleLine());
    }

    private void generateMoveControls(GamePosition position, boolean showMoveNumber, int level, boolean inlineVariation) {
        if (position.getLastMove() != null) {
            addMoveBox(position.getBackPosition(), position.getLastMove(), showMoveNumber, level, inlineVariation);
            showMoveNumber = false;
        }

        while (!position.isEndOfVariation()) {
            List<Move> moves = position.getMoves();
            if (moves.size() == 1) {
                addMoveBox(position, position.getMainMove(), showMoveNumber, level, inlineVariation);
                showMoveNumber = false;
            } else {
                if (inlineVariation) throw new RuntimeException("Found variations in an inline variation");
                if (level == 0) {
                    // Show main move on existing line, but then one new paragraph per sub-line,
                    // each paragraph starting with [ and ending with ]
                    addMoveBox(position, position.getMainMove(), showMoveNumber, level, false);

                    for (int i = 1; i < moves.size(); i++) {
                        addNewMoveLine(level + 1);
                        addDelimiter("[", level + 1);
                        Move subMove = moves.get(i);
                        generateMoveControls(position.getForwardPosition(subMove), true, level + 1, false);
                        addDelimiter("]", level + 1);
                    }
                    addNewMoveLine(level);
                    showMoveNumber = true;
                } else if (allSingleLines(position)) {
                    // Show the alternatives inline, within () and separated by ;
                    addMoveBox(position, position.getMainMove(), showMoveNumber, level, false);

                    addDelimiter("(", level);
                    for (int i = 1; i < moves.size(); i++) {
                        if (i > 1) addDelimiter(";", level);
                        Move subMove = moves.get(i);
                        generateMoveControls(position.getForwardPosition(subMove), true, level, true);
                    }
                    addDelimiter(")", level);
                    showMoveNumber = true;
                } else {
                    // Show subvariation annotated with A,B,C, then e.g. B1, B2, B3
                    for (int i = 0; i < moves.size(); i++) {
                        addNewMoveLine(level + 1);
                        addDelimiter(String.format("%c)", (char) ('A'+i)), level + 1);
                        Move subMove = moves.get(i);
                        generateMoveControls(position.getForwardPosition(subMove), true, level + 1, false);
                    }
                    break;
                }
            }
            position = position.getForwardPosition();
        }
    }

    private void drawMoves() {
        log.debug("starting to generate move controls");
        long start = System.currentTimeMillis();
        generateMoveControls(game, true, 0, false);
        long stop = System.currentTimeMillis();
        log.debug("generate move controls in " + (stop-start) + " ms");
    }

    private void handleMoveSelected(MouseEvent mouseEvent) {
        MoveLabel source = (MoveLabel) mouseEvent.getSource();
        log.debug("Clicked on " + source.getMove() + ", node " + source.getNode());
        gameCursor = source.getNode();
        drawBoard();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
//        String cbhFile = "/Users/yarin/Dropbox/ChessBase/My Games/My White Openings.cbh";
//        String cbhFile = "/Users/yarin/Dropbox/ChessBase/My Games/jimmy.cbh";
        String cbhFile = "/Users/yarin/src/cbhlib/src/test/java/yarin/cbhlib/databases/cbhlib_test.cbh";

        try {
            Database db = Database.open(cbhFile);
            GameHeader gameHeader = db.getGameHeader(6);
            this.game = gameHeader.getGame();
            this.gameCursor = this.game;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load the game", e);
        } catch (CBHFormatException e) {
            throw new RuntimeException("Failed to load the game", e);
        } catch (CBHException e) {
            throw new RuntimeException("Failed to load the game", e);
        }


        drawMoves();

        board.widthProperty().bind(leftPane.widthProperty().subtract(20));
        board.heightProperty().bind(leftPane.heightProperty().subtract(20));
//        moveBox.prefWidthProperty().bind(movePane.widthProperty());

        board.widthProperty().addListener(observable -> drawBoard());
        board.heightProperty().addListener(observable -> drawBoard());
    }

}
