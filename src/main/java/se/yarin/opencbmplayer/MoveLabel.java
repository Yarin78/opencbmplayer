package se.yarin.opencbmplayer;

import javafx.scene.control.Label;
import yarin.chess.GamePosition;
import yarin.chess.Move;

public class MoveLabel extends Label {
    private Move move;
    private GamePosition node;

    public MoveLabel(Move move, GamePosition node) {
        this.move = move;
        this.node = node;
    }

    public Move getMove() {
        return move;
    }

    public GamePosition getNode() {
        return node;
    }
}
