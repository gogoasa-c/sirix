/**
 * Copyright (c) 2011, University of Konstanz, Distributed Systems Group
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 * * Neither the name of the University of Konstanz nor the
 * names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.sirix.page;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.sirix.api.PageReadTrx;
import org.sirix.api.PageWriteTrx;
import org.sirix.exception.SirixException;
import org.sirix.node.interfaces.Record;
import org.sirix.node.interfaces.RecordPersistenter;
import org.sirix.page.delegates.PageDelegate;
import org.sirix.page.interfaces.Page;

import com.google.common.base.Objects;
import com.google.common.base.Objects.ToStringHelper;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;

/**
 * <h1>RecordPage</h1>
 * 
 * <p>
 * A record page stores a set of records commonly nodes.
 * </p>
 */
public final class RecordPage implements Page {

	/** Key of record page. This is the base key of all contained nodes. */
	private final long mRecordPageKey;

	/** Nodes. */
	private final Map<Long, Record> mNodes;

	/** {@link PageDelegate} reference. */
	private final int mRevision;

	/** Determine if node page has been modified. */
	private boolean mIsDirty;

	/** {@link PageReadTrx} instance. */
	private final PageReadTrx mPageReadTrx;

	/**
	 * Create record page.
	 * 
	 * @param recordPageKey
	 *          base key assigned to this node page
	 * @param revision
	 *          revision the page belongs to
	 */
	public RecordPage(final @Nonnegative long recordPageKey,
			final @Nonnegative int revision, final @Nonnull PageReadTrx pageReadTrx) {
		assert recordPageKey >= 0 : "recordPageKey must not be negative!";
		assert revision >= 0 : "revision must not be negative!";
		assert pageReadTrx != null : "pageReadTrx must not be null!";
		mRevision = revision;
		mRecordPageKey = recordPageKey;
		mNodes = new HashMap<>();
		mIsDirty = true;
		mPageReadTrx = pageReadTrx;
	}

	/**
	 * Read node page.
	 * 
	 * @param in
	 *          input bytes to read page from
	 * @param pageReadTrx
	 *          {@link 
	 */
	protected RecordPage(final @Nonnull ByteArrayDataInput in,
			final @Nonnull PageReadTrx pageReadTrx) {
		mRevision = in.readInt();
		mRecordPageKey = in.readLong();
		final int size = in.readInt();
		mNodes = new HashMap<>(size);
		final RecordPersistenter persistenter = pageReadTrx.getSession()
				.getResourceConfig().mPersistenter;
		for (int offset = 0; offset < size; offset++) {
			final Record node = persistenter.deserialize(in, pageReadTrx);
			mNodes.put(node.getNodeKey(), node);
		}
		assert pageReadTrx != null : "pageReadTrx must not be null!";
		mPageReadTrx = pageReadTrx;
	}

	/**
	 * Get key of node page.
	 * 
	 * @return node page key
	 */
	public long getRecordPageKey() {
		return mRecordPageKey;
	}

	/**
	 * Get node with the specified node key.
	 * 
	 * @param key
	 *          node key
	 * @return node with given node key, or {@code null} if not present
	 * @throws IllegalArgumentException
	 *           if {@code key < 0}
	 */
	public Record getNode(final @Nonnegative long key) {
		assert key >= 0 : "pKey must not be negative!";
		return mNodes.get(key);
	}

	/**
	 * Store or overwrite a single node.
	 * 
	 * @param node
	 *          node to store
	 */
	public void setNode(final @Nonnull Record node) {
		assert node != null : "node must not be null!";
		mNodes.put(node.getNodeKey(), node);
	}

	@Override
	public void serialize(final @Nonnull ByteArrayDataOutput out) {
		out.writeInt(mRevision);
		out.writeLong(mRecordPageKey);
		out.writeInt(mNodes.size());
		final RecordPersistenter persistenter = mPageReadTrx.getSession()
				.getResourceConfig().mPersistenter;
		for (final Record node : mNodes.values()) {
			persistenter.serialize(out, node, mPageReadTrx);
		}
	}

	@Override
	public final String toString() {
		final ToStringHelper helper = Objects.toStringHelper(this)
				.add("revision", mRevision).add("pagekey", mRecordPageKey)
				.add("nodes", mNodes.toString());
		for (final Record node : mNodes.values()) {
			helper.add("node", node);
		}
		return helper.toString();
	}

	/**
	 * Entry set of all nodes in the page.
	 * 
	 * @return an entry set
	 */
	public final Set<Entry<Long, Record>> entrySet() {
		return Collections.unmodifiableSet(mNodes.entrySet());
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(mRecordPageKey, mNodes);
	}

	@Override
	public boolean equals(final @Nullable Object obj) {
		if (obj instanceof RecordPage) {
			final RecordPage other = (RecordPage) obj;
			return Objects.equal(mRecordPageKey, other.mRecordPageKey)
					&& Objects.equal(mNodes, other.mNodes);
		}
		return false;
	}

	@Override
	public int getRevision() {
		return mRevision;
	}

	@Override
	public PageReference[] getReferences() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void commit(final @Nonnull PageWriteTrx pPageWriteTrx)
			throws SirixException {
	}

	/**
	 * All available nodes.
	 * 
	 * @return a collection view of all nodes
	 */
	public Collection<Record> values() {
		return Collections.unmodifiableCollection(mNodes.values());
	}

	@Override
	public PageReference getReference(int offset) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isDirty() {
		return mIsDirty;
	}

	@Override
	public Page setDirty(final boolean pDirty) {
		mIsDirty = pDirty;
		return this;
	}

	/**
	 * Get the {@link PageReadTrx}.
	 * 
	 * @return page reading transaction
	 */
	public PageReadTrx getPageReadTrx() {
		return mPageReadTrx;
	}

}