package com.jamesswafford.chess4j.search;

import com.jamesswafford.chess4j.board.*;
import com.jamesswafford.chess4j.eval.Eval;
import com.jamesswafford.chess4j.eval.Evaluator;
import com.jamesswafford.chess4j.init.Initializer;
import com.jamesswafford.chess4j.io.FenBuilder;
import com.jamesswafford.chess4j.movegen.MagicBitboardMoveGenerator;
import com.jamesswafford.chess4j.movegen.MoveGenerator;
import com.jamesswafford.chess4j.utils.BoardUtils;
import com.jamesswafford.chess4j.utils.MoveUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.javatuples.Quintet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.jamesswafford.chess4j.Constants.CHECKMATE;

public class AlphaBetaSearch implements Search {

    private static final  Logger LOGGER = LogManager.getLogger(AlphaBetaSearch.class);

    static {
        Initializer.init();
    }

    private final List<Move> pv;
    private final List<Move> lastPv;
    private final SearchStats searchStats;

    private boolean stop;
    private Evaluator evaluator;
    private MoveGenerator moveGenerator;
    private MoveScorer moveScorer;
    private KillerMovesStore killerMovesStore;
    private Consumer<Quintet<Integer, List<Move>, Integer, Integer, Long>> pvCallback;

    public AlphaBetaSearch() {
        this.pv = new ArrayList<>();
        this.lastPv = new ArrayList<>();
        this.searchStats = new SearchStats();

        this.stop = false;
        this.evaluator = new Eval();
        this.moveGenerator = new MagicBitboardMoveGenerator();
        this.moveScorer = new MVVLVA();
        this.killerMovesStore = KillerMoves.getInstance();

        if (Initializer.nativeCodeInitialized()) {
            initializeNativeSearch();
        }
    }
    public SearchStats getSearchStats() {
        return searchStats;
    }

    public List<Move> getPv() { return Collections.unmodifiableList(pv); }

    @Override
    public void initialize() {
        lastPv.clear();
        if (Initializer.nativeCodeInitialized()) {
            initializeNativeSearch();
        }
    }

    public void setEvaluator(Evaluator evaluator) {
        this.evaluator = evaluator;
    }

    public void setMoveGenerator(MoveGenerator moveGenerator) {
        this.moveGenerator = moveGenerator;
    }

    public void setMoveScorer(MoveScorer moveScorer) {
        this.moveScorer = moveScorer;
    }

    public void setKillerMovesStore(KillerMovesStore killerMovesStore) {
        this.killerMovesStore = killerMovesStore;
    }

    @Override
    public void setPvCallback(Consumer<Quintet<Integer, List<Move>, Integer, Integer, Long>> pvCallback) {
        this.pvCallback = pvCallback;
    }

    @Override
    public int search(Board board, SearchParameters searchParameters) {
        return search(board, new ArrayList<>(), searchParameters);
    }

    @Override
    public int search(Board board, List<Undo> undos, SearchParameters searchParameters) {
        if (Initializer.nativeCodeInitialized()) {
            return searchWithNativeCode(board, undos, searchParameters);
        } else {
            return searchWithJavaCode(board, undos, searchParameters);
        }
    }

    @Override
    public boolean isStopped() {
        return stop;
    }

    @Override
    public void stop() {
        stop = true;
    }

    @Override
    public void unstop() {
        stop = false;
    }

    private int searchWithJavaCode(Board board, List<Undo> undos, SearchParameters searchParameters) {
        killerMovesStore.clear();
        int score = search(board, undos, pv, true, 0, searchParameters.getDepth(),
                searchParameters.getAlpha(), searchParameters.getBeta());
        lastPv.clear();
        lastPv.addAll(pv);
        return score;
    }

    private int searchWithNativeCode(Board board, List<Undo> undos, SearchParameters searchParameters) {

        String fen = FenBuilder.createFen(board, false);

        List<Long> prevMoves = undos.stream()
                .map(undo -> MoveUtils.toNativeMove(undo.getMove()))
                .collect(Collectors.toList());

        List<Long> nativePV = new ArrayList<>();
        try {
            int nativeScore = searchNative(fen, prevMoves, nativePV, searchParameters.getDepth(),
                    searchParameters.getAlpha(), searchParameters.getBeta(), searchStats);

            assert (searchesAreEqual(board, undos, searchParameters, fen, nativeScore, nativePV));

            // translate the native PV into the object's PV
            pv.clear();
            for (int i=0; i<nativePV.size(); i++) {
                Long nativeMv = nativePV.get(i);
                // which side is moving?  On even moves it is the player on move.
                Color ptm = (i % 2) == 0 ? board.getPlayerToMove() : Color.swap(board.getPlayerToMove());
                pv.add(MoveUtils.fromNativeMove(nativeMv, ptm));
            }

            return nativeScore;
        } catch (IllegalStateException e) {
            LOGGER.error(e);
            throw e;
        }
    }

    private boolean searchesAreEqual(Board board, List<Undo> undos, SearchParameters searchParameters,
                                     String fen, int nativeScore, List<Long> nativePV)
    {
        try {
            // copy the search stats for comparison
            SearchStats nativeStats = new SearchStats();
            nativeStats.nodes = searchStats.nodes;
            nativeStats.failHighs = searchStats.failHighs;
            nativeStats.draws = searchStats.draws;

            searchStats.initialize();
            int javaScore = searchWithJavaCode(board, undos, searchParameters);
            if (javaScore != nativeScore || !searchStats.equals(nativeStats)) {
                LOGGER.error("searches not equal!  javaScore: " + javaScore + ", nativeScore: " + nativeScore
                        + ", java stats: " + searchStats + ", native stats: " + nativeStats
                        + ", params: " + searchParameters + ", fen: " + fen);
                return false;
            }
            // compare the PVs.
            if (!moveLinesAreEqual(board, nativePV, pv)) {
                LOGGER.error("pvs are not equal!"
                        + ", java stats: " + searchStats + ", native stats: " + nativeStats
                        + ", params: " + searchParameters + ", fen: " + fen);
                return false;
            }

            return true;
        } catch (IllegalStateException e) {
            LOGGER.error(e);
            throw e;
        }
    }

    private boolean moveLinesAreEqual(Board board, List<Long> nativePV, List<Move> javaPV) {
        if (nativePV.size() != pv.size()) {
            LOGGER.error("nativePV.size: " + nativePV.size() + ", javaPV.size: " + javaPV.size());
            return false;
        }

        for (int i=0; i<nativePV.size(); i++) {
            Long nativeMv = nativePV.get(i);
            // which side is moving?  On even moves it is the player on move.
            Color ptm = (i % 2) == 0 ? board.getPlayerToMove() : Color.swap(board.getPlayerToMove());
            Move convertedMv = MoveUtils.fromNativeMove(nativeMv, ptm);
            Move javaMv = javaPV.get(i);
            if (! javaMv.equals(convertedMv)) {
                return false;
            }
        }

        return true;
    }

    private int search(Board board, List<Undo> undos, List<Move> parentPV, boolean first, int ply, int depth,
                       int alpha, int beta) {

        searchStats.nodes++;
        parentPV.clear();

        if (depth == 0) {
            return evaluator.evaluateBoard(board);
        }

        // Draw check
        if (Draw.isDraw(board, undos)) {
            searchStats.draws++;
            return 0;
        }

        List<Move> pv = new ArrayList<>(50);

        int numMovesSearched = 0;
        Move pvMove = first && lastPv.size() > ply ? lastPv.get(ply) : null;
        MoveOrderer moveOrderer = new MoveOrderer(board, moveGenerator, moveScorer,
                pvMove, killerMovesStore.getKiller1(ply), killerMovesStore.getKiller2(ply));
        Move move;

        while ((move = moveOrderer.selectNextMove()) != null) {
            assert(BoardUtils.isPseudoLegalMove(board, move));

            undos.add(board.applyMove(move));
            // check if move was legal
            if (BoardUtils.isOpponentInCheck(board)) {
                board.undoMove(undos.remove(undos.size()-1));
                continue;
            }

            boolean pvNode = first && numMovesSearched == 0;
            int val = -search(board, undos, pv, pvNode, ply+1, depth-1,
                    -beta, -alpha);
            ++numMovesSearched;
            board.undoMove(undos.remove(undos.size()-1));

            // if the search was stopped we can't trust these results, so don't update the PV
            if (stop) {
                return 0;
            }

            if (val >= beta) {
                searchStats.failHighs++;
                if (move.captured()==null && move.promotion()==null) {
                    killerMovesStore.addKiller(ply, move);
                }
                return beta;
            }
            if (val > alpha) {
                alpha = val;
                setParentPV(parentPV, move, pv);
                if (pvCallback != null) {
                    pvCallback.accept(Quintet.with(ply, parentPV, depth, alpha, searchStats.nodes));
                }
            }
        }

        alpha = adjustFinalScoreForMates(board, alpha, numMovesSearched, ply);

        return alpha;
    }

    private int adjustFinalScoreForMates(Board board, int score, int numMovesSearched, int ply) {
        int adjScore = score;

        if (numMovesSearched==0) {
            if (BoardUtils.isPlayerInCheck(board)) {
                adjScore = -CHECKMATE + ply;
            } else {
                // draw score
                adjScore = 0;
            }
        }

        return adjScore;
    }

    private void setParentPV(List<Move> parentPV, Move head, List<Move> tail) {
        parentPV.clear();
        parentPV.add(head);
        parentPV.addAll(tail);
    }

    private native void initializeNativeSearch();

    private native int searchNative(String boardFen, List<Long> prevMoves, List<Long> parentPV, int depth,
                                    int alpha, int beta, SearchStats searchStats);

}
