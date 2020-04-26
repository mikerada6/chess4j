package com.jamesswafford.chess4j.movegen;

import com.jamesswafford.chess4j.board.Bitboard;
import com.jamesswafford.chess4j.board.Board;
import com.jamesswafford.chess4j.board.squares.File;
import com.jamesswafford.chess4j.board.squares.Rank;
import com.jamesswafford.chess4j.board.squares.Square;
import com.jamesswafford.chess4j.utils.BoardUtils;

import java.util.Optional;
import java.util.Random;
import java.util.function.Function;

public class Magic {

    private static long[] rookMasks = new long[64];
    private static long[][] rookOcc = new long[64][4096];
    private static long[][] rookAttacks = new long[64][4096];
    private static long[] magicNumbersRooks = new long[64];
    private static long[] magicNumbersShiftRooks = new long[64];
    private static long[][] magicRookMoves = new long[64][4096]; // 4096 = 2^12.  12=max bits for mask

    private static long[] bishopMasks = new long[64];
    private static long[][] bishopOcc = new long[64][1024];
    private static long[][] bishopAttacks = new long[64][1024];
    private static long[] magicNumbersBishops = new long[64];
    private static long[] magicNumbersShiftBishops = new long[64];
    private static long[][] magicBishopMoves = new long[64][1024];

    // initialize rook and bishop masks
    static {

        for (int i=0;i<64;i++) {
            rookMasks[i] = 0;

            Square sq = Square.valueOf(i);

            // if on the same rank or file.  "End Points" can be ignored.
            for (int j=0;j<64;j++) {
                Square sq2 = Square.valueOf(j);
                if (i != j) {
                    if ((sq2.rank()==sq.rank() && sq2.file() != File.FILE_A && sq2.file() != File.FILE_H)
                        || (sq2.file()==sq.file() && sq2.rank() != Rank.RANK_1 && sq2.rank() != Rank.RANK_8))
                    {
                        rookMasks[i] |= Bitboard.squares[j];
                    }
                    if (BoardUtils.isDiagonal(sq, sq2)
                            && sq2.rank() != Rank.RANK_1 && sq2.rank() != Rank.RANK_8
                            && sq2.file() != File.FILE_A && sq2.file() != File.FILE_H)
                    {
                        bishopMasks[i] |= Bitboard.squares[j];
                    }
                }
            }
        }
    }

    // initialize rook attack sets
    static {

        for (int sqInd=0;sqInd<64;sqInd++) {
            Square sq = Square.valueOf(sqInd);
            long mask = rookMasks[sqInd];
            int numVariations = 1 << Long.bitCount(mask); // 2 ^ num bits

            for (int i=0;i<numVariations;i++) {
                rookOcc[sqInd][i] = rookAttacks[sqInd][i] = 0;

                // map the index to an occupancy variation
                int index = i;
                while (index != 0) {
                    int indexBit = Bitboard.lsb(index);
                    rookOcc[sqInd][i] |= Bitboard.isolateLSB(mask,indexBit);
                    index ^= 1 << indexBit;
                }

                // sanity check
                for (int j=0;j<i;j++) {
                    assert(rookOcc[sqInd][i] != rookOcc[sqInd][j]);
                }

                // create the attack set.  that's the first "blocker" in every direction
                Optional<Square> toSq = sq.north();
                while (toSq.isPresent()) {
                    if ((rookOcc[sqInd][i] & Bitboard.squares[toSq.get().value()]) != 0) {
                        rookAttacks[sqInd][i] |= Bitboard.squares[toSq.get().value()];
                        break;
                    }
                    toSq = toSq.flatMap(Square::north);
                }

                toSq = sq.south();
                while (toSq.isPresent()) {
                    if ((rookOcc[sqInd][i] & Bitboard.squares[toSq.get().value()]) != 0) {
                        rookAttacks[sqInd][i] |= Bitboard.squares[toSq.get().value()];
                        break;
                    }
                    toSq = toSq.flatMap(Square::south);
                }

                toSq = sq.east();
                while (toSq.isPresent()) {
                    if ((rookOcc[sqInd][i] & Bitboard.squares[toSq.get().value()]) != 0) {
                        rookAttacks[sqInd][i] |= Bitboard.squares[toSq.get().value()];
                        break;
                    }
                    toSq = toSq.flatMap(Square::east);
                }

                toSq = sq.west();
                while (toSq.isPresent()) {
                    if ((rookOcc[sqInd][i] & Bitboard.squares[toSq.get().value()]) != 0) {
                        rookAttacks[sqInd][i] |= Bitboard.squares[toSq.get().value()];
                        break;
                    }
                    toSq = toSq.flatMap(Square::west);
                }
            }
        }
    }

    // initialize bishop attack sets
    static {

        for (int sqInd=0;sqInd<64;sqInd++) {
            Square sq = Square.valueOf(sqInd);
            long mask = bishopMasks[sqInd];
            int numVariations = 1 << Long.bitCount(mask); // 2 ^ num bits

            for (int i=0;i<numVariations;i++) {
                bishopOcc[sqInd][i] = bishopAttacks[sqInd][i] = 0;

                // map the index to an occupancy variation
                int index = i;
                while (index != 0) {
                    int indexBit = Bitboard.lsb(index);
                    bishopOcc[sqInd][i] |= Bitboard.isolateLSB(mask,indexBit);
                    index ^= 1 << indexBit;
                }

                // sanity check
                for (int j=0;j<i;j++) {
                    assert(bishopOcc[sqInd][i] != bishopOcc[sqInd][j]);
                }

                // create the attack set.  that's the first "blocker" in every direction
                Optional<Square> toSq = sq.northEast();
                while (toSq.isPresent()) {
                    if ((bishopOcc[sqInd][i] & Bitboard.squares[toSq.get().value()]) != 0) {
                        bishopAttacks[sqInd][i] |= Bitboard.squares[toSq.get().value()];
                        break;
                    }
                    toSq = toSq.flatMap(Square::northEast);
                }

                toSq = sq.southEast();
                while (toSq.isPresent()) {
                    if ((bishopOcc[sqInd][i] & Bitboard.squares[toSq.get().value()]) != 0) {
                        bishopAttacks[sqInd][i] |= Bitboard.squares[toSq.get().value()];
                        break;
                    }
                    toSq = toSq.flatMap(Square::southEast);
                }

                toSq = sq.southWest();
                while (toSq.isPresent()) {
                    if ((bishopOcc[sqInd][i] & Bitboard.squares[toSq.get().value()]) != 0) {
                        bishopAttacks[sqInd][i] |= Bitboard.squares[toSq.get().value()];
                        break;
                    }
                    toSq = toSq.flatMap(Square::southWest);
                }

                toSq = sq.northWest();
                while (toSq.isPresent()) {
                    if ((bishopOcc[sqInd][i] & Bitboard.squares[toSq.get().value()]) != 0) {
                        bishopAttacks[sqInd][i] |= Bitboard.squares[toSq.get().value()];
                        break;
                    }
                    toSq = toSq.flatMap(Square::northWest);
                }
            }
        }
    }

    static {

        for (int sq=0;sq<64;sq++) {
            long mask = rookMasks[sq];
            long numMaskBits = Long.bitCount(mask);
            int numVariations = 1 << numMaskBits; // 2 ^ num bits

            long magic;
            long magicShift = 64 - numMaskBits;

            boolean fail;
            boolean isUsed[] = new boolean[4096];
            long usedBy[] = new long[4096];

            Random r = new Random();
            do {
                magic = r.nextLong() & r.nextLong() & r.nextLong();

                for (int i=0;i<numVariations;i++) {
                    isUsed[i] = false;
                }
                fail = false;

                for (int i=0;i<numVariations;i++) {
                    long occupied = rookOcc[sq][i];
                    int index = (int)((occupied * magic) >>> magicShift);
                    assert(index <= (1 << numMaskBits) && index <= 4096);

                    // fail if this index is used by an attack set that is incorrect
                    // for this occupancy variation
                    long attackSet = rookAttacks[sq][i];

                    fail = isUsed[index] && (usedBy[index] != attackSet);
                    if (fail) {
                        break;
                    }
                    isUsed[index] = true;
                    usedBy[index] = attackSet;
                }

            } while (fail);

            magicNumbersRooks[sq] = magic;
            magicNumbersShiftRooks[sq] = magicShift;
        }
    }

    static {

        for (int sq=0;sq<64;sq++) {
            long mask = bishopMasks[sq];
            long numMaskBits = Long.bitCount(mask);
            int numVariations = 1 << numMaskBits; // 2 ^ num bits

            long magic;
            long magicShift = 64 - numMaskBits;

            boolean fail;
            boolean isUsed[] = new boolean[1024];
            long usedBy[] = new long[1024];

            Random r = new Random();
            do {
                magic = r.nextLong() & r.nextLong() & r.nextLong();

                for (int i=0;i<numVariations;i++) {
                    isUsed[i] = false;
                }
                fail = false;

                for (int i=0;i<numVariations;i++) {
                    long occupied = bishopOcc[sq][i];
                    int index = (int)((occupied * magic) >>> magicShift);
                    assert(index <= (1 << numMaskBits) && index <= 1024);

                    // fail if this index is used by an attack set that is incorrect
                    // for this occupancy variation
                    long attackSet = bishopAttacks[sq][i];

                    fail = isUsed[index] && (usedBy[index] != attackSet);
                    if (fail) {
                        break;
                    }
                    isUsed[index] = true;
                    usedBy[index] = attackSet;
                }

            } while (fail);

            magicNumbersBishops[sq] = magic;
            magicNumbersShiftBishops[sq] = magicShift;
        }
    }

    // initialize magic moves
    static {
        for (int sqVal=0;sqVal<64;sqVal++) {
            Square sq = Square.valueOf(sqVal);
            long mask = rookMasks[sqVal];
            int numVariations = 1 << Long.bitCount(mask);

            for (int i=0;i<numVariations;i++) {
                int magicInd = (int)((rookOcc[sqVal][i] * magicNumbersRooks[sqVal]) >>> magicNumbersShiftRooks[sqVal]);
                magicRookMoves[sqVal][magicInd] =
                        genMovesMask(sq,rookOcc[sqVal][i],Square::north)
                      | genMovesMask(sq,rookOcc[sqVal][i],Square::south)
                      | genMovesMask(sq,rookOcc[sqVal][i],Square::east)
                      | genMovesMask(sq,rookOcc[sqVal][i],Square::west);
            }
        }

        for (int sqVal=0;sqVal<64;sqVal++) {
            Square sq = Square.valueOf(sqVal);
            long mask = bishopMasks[sqVal];
            int numVariations = 1 << Long.bitCount(mask);

            for (int i=0;i<numVariations;i++) {
                int magicInd = (int)((bishopOcc[sqVal][i] * magicNumbersBishops[sqVal]) >>> magicNumbersShiftBishops[sqVal]);
                magicBishopMoves[sqVal][magicInd] =
                        genMovesMask(sq,bishopOcc[sqVal][i],Square::northEast)
                      | genMovesMask(sq,bishopOcc[sqVal][i],Square::southEast)
                      | genMovesMask(sq,bishopOcc[sqVal][i],Square::southWest)
                      | genMovesMask(sq,bishopOcc[sqVal][i],Square::northWest);
            }
        }
    }

    private static long genMovesMask(Square sq, long occupied, Function<Square,Optional<Square>> next) {
        long mask = 0;

        Optional<Square> to = next.apply(sq);
        while (to.isPresent()) {
            mask |= Bitboard.squares[to.get().value()];
            if ((Bitboard.squares[to.get().value()] & occupied) != 0) {
                break;
            }
            to = next.apply(to.get());
        }

        return mask;
    }

    public static long getBishopMoves(Board board, int fromSq, long targets) {
        long blockers = (board.getBlackPieces() | board.getWhitePieces()) & bishopMasks[fromSq];
        int magicInd = (int)((blockers * magicNumbersBishops[fromSq]) >>> magicNumbersShiftBishops[fromSq]);
        return magicBishopMoves[fromSq][magicInd] & targets;
    }

    public static long getQueenMoves(Board board,int fromSq,long targets) {
        return getBishopMoves(board,fromSq,targets) | getRookMoves(board,fromSq,targets);
    }

    public static long getRookMoves(Board board,int fromSq,long targets) {
        long blockers = (board.getBlackPieces() | board.getWhitePieces()) & rookMasks[fromSq];
        int magicInd = (int)((blockers * magicNumbersRooks[fromSq]) >>> magicNumbersShiftRooks[fromSq]);
        return magicRookMoves[fromSq][magicInd] & targets;
    }

}
