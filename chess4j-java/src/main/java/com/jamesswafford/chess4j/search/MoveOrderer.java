package com.jamesswafford.chess4j.search;

import com.jamesswafford.chess4j.board.Board;
import com.jamesswafford.chess4j.board.Move;
import com.jamesswafford.chess4j.movegen.MoveGenerator;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.jamesswafford.chess4j.utils.BoardUtils.*;
import static com.jamesswafford.chess4j.utils.MoveUtils.*;

public class MoveOrderer {

    private final Board board;
    private final MoveGenerator moveGenerator;
    private final MoveScorer moveScorer;

    private final Move pvMove, killer1, killer2;
    private final Set<Move> specialMovesPlayed;

    private Move[] captures;
    private int captureIndex;
    private Integer[] captureScores;
    private Move[] noncaptures;
    private int noncaptureIndex;

    private MoveOrderStage nextMoveOrderStage = MoveOrderStage.PV;

    public MoveOrderer(Board board, MoveGenerator moveGenerator, MoveScorer moveScorer,
                       Move pvMove, Move killer1, Move killer2)
    {
        this.board = board;
        this.moveGenerator = moveGenerator;
        this.moveScorer = moveScorer;

        this.pvMove = pvMove;
        this.killer1 = killer1;
        this.killer2 = killer2;
        this.specialMovesPlayed = new HashSet<>();
    }

    public MoveOrderStage getNextMoveOrderStage() {
        return nextMoveOrderStage;
    }

    public Move selectNextMove() {

        // pv move
        if (nextMoveOrderStage == MoveOrderStage.PV) {
            nextMoveOrderStage = MoveOrderStage.GENCAPS;
            if (pvMove != null) {
                assert(moveGenerator.generateLegalMoves(board).contains(pvMove));
                specialMovesPlayed.add(pvMove);
                return pvMove;
            }
        }

        // generate and score captures and promotions
        if (nextMoveOrderStage == MoveOrderStage.GENCAPS) {
            nextMoveOrderStage = MoveOrderStage.CAPTURES_PROMOS;
            List<Move> myCaptures = moveGenerator.generatePseudoLegalCaptures(board);
            captures =  myCaptures.toArray(new Move[0]);
            captureIndex = 0;
            captureScores = new Integer[myCaptures.size()];
            for (int i=0;i<captures.length;i++) {
                if (specialMovesPlayed.contains(captures[i])) {
                    captures[i] = null;
                } else {
                    captureScores[i] = moveScorer.calculateStaticScore(captures[i]);
                }
            }
        }

        // captures and promotions
        if (nextMoveOrderStage == MoveOrderStage.CAPTURES_PROMOS) {
            int bestInd = getIndexOfBestCapture(captureIndex);
            if (bestInd != -1) {
                swap(captures, captureIndex, bestInd);
                swapScores(captureIndex, bestInd);
                return captures[captureIndex++];
            }
            nextMoveOrderStage = MoveOrderStage.KILLER1;
        }

        if (nextMoveOrderStage == MoveOrderStage.KILLER1) {
            nextMoveOrderStage = MoveOrderStage.KILLER2;
            if (killer1 != null && !specialMovesPlayed.contains(killer1) && isPseudoLegalMove(board, killer1)) {
                assert(killer1.captured()==null);
                specialMovesPlayed.add(killer1);
                return killer1;
            }
        }

        if (nextMoveOrderStage == MoveOrderStage.KILLER2) {
            nextMoveOrderStage = MoveOrderStage.GENNONCAPS;
            if (killer2 != null && !specialMovesPlayed.contains(killer2) && isPseudoLegalMove(board, killer2)) {
                assert(killer2.captured()==null);
                specialMovesPlayed.add(killer2);
                return killer2;
            }
        }

        // generate non-captures
        if (nextMoveOrderStage == MoveOrderStage.GENNONCAPS) {
            nextMoveOrderStage = MoveOrderStage.REMAINING;
            List<Move> myNoncaps = moveGenerator.generatePseudoLegalNonCaptures(board);
            noncaptures =  myNoncaps.toArray(new Move[0]);
            // avoid playing special moves again
            for (int i=0;i<noncaptures.length;i++) {
                if (specialMovesPlayed.contains(noncaptures[i])) {
                    noncaptures[i] = null;
                }
            }

            noncaptureIndex = 0;
        }

        // just play them as they come
        if (noncaptureIndex < noncaptures.length) {
            int ind = getIndexOfFirstNonCapture(noncaptureIndex);
            if (ind != -1) {
                swap(noncaptures, noncaptureIndex,ind);
                return noncaptures[noncaptureIndex++];
            }
        }

        return null;
    }

    private int getIndexOfFirstNonCapture(int startIndex) {
        int index = -1;

        for (int i=startIndex;i<noncaptures.length;i++) {
            Move m = noncaptures[i];
            if (m != null && m.captured()==null) {
                index = i;
                break;
            }
        }

        return index;
    }

    private void swapScores(int ind1, int ind2) {
        Integer tmp = captureScores[ind1];
        captureScores[ind1] = captureScores[ind2];
        captureScores[ind2] = tmp;
    }

    private int getIndexOfBestCapture(int startIndex) {
        int bestIndex = -1;
        int bestScore = -9999;

        for (int i=startIndex;i<captures.length;i++) {
            Move m = captures[i];
            if (m != null && (m.captured() != null || m.promotion() != null)) {
                if (captureScores[i] > bestScore) {
                    bestIndex = i;
                    bestScore = captureScores[i];
                }
            }
        }
        return bestIndex;
    }

}
