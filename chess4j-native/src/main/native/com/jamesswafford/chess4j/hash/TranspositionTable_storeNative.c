#include <prophet/hash.h>
#include <prophet/parameters.h>
#include <prophet/position/position.h>

#include <com_jamesswafford_chess4j_hash_TranspositionTable.h>
#include "../board/Board.h"
#include "../init/p4_init.h"
#include "../../../../java/lang/IllegalStateException.h"

extern hash_table_t htbl;

/*
 * Class:     com_jamesswafford_chess4j_hash_TranspositionTable
 * Method:    storeNative
 * Signature: (Lcom/jamesswafford/chess4j/board/Board;J)V
 */
JNIEXPORT void JNICALL Java_com_jamesswafford_chess4j_hash_TranspositionTable_storeNative
  (JNIEnv *env, jobject UNUSED(htable), jobject board_obj, jlong val)
{
    /* ensure the static library is initialized */
    if (!p4_initialized) 
    {
        (*env)->ThrowNew(env, IllegalStateException, "Prophet4 not initialized!");
        return;
    }
    
    /* set the position */
    position_t c4j_pos;
    if (0 != convert(env, board_obj, &c4j_pos))
    {
        (*env)->ThrowNew(env, IllegalStateException, 
            "An error was encountered while converting a position.");
        return;
    }

    /* store the value in the hash table */
    store_hash_entry(&htbl, c4j_pos.hash_key, (uint64_t)val);

}
