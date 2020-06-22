package com.hedera.services.context.domain.haccount;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.google.common.base.MoreObjects;
import com.hedera.services.context.domain.serdes.DomainSerdes;
import com.hedera.services.legacy.core.jproto.HEntityId;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JTransactionRecord;
import com.hedera.services.legacy.exception.NegativeAccountBalanceException;
import com.hedera.services.state.merkle.AccountState;
import com.swirlds.common.FCMValue;
import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.io.SerializedObjectProvider;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.utility.AbstractMerkleInternal;
import com.swirlds.fcqueue.FCQueue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import static com.hedera.services.legacy.logic.ApplicationConstants.P;

public class HederaAccount extends AbstractMerkleInternal implements FCMValue, MerkleInternal {
	private static final Logger log = LogManager.getLogger(HederaAccount.class);

	static final int MERKLE_VERSION = 1;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0x950bcf7255691908L;

	static DomainSerdes serdes = new DomainSerdes();

	@Deprecated
	public static final Provider LEGACY_PROVIDER = new Provider();

	/* Order of v1 Merkle node children */
	static final int STATE_CHILD_INDEX = 0;
	static final int RECORDS_CHILD_INDEX = 1;
	static final int PAYER_RECORDS_CHILD_INDEX = 2;
	static final int NUM_V1_CHILDREN = 3;

	public HederaAccount(List<MerkleNode> children) {
		super(NUM_V1_CHILDREN);
		addDeserializedChildren(children, MERKLE_VERSION);
	}

	public HederaAccount() {
		this(List.of(
				new AccountState(),
				new FCQueue<JTransactionRecord>(JTransactionRecord::deserialize),
				new FCQueue<JTransactionRecord>(JTransactionRecord::deserialize)));
	}

	public HederaAccount(HederaAccount that, boolean fastCopy) {
		this(List.of(
				that.state().copy(),
				fastCopy
						? (that.records().isImmutable() ? that.records() : that.records().copy(false))
						: that.records(),
				fastCopy
						? (that.payerRecords().isImmutable() ? that.payerRecords() : that.payerRecords().copy(false))
						: that.payerRecords()));
	}

	public HederaAccount(HederaAccount that) {
		this(that, false);
	}

	/* --- MerkleInternal --- */

	@Override
	public long getClassId() {
		return RUNTIME_CONSTRUCTABLE_ID;
	}

	@Override
	public int getVersion() {
		return MERKLE_VERSION;
	}

	@Override
	public int getMinimumChildCount(int version) {
		return NUM_V1_CHILDREN;
	}

	/* --- FastCopyable --- */

	@Override
	public boolean isImmutable() {
		return false;
	}

	@Override
	public HederaAccount copy() {
		return new HederaAccount(this, true);
	}

	@Override
	public void delete() {
		records().delete();
		payerRecords().delete();
	}

	@Override
	@Deprecated
	public void copyTo(SerializableDataOutputStream out) {
		throw new UnsupportedOperationException();
	}

	@Override
	@Deprecated
	public void copyToExtra(SerializableDataOutputStream out) {
		throw new UnsupportedOperationException();
	}

	@Override
	@Deprecated
	public void copyFrom(SerializableDataInputStream in) {
		throw new UnsupportedOperationException();
	}

	@Override
	@Deprecated
	public void copyFromExtra(SerializableDataInputStream in) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void diffCopyTo(SerializableDataOutputStream out, SerializableDataInputStream in) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void diffCopyFrom(SerializableDataOutputStream out, SerializableDataInputStream in) {
		throw new UnsupportedOperationException();
	}

	/* ---- Object ---- */

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (o == null || HederaAccount.class != o.getClass()) {
			return false;
		}
		var that = (HederaAccount)o;
		return this.state().equals(that.state()) &&
				this.records().equals(that.records()) &&
				this.payerRecords().equals(that.payerRecords());
	}

	@Override
	public int hashCode() {
		return Objects.hash(state(), records(), payerRecords());
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(HederaAccount.class)
				.add("state", state())
				.add("# records", records().size())
				.add("# payer records", payerRecords().size())
				.toString();
	}

	/* ----  Merkle children  ---- */

	public AccountState state() {
		return getChild(STATE_CHILD_INDEX);
	}

	public FCQueue<JTransactionRecord> records() {
		return getChild(RECORDS_CHILD_INDEX);
	}

	public FCQueue<JTransactionRecord> payerRecords() {
		return getChild(PAYER_RECORDS_CHILD_INDEX);
	}

	public void setRecords(FCQueue<JTransactionRecord> records) {
		setChild(RECORDS_CHILD_INDEX, records);
	}

	/* ----  Bean  ---- */

	public String getMemo() {
		return state().memo();
	}

	public void setMemo(String memo) {
		state().setMemo(memo);
	}

	public boolean isSmartContract() {
		return state().isSmartContract();
	}

	public void setSmartContract(boolean smartContract) {
		state().setSmartContract(smartContract);
	}

	public long getBalance() {
		return state().balance();
	}

	public void setBalance(long balance) throws NegativeAccountBalanceException {
		if (balance < 0) {
			throw new NegativeAccountBalanceException(String.format("Illegal balance: %d!", balance));
		}
		state().setBalance(balance);
	}

	public long getReceiverThreshold() {
		return state().receiverThreshold();
	}

	public void setReceiverThreshold(long receiverThreshold) {
		state().setReceiverThreshold(receiverThreshold);
	}

	public long getSenderThreshold() {
		return state().senderThreshold();
	}

	public void setSenderThreshold(long senderThreshold) {
		state().setSenderThreshold(senderThreshold);
	}

	public boolean isReceiverSigRequired() {
		return state().isReceiverSigRequired();
	}

	public void setReceiverSigRequired(boolean receiverSigRequired) {
		state().setReceiverSigRequired(receiverSigRequired);
	}

	public JKey getKey() {
		return state().key();
	}

	public void setKey(JKey key) {
		state().setKey(key);
	}

	public HEntityId getProxy() {
		return state().proxy();
	}

	public void setProxy(HEntityId proxy) {
		state().setProxy(proxy);
	}

	public long getAutoRenewSecs() {
		return state().autoRenewSecs();
	}

	public void setAutoRenewSecs(long autoRenewSecs) {
		state().setAutoRenewSecs(autoRenewSecs);
	}

	public boolean isDeleted() {
		return state().isDeleted();
	}

	public void setDeleted(boolean deleted) {
		state().setDeleted(deleted);
	}

	public long getExpiry() {
		return state().expiry();
	}

	public void setExpiry(long expiry) {
		state().setExpiry(expiry);
	}

	/* --- Helpers --- */

	public long expiryOfEarliestRecord() {
		var records = records();
		return records.isEmpty() ? -1L : records.peek().getExpirationTime();
	}

	public List<JTransactionRecord> recordList() {
		return new ArrayList<>(records());
	}

	public Iterator<JTransactionRecord> recordIterator() {
		return records().iterator();
	}

	@Deprecated
	public static class Provider implements SerializedObjectProvider {
		@Override
		public FastCopyable deserialize(DataInputStream in) throws IOException {
			in.readLong();
			in.readLong();

			var balance = in.readLong();
			var senderThreshold = in.readLong();
			var receiverThreshold = in.readLong();
			var receiverSigRequired = (in.readByte() == 1);
			var key = serdes.deserializeKey(in);
			HEntityId proxy = null;
			if (in.readChar() == P) {
				in.readLong();
				in.readLong();
				proxy = new HEntityId(in.readLong(), in.readLong(), in.readLong());
			}
			var autoRenewSecs = in.readLong();
			var deleted = (in.readByte() == 1);
			var expiry = in.readLong();
			var memo = in.readUTF();
			var smartContract = (in.readByte() == 1);
			var state = new AccountState(
					key,
					expiry, balance, autoRenewSecs, senderThreshold, receiverThreshold,
					memo,
					deleted, smartContract, receiverSigRequired,
					proxy);

			var records = new FCQueue<JTransactionRecord>(JTransactionRecord::deserialize);
			serdes.deserializeIntoRecords(in, records);
			var payerRecords = new FCQueue<JTransactionRecord>(JTransactionRecord::deserialize);

			return new HederaAccount(List.of(state, records, payerRecords));
		}
	}
}
