package se.yarin.opencbmplayer;

import chesspresso.Chess;
import chesspresso.game.Game;
import chesspresso.move.Move;
import chesspresso.pgn.PGNReader;
import chesspresso.pgn.PGNSyntaxError;
import chesspresso.position.NAG;
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
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import yarin.cbhlib.Database;
import yarin.cbhlib.GameHeader;
import yarin.cbhlib.exceptions.CBHException;
import yarin.cbhlib.exceptions.CBHFormatException;

import java.io.IOException;
import java.net.URL;
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

    private Game game = new Game();

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
        log.debug("drawing board at node " + game.getCurNode());
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

                int piece = game.getPosition().getStone(Chess.coorToSqi(x, y));

                drawPiece(gc, x, y, piece);
            }
        }

    }

    private void drawPiece(GraphicsContext gc, int x, int y, int piece) {
        int sx = 0, sy = 0;
        switch (piece) {
            case Chess.NO_PIECE:
                return;
            case Chess.WHITE_PAWN:
                sx = 5;
                break;
            case Chess.WHITE_KNIGHT:
                sx = 1;
                break;
            case Chess.WHITE_BISHOP:
                sx = 2;
                break;
            case Chess.WHITE_ROOK:
                sx = 0;
                break;
            case Chess.WHITE_QUEEN:
                sx = 3;
                break;
            case Chess.WHITE_KING:
                sx = 4;
                break;
            case Chess.BLACK_PAWN:
                sx = 5;
                sy = 1;
                break;
            case Chess.BLACK_KNIGHT:
                sx = 1;
                sy = 1;
                break;
            case Chess.BLACK_BISHOP:
                sx = 2;
                sy = 1;
                break;
            case Chess.BLACK_ROOK:
                sy = 1;
                break;
            case Chess.BLACK_QUEEN:
                sx = 3;
                sy = 1;
                break;
            case Chess.BLACK_KING:
                sx = 4;
                sy = 1;
                break;
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

    private boolean showMoveNumber;
    private int level;

    private void addNewMoveLine() {
        HBox moveLine = new HBox();
        moveLine.setPadding(new Insets(0, 0, 0, 8 * level));
        moveBox2.getChildren().add(moveLine);
    }

    private void addMoveBox(Move move, int node) {
        ObservableList<Node> noRows = moveBox2.getChildren();
        if (noRows.size() == 0 || ((HBox) moveBox2.getChildren().get(noRows.size() - 1)).getChildren().size() == 60) {
            addNewMoveLine();
        }
        HBox lastRow = (HBox) moveBox2.getChildren().get(noRows.size() - 1);

        // Add, if needed, move number
        int ply = game.getCurrentPly() - 1; // We already moved ahead
        if (showMoveNumber || ply % 2 == 0) {
            Label moveNoLbl = new Label();
            if (level == 0) {
                moveNoLbl.getStyleClass().add("main-line");
            }
            moveNoLbl.getStyleClass().add("move-no-label");
            String s = String.format("%d.", ply / 2 + 1);
            if (ply % 2 == 1) s += "..";
            moveNoLbl.setText(s);
            lastRow.getChildren().add(moveNoLbl);
            showMoveNumber = false;
        }

        // Add move
        MoveLabel lbl = new MoveLabel(move, node, "move-label");
        lbl.setText(move.getSAN());
        lbl.setOnMouseClicked(Controller.this::handleMoveSelected);
        if (level == 0) {
            lbl.getStyleClass().add("main-line");
        }
        lastRow.getChildren().add(lbl);

        // Add comment
        String comment = game.getComment();
        if (comment != null) {
            Label commentLbl = new Label(comment);
            commentLbl.getStyleClass().add("comment-label");
//                log.debug("width " + getLabelLength(commentLbl));
            lastRow.getChildren().add(commentLbl);
        }
    }

    private void newLine() {
        showMoveNumber = true;
        addNewMoveLine();
    }

    private void go() {
        while (game.hasNextMove()) {
            int numOfNextMoves = game.getNumOfNextMoves();
            if (numOfNextMoves == 1) {
                Move move = game.getNextMove();
                game.goForward();
                addMoveBox(move, game.getCurNode());
            } else {
                Move move = game.getNextMove();
                game.goForward();
                addMoveBox(move, game.getCurNode());
                level++;

                for (int i = 1; i < numOfNextMoves; i++) {
                    newLine();
                    game.goBack();

                    move = game.getNextMove(i);
                    game.goForward(i);
                    addMoveBox(move, game.getCurNode());

                    go();

                    game.goBackToMainLine();
                }
                level--;
                newLine();
            }
        }
    }

    private void drawMoves() {
        log.debug("drawing moves");

        showMoveNumber = true;
        this.level = 0;
        game.gotoStart();
        go();


/*
        game.gotoStart();

        while (game.hasNextMove()) {
            if (game.getCurrentPly() % 2 == 0) {
                Label moveNoLbl = new Label();
                moveNoLbl.setText(String.format("%d.", game.getCurrentMoveNumber() + 1));
                moveBox.getChildren().add(moveNoLbl);
            }
            Move move = game.getNextMove();

            game.goForward();
            MoveLabel lbl = new MoveLabel(move, game.getCurNode(), "move-label");
            lbl.setText(move.getSAN());
            lbl.setOnMouseClicked(Controller.this::handleMoveSelected);
            moveBox.getChildren().add(lbl);

            String comment = game.getComment();
            if (comment != null) {
                Label commentLbl = new Label(comment);
//                log.debug("width " + getLabelLength(commentLbl));
                moveBox.getChildren().add(commentLbl);
            }

        }
        */
    }

    private void handleMoveSelected(MouseEvent mouseEvent) {
        MoveLabel source = (MoveLabel) mouseEvent.getSource();
        log.debug("Clicked on " + source.getMove().getLAN() + ", node " + source.getNode());
        game.gotoNode(source.getNode());
        drawBoard();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        PGNReader reader = new PGNReader(getClass().getResourceAsStream("/game1.pgn"), "My game");
        try {
            this.game = reader.parseGame();
        } catch (PGNSyntaxError pgnSyntaxError) {
            throw new RuntimeException("Failed to load the game");
        } catch (IOException e) {
            throw new RuntimeException("Failed to load the game");
        }

        drawMoves();

        this.game.gotoStart();
        log.debug("starting at node " + this.game.getCurNode());

        board.widthProperty().bind(leftPane.widthProperty().subtract(20));
        board.heightProperty().bind(leftPane.heightProperty().subtract(20));
//        moveBox.prefWidthProperty().bind(movePane.widthProperty());

        board.widthProperty().addListener(observable -> drawBoard());
        board.heightProperty().addListener(observable -> drawBoard());
    }

}
