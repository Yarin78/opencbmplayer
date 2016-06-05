package se.yarin.opencbmplayer;

import chesspresso.move.Move;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Label;

public class MoveLabel extends Label {
    private Move move;
    private int node;

    public MoveLabel(Move move, int node) {
        this(move, node, null);
    }

    public MoveLabel(Move move, int node, String styleClass) {
        if (styleClass != null) {
            getStyleClass().setAll(styleClass);
        }

        this.move = move;
        this.node = node;
    }

    public Move getMove() {
        return move;
    }

    public int getNode() {
        return node;
    }
}
