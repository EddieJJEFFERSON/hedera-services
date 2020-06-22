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
import com.hedera.services.legacy.core.jproto.HEntityId;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JTransactionRecord;
import com.hedera.services.legacy.exception.NegativeAccountBalanceException;
import com.swirlds.common.FCMValue;
import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.utility.AbstractMerkleNode;
import com.swirlds.fcqueue.FCQueue;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import static com.hedera.services.context.domain.haccount.HederaAccountSerializer.HEDERA_ACCOUNT_SERIALIZER;
import static com.hedera.services.context.domain.haccount.HederaAccountDeserializer.HEDERA_ACCOUNT_DESERIALIZER;
import static com.hedera.services.legacy.core.jproto.JKey.equalUpToDecodability;
import static java.util.Comparator.comparingLong;

public class HederaAccount extends AbstractMerkleNode implements FCMValue, MerkleLeaf {
	private static final Logger log = LogManager.getLogger(HederaAccount.class);

	JKey accountKeys;
	long balance;
	long expirationTime;
	long autoRenewPeriod;
	long senderThreshold;
	long receiverThreshold;
	String memo;
	boolean deleted;
	boolean isSmartContract;
	boolean receiverSigRequired;
	HEntityId proxyAccount;
	FCQueue<JTransactionRecord> records;

	public HederaAccount() {
		this.records = new FCQueue<>(JTransactionRecord::deserialize);
	}

	public HederaAccount(HederaAccount that) {
		this(that, false);
	}

	public HederaAccount(HederaAccount that, boolean isFastCopy) {
		this.balance = that.balance;
		this.receiverThreshold = that.receiverThreshold;
		this.senderThreshold = that.senderThreshold;
		this.receiverSigRequired = that.receiverSigRequired;
		this.accountKeys = that.accountKeys;
		this.proxyAccount = that.getProxyAccount();
		this.autoRenewPeriod = that.autoRenewPeriod;
		this.deleted = that.deleted;
		this.expirationTime = that.expirationTime;
		this.memo = that.memo;
		this.isSmartContract = that.isSmartContract;
		if (isFastCopy) {
			try {
				this.records = that.records.copy(false);
			} catch (IllegalStateException ignore) {
				this.records = that.records;
			}
		} else {
			this.records = that.records;
		}
	}

	/* ------------------------------- */
	/* ---- SerializedObjProvider ---- */
	/* ------------------------------- */
	@SuppressWarnings("unchecked")
	public static <T extends FastCopyable> T legacyDeserialize(final DataInputStream in) throws IOException {
		return (T)HEDERA_ACCOUNT_DESERIALIZER.deserialize(in);
	}

	/* ------------------------------- */
	/* ------    FastCopyable    ----- */
	/* ------------------------------- */
	@Override
	public HederaAccount copy() {
		return new HederaAccount(this, true);
	}

	@Override
	public void copyTo(SerializableDataOutputStream out) throws IOException {
		HEDERA_ACCOUNT_SERIALIZER.serializeExceptRecords(this, out);
		records.copyTo(out);
	}

	@Override
	public void copyToExtra(SerializableDataOutputStream out) throws IOException {
		records.copyToExtra(out);
	}

	@Override
	public void delete() {
		records.delete();
		records = null;
	}

	@Override
	public void copyFrom(SerializableDataInputStream in) {
		throw new UnsupportedOperationException();
	}
	@Override
	public void copyFromExtra(SerializableDataInputStream in) {
		throw new UnsupportedOperationException();
	}
	@Override
	public void diffCopyTo(SerializableDataOutputStream out, SerializableDataInputStream in) {
		throw new UnsupportedOperationException();
	}
	@Override
	public void diffCopyFrom(final SerializableDataOutputStream out, final SerializableDataInputStream in) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isLeaf() {
		return true;
	}

	/* ------------------------------- */
	/* ----        Object         ---- */
	/* ------------------------------- */
	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (o == null || !o.getClass().equals(HederaAccount.class)) {
			return false;
		}
		HederaAccount that = (HederaAccount)o;
		return this.balance == that.balance
				&& this.receiverThreshold == that.receiverThreshold
				&& this.senderThreshold == that.senderThreshold
				&& this.receiverSigRequired == that.receiverSigRequired
				&& this.autoRenewPeriod == that.autoRenewPeriod
				&& this.deleted == that.deleted
				&& this.expirationTime == that.expirationTime
				&& this.isSmartContract == that.isSmartContract
				&& Objects.equals(this.proxyAccount, that.proxyAccount)
				&& Objects.equals(this.records, that.records)
				&& StringUtils.equals(this.memo, that.memo)
				&& equalUpToDecodability(this.accountKeys, that.accountKeys);
	}

	@Override
	public int hashCode() {
		return Objects.hash(
				balance,
				receiverThreshold,
				senderThreshold,
				receiverSigRequired,
				accountKeys,
				proxyAccount,
				autoRenewPeriod,
				deleted,
				records,
				expirationTime,
				memo,
				isSmartContract);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(HederaAccount.class)
				.add("balance", balance)
				.add("receiverThreshold", receiverThreshold)
				.add("senderThreshold", senderThreshold)
				.add("receiverSigRequired", receiverSigRequired)
				.add("accountKeys", accountKeys)
				.add("proxyAccount", proxyAccount)
				.add("autoRenewPeriod", autoRenewPeriod)
				.add("deleted", deleted)
				.add("expirationTime", expirationTime)
				.add("numRecords", records.size())
				.add("memo", memo)
				.add("isSmartContract", isSmartContract)
				.toString();
	}

	/* ------------------------------- */
	/* ----   Getters / Setters   ---- */
	/* ------------------------------- */
	public long expiryOfEarliestRecord() {
		return records.isEmpty() ? -1L : records.peek().getExpirationTime();
	}

	public List<JTransactionRecord> recordList() {
		return new ArrayList<>(records);
	}

	public Iterator<JTransactionRecord> recordIterator() {
		return records.iterator();
	}

	public void resetRecordsToContain(List<JTransactionRecord> records) {
		List<JTransactionRecord> recordList = new ArrayList<>(records);
		recordList.sort(comparingLong(JTransactionRecord::getExpirationTime));
		for (JTransactionRecord record : recordList) {
			this.records.offer(record);
		}
	}

	public FCQueue<JTransactionRecord> getRecords() {
		return records;
	}

	public void setRecords(FCQueue<JTransactionRecord> records) {
		this.records = records;
	}

	public String getMemo() {
		return memo;
	}

	public void setMemo(String memo) {
		this.memo = memo;
	}

	public boolean isSmartContract() {
		return isSmartContract;
	}

	public void setSmartContract(boolean isSmartContract) {
		this.isSmartContract = isSmartContract;
	}

	public long getBalance() {
		return balance;
	}

	public void setBalance(long balance) throws NegativeAccountBalanceException {
		if (balance < 0) {
			throw new NegativeAccountBalanceException(String.format("Illegal balance: %d!", balance));
		}
		this.balance = balance;
	}

	public long getReceiverThreshold() {
		return receiverThreshold;
	}

	public void setReceiverThreshold(long receiverThreshold) {
		this.receiverThreshold = receiverThreshold;
	}

	public long getSenderThreshold() {
		return senderThreshold;
	}

	public void setSenderThreshold(long senderThreshold) {
		this.senderThreshold = senderThreshold;
	}

	public boolean isReceiverSigRequired() {
		return receiverSigRequired;
	}

	public void setReceiverSigRequired(boolean receiverSigRequired) {
		this.receiverSigRequired = receiverSigRequired;
	}

	public JKey getAccountKeys() {
		return accountKeys;
	}

	public void setAccountKeys(JKey accountKeys) {
		this.accountKeys = accountKeys;
	}

	public HEntityId getProxyAccount() {
		return proxyAccount;
	}

	public void setProxyAccount(HEntityId proxyAccount) {
		this.proxyAccount = proxyAccount;
	}

	public long getAutoRenewPeriod() {
		return autoRenewPeriod;
	}

	public void setAutoRenewPeriod(long autoRenewPeriod) {
		this.autoRenewPeriod = autoRenewPeriod;
	}

	public boolean isDeleted() {
		return deleted;
	}

	public void setDeleted(boolean deleted) {
		this.deleted = deleted;
	}

	public long getExpirationTime() {
		return expirationTime;
	}

	public void setExpirationTime(long expirationTime) {
		this.expirationTime = expirationTime;
	}

	@Override
	public long getClassId() {
		return 0;
	}

	@Override
	public int getVersion() {
		return 1;
	}

	@Override
	public void deserialize(SerializableDataInputStream serializableDataInputStream, int i) throws IOException {
		legacyDeserialize(serializableDataInputStream);
	}

	@Override
	public void serialize(SerializableDataOutputStream serializableDataOutputStream) throws IOException {
		this.copyTo(serializableDataOutputStream);
		this.copyToExtra(serializableDataOutputStream);
	}
}
