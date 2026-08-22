package org.jshard.algorithm;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Stateless implementation of BinomialHash, a constant-time, minimal-memory
 * consistent hashing algorithm (Coluzzi, Brocco, Antonucci &amp; Leidi, 2024
 * - arXiv:2406.19836).
 * <p>
 * A key is first assigned to one of {@link ConstantValues#SLOT_COUNT} fixed
 * slots via {@link #slotIdAssigner(byte[])}; that slot id is then routed to
 * one of {@code shardCount} shards via {@link #getShardIndex(int, int)}.
 * <p>
 * <b>Why the slot indirection exists:</b> {@link ConstantValues#SLOT_COUNT}
 * never changes, so a slot's identity is stable even as the shard count
 * changes around it. This is intended to make a future resharding/migration
 * feature possible without a full-table scan: because the slot -&gt; shard
 * mapping is a pure function of {@code (slotId, shardCount)}, it can be
 * recomputed for every one of the (fixed, small) {@code SLOT_COUNT} slots
 * up front - before any row is touched - to determine exactly which slots
 * move when the shard count changes, then migrate only the rows in those
 * slots. This requires a caller to persist each row's slot id (via
 * {@link #getSlotId(byte[])}) alongside it, e.g. in an indexed column, so
 * migration can filter directly on it instead of rehashing every row.
 * <b>This migration path is not implemented anywhere in this project yet</b>
 * - only the slot id primitive it would depend on exists today.
 * <p>
 * No hash ring, slot table, or other structure is ever built or cached:
 * every call is computed independently from its arguments alone, with no
 * state shared across calls or across instances.
 *
 * @author taha rouhi
 */
public class Algorithm {

    /**
     * Assigns {@code key} to one of {@link ConstantValues#SLOT_COUNT} fixed
     * slots, via a single MurmurHash3-32 hash masked to
     * {@link ConstantValues#SLOT_MASK}.
     * <p>
     * If {@code key} contains a Redis-style hash tag - a {@code {...}}
     * substring - only the bytes inside the braces are hashed, so that
     * multiple keys sharing the same tag (e.g. {@code "user:{42}:profile"}
     * and {@code "user:{42}:orders"}) always land on the same slot (and
     * therefore the same shard).
     *
     * @param key the raw key bytes to assign a slot to
     * @return the assigned slot id, in {@code [0, ConstantValues.SLOT_COUNT)}
     */
    int slotIdAssigner(byte[] key) {

        int openIndex = -1;
        int closedIndex = -1;


        for (int i = 0; i < key.length; i++) {
            if (key[i] == '{' && openIndex == -1) {
                openIndex = i + 1;
            } else if (key[i] == '}' && openIndex != -1) {
                closedIndex = i;
                break;
            }
        }


        int offset = 0;
        int length = key.length;


        if (openIndex != -1 && closedIndex != -1 && closedIndex > openIndex) {
            offset = openIndex;
            length = closedIndex - openIndex;
        }

        int hash = MurmurHash3.murmurHash332(key, offset, length, 0);

        return hash & ConstantValues.SLOT_MASK;
    }



    /**
     * Routes {@code slotId} to one of {@code shardCount} shards using the
     * BinomialHash algorithm: {@code slotId} is hashed against an
     * "enclosing tree" of size {@code E} (the smallest power of two
     * &gt;= shardCount) and a "minor tree" of half that size, {@code M}.
     * If the resulting bucket falls in the lower half ({@code < M}), it is
     * rehashed against the minor tree to preserve monotonicity (the
     * property that, as {@code shardCount} grows, keys only ever move onto
     * newly added shards, never between two pre-existing ones). If the
     * bucket falls at or above {@code shardCount} (an out-of-range bucket
     * in a non-power-of-two shard count), up to {@link ConstantValues#OMEGA}
     * further hash attempts are made before falling back to a final,
     * deterministic minor-tree remap that is guaranteed to land in range.
     *
     * @param slotId     the slot id to route, as produced by {@link #slotIdAssigner(byte[])}
     * @param shardCount the number of available shards
     * @return the target shard index, in {@code [0, shardCount)}
     */
    private int getShardIndex(int slotId , int shardCount){

        if (shardCount <= 1) return 0;


        int l = 32 - Integer.numberOfLeadingZeros(shardCount - 1);
        int E = 1 << l;
        int M = E >> 1;

        int hash_0 = MurmurHash3.murmurHash332(slotId,0);



        for (int i = 0; i <= ConstantValues.OMEGA; i++){

            int hash_i = (i == 0) ? hash_0 : MurmurHash3.murmurHash332(slotId,i);

            int b = hash_i & (E-1);
            int c = relocateWithinLevel(b, hash_i);

            if (c < M){
                int d = hash_0 & (M - 1);
                return relocateWithinLevel(d, hash_0);
            }

            if (c < shardCount){
                return c;
            }


        }

        int d =  hash_0 & (M - 1);
        return relocateWithinLevel(d, hash_0);
    }

    /**
     * Relocates bucket {@code b} within its binary level: if {@code b} is
     * already in the lowest level ({@code 0} or {@code 1}), it is returned
     * unchanged; otherwise {@code b} is pseudo-randomly repositioned (via a
     * hash of {@code h}) to another bucket within the same power-of-two
     * level {@code [2^d, 2^(d+1))}, where {@code d} is {@code b}'s
     * highest set bit. This is the core "recursive halving" step that
     * gives BinomialHash its constant-time, minimal-disruption guarantees.
     *
     * @param b the bucket to relocate
     * @param h the hash value {@code b} was derived from, used as the relocation seed
     * @return the relocated bucket, within the same binary level as {@code b}
     */
    private int relocateWithinLevel(int b, int h) {
        if (b < 2) {
            return b;
        }


        int d = 31 - Integer.numberOfLeadingZeros(b);
        int f = (1 << d) - 1;

        int r = MurmurHash3.murmurHash332(h, f);
        int i = r & f;

        return (1 << d) + i; // 2^d + i
    }


    /**
     * Returns the slot id {@code key} is assigned to, independent of the
     * current shard count. See {@link #slotIdAssigner(byte[])}.
     * <p>
     * Intended for callers that want to persist a row's slot id (e.g. in
     * an indexed column) to support future slot-based resharding/migration
     * without a full-table scan - see this class's Javadoc for details.
     * Not currently called anywhere else in this project.
     *
     * @param key the raw key bytes to compute a slot id for
     * @return the assigned slot id, in {@code [0, ConstantValues.SLOT_COUNT)}
     */
    public int getSlotId(byte[] key){
        return slotIdAssigner(key);
    }

    /**
     * Routes {@code key} to a shard name, looking up the shard index
     * (see {@link #getShardId(byte[], int)}) in {@code shardMap}.
     *
     * @param shardMap the shard index -&gt; shard name mapping to resolve against;
     *                 its size is used as the shard count for routing
     * @param key      the key to route
     * @return the name of the shard {@code key} routes to
     */
    public String getShardKey(Map<Integer ,String> shardMap,String key){
        byte[] byteKey = key.getBytes(StandardCharsets.UTF_8);

        int targetShardId = getShardId(byteKey,shardMap.size());

        return shardMap.get(targetShardId);
    }

    /**
     * Routes {@code key} to a shard index among {@code shardCount} shards:
     * computes the key's slot id (see {@link #getSlotId(byte[])}), then
     * routes that slot id via {@link #getShardIndex(int, int)}.
     *
     * @param key        the raw key bytes to route
     * @param shardCount the number of available shards
     * @return the target shard index, in {@code [0, shardCount)}
     */
    public int getShardId(byte[] key, int shardCount){
        int slotId = getSlotId(key);
        return getShardIndex(slotId ,shardCount);
    }







}