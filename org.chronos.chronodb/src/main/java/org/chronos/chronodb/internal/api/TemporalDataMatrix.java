package org.chronos.chronodb.internal.api;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.chronos.chronodb.api.key.TemporalKey;
import org.chronos.chronodb.internal.api.stream.CloseableIterator;
import org.chronos.chronodb.internal.impl.temporal.UnqualifiedTemporalEntry;

/**
 * A {@link TemporalDataMatrix} is a structured container for temporal key-value pairs.
 *
 * <p>
 * Such a matrix is defined over a set of timestamps <i>T</i>, in combination with a set of keys <i>K</i>. A temporal
 * data matrix <i>D</i> is then defined as <i>D = T x K</i>. The matrix can be visualized as follows:
 *
 * <pre>
 *    K
 *
 *    ^
 *    |
 *    |
 *    +----+----+----+----+----+
 *  c |    | V2 |    |    |    |
 *    +----+----+----+----+----+
 *  b |    |    | V4 |    |  X |
 *    +----+----+----+----+----+
 *  a | V1 |    | V3 |    |    |
 *    +----+----+----+----+----+---> T
 *       1    2    3    4    5
 * </pre>
 *
 * In the example above, the following inserts have been applied:
 * <ul>
 * <li>a->V1 at T = 1
 * <li>c->v2 at T = 2
 * <li>a->v3, b-> v4 at T = 3
 * <li>b->deleted at T = 5
 * </ul>
 *
 * Each entry in such a matrix D has a certain validity range on the T axis. For example, the entry "a->V1" is valid in
 * range [1;3[, while "b->V4" is valid in [3;5[. In general, each entry is valid from (including) the timestamp at which
 * it was inserted, up to the timestamp where the next insert occurs (exclusive).
 *
 * @author martin.haeusler@uibk.ac.at -- Initial Contribution and API
 *
 */
public interface TemporalDataMatrix {

	// =================================================================================================================
	// METADATA
	// =================================================================================================================

	/**
	 * Returns the name of the keyspace that is represented by this matrix.
	 *
	 * @return The keyspace. Never <code>null</code>.
	 */
	public String getKeyspace();

	/**
	 * Returns the timestamp at which this matrix was created.
	 *
	 * @return The creation timestamp. Never negative.
	 */
	public long getCreationTimestamp();

	// =================================================================================================================
	// QUERIES
	// =================================================================================================================

	/**
	 * Returns the value associated with the given key, at the given timestamp.
	 *
	 * @param timestamp
	 *            The timestamp at which to get the value for the given key. Must not be negative.
	 * @param key
	 *            The key to get the value for. Must not be <code>null</code>.
	 * @return The value for the given key at the given timestamp, or <code>null</code> if the key does not exist in
	 *         this matrix at the given timestamp.
	 */
	public byte[] get(final long timestamp, final String key);

	/**
	 * An extended variation of {@link #get(long, String)}, which also returns the period in which the requested key is
	 * valid.
	 *
	 * @param timestamp
	 *            The timestamp at which to get the value for the given key. Must not be negative.
	 * @param key
	 *            The key to get the value for. Must not be <code>null</code>.
	 * @return A ranged result. Contains the vlaue for the given key at the given timestamp, or <code>null</codE> if the
	 *         key does not exist in this matrix at the given timestamp. The range of the returned object represents the
	 *         period in which the given key is bound to the returned value.
	 */
	public RangedGetResult<byte[]> getRanged(final long timestamp, final String key);

	/**
	 * Returns an iterator over all keys which are present in the matrix at the given timestamp.
	 *
	 * @param timestamp
	 *            The timestamp to get the key set at. Must not be negative.
	 * @return An iterator over all keys that exist in the matrix at the given timestamp. May be empty, but never
	 *         <code>null</code>.
	 */
	public Iterator<String> keys(final long timestamp);

	/**
	 * Returns an iterator over all keys which are present in the matrix, regardless of the timestamp.
	 *
	 * <p>
	 * This in particular also includes keys which were deleted later on. This method returns all keys that were ever
	 * part of the matrix (not including rolled-back entries).
	 *
	 * @return The iterator over all keys. May be empty, but never <code>null</code>.
	 */
	public Iterator<String> allKeys();

	/**
	 * Returns the history of the given key, i.e. all timestamps at which the given key changed its value due to a
	 * commit.
	 *
	 * @param maxTime
	 *            The maximum timestamp to consider. Only timestamps equal to or lower than this value will be
	 *            considered. Must not be negative.
	 * @param key
	 *            The key to get the commit timestamps for. Must not be <code>null</code>.
	 * @return An iterator over all commit timestamps on the given key, up to the given maximum timestamp. May be empty,
	 *         but never <code>null</code>.
	 */
	public Iterator<Long> history(final long maxTime, final String key);

	/**
	 * Returns an iterator over all entries in this matrix.
	 *
	 * @param timestamp
	 *            The timestamp at which the iteration takes place. Only entries which were valid at this timestamp will
	 *            be considered. Must not be negative.
	 * @return An iterator over all entries that were valid at the given timestamp. May be empty, but never
	 *         <code>null</code>.
	 */
	public CloseableIterator<UnqualifiedTemporalEntry> allEntriesIterator(long timestamp);

	/**
	 * Returns the timestamp at which the last (latest) commit has happened on the given key.
	 *
	 * @param key
	 *            The key in question. Must not be <code>null</code>.
	 * @return The latest commit timestamp, or a negative value if there has never been a commit to this key in this
	 *         matrix.
	 */
	public long lastCommitTimestamp(String key);

	/**
	 * Returns the {@link TemporalKey temporal keys} that were modified in the given time range.
	 *
	 * @param timestampLowerBound
	 *            The lower bound on the timestamp to search for (inclusive). Must not be negative. Must be less than or
	 *            equal to the upper bound.
	 * @param timestampUpperBound
	 *            The upper bound on the timestamp to search for (inclusive). Must not be negative. Must be larger than
	 *            or equal to the lower bound.
	 *
	 * @return An iterator over the temporal keys that were modified in the given time range. May be empty, but never
	 *         <code>null</code>.
	 * @see #getCommitTimestampsBetween(long, long)
	 */
	public Iterator<TemporalKey> getModificationsBetween(long timestampLowerBound, long timestampUpperBound);

	/**
	 * Returns the timestamps at which actual commits have taken place in the given time range.
	 *
	 * @param timestampLowerBound
	 *            The lower bound on the timestamp to search for (inclusive). Must not be negative. Must be less than or
	 *            equal to the upper bound.
	 * @param timestampUpperBound
	 *            The upper bound on the timestamp to search for (inclusive). Must not be negative. Must be larger than
	 *            or equal to the lower bound.
	 *
	 * @return An iterator over the commit timestamps in the given time range. May be empty, but never <code>null</code>
	 *         .
	 * @see #getModificationsBetween(long, long)
	 */
	public Iterator<Long> getCommitTimestampsBetween(long timestampLowerBound, long timestampUpperBound);

	// =================================================================================================================
	// CONTENT MODIFICATION
	// =================================================================================================================

	/**
	 * Adds the given contents to this matrix, at the given timestamp.
	 *
	 * @param timestamp
	 *            The timestamp at which to add the given contents to this matrix. Must not be negative.
	 * @param contents
	 *            The key-value pairs to add, as a map. Must not be <code>null</code>. If the map is empty, this method
	 *            is a no-op and returns immediately.
	 */
	public void put(final long timestamp, final Map<String, byte[]> contents);

	/**
	 * Inserts the given set of entries into this matrix.
	 *
	 * @param entries
	 *            The entries to insert. Must not be <code>null</code>. If the set is empty, this method is a no-op and
	 *            returns immediately.
	 */
	public void insertEntries(Set<UnqualifiedTemporalEntry> entries);

	/**
	 * Performs a rollback of the contents of this matrix to the given timestamp.
	 *
	 * <p>
	 * Any entries which have been inserted after the given timestamp will be removed during the process.
	 *
	 * @param timestamp
	 *            The timestamp to roll back to. Must not be negative.
	 */
	public void rollback(long timestamp);

}