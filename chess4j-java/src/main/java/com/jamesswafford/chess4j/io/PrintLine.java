package com.jamesswafford.chess4j.io;

import com.jamesswafford.chess4j.board.Move;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.List;

public class PrintLine {
    private static final  Logger LOGGER = LogManager.getLogger(PrintLine.class);

    public static void printLine(List<Move> moves, int depth, int score, long startTime, long nodes) {
        long timeInCentis = (System.currentTimeMillis() - startTime) / 10;
        String line = getMoveString(moves);
        String output = String.format("%2d %5d %5d %7d %s",depth,score,timeInCentis,nodes,line);
        LOGGER.info(output);
    }

    public static void printNativeLine(int depth, int score, long nodes) {
        printLine(Collections.emptyList(), depth, score, 0, nodes);
    }

    public static String getMoveString(List<Move> moves) {
        StringBuilder s = new StringBuilder();
        for (Move m : moves) {
            s.append(m.toString()).append(" ");
        }
        return s.toString();
    }

}
