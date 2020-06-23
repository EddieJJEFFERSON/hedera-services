package com.hedera.services.legacy.core.jproto;

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

import com.hedera.services.legacy.logic.ApplicationConstants;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionID.Builder;
import com.hederahashgraph.builder.RequestBuilder;
import com.swirlds.common.FastCopyable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.Objects;

import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This is custom class equivalent to Transaction ID proto
 *
 * @author Akshay
 * @Date : 1/9/2019
 */
public class JTransactionID implements FastCopyable, Serializable {

	private static final Logger log = LogManager.getLogger(JTransactionID.class);
	private static final long LEGACY_VERSION_1 = 1;
	private static final long CURRENT_VERSION = 2;
	private EntityId payerAccount;
	private RichInstant startTime;

	public JTransactionID() {
	}

	public JTransactionID(final EntityId payerAccount, final RichInstant startTime) {
		this.payerAccount = payerAccount;
		this.startTime = startTime;
	}

	public JTransactionID(final JTransactionID other) {
		this.payerAccount = other.payerAccount;
		this.startTime = other.startTime;
	}

	public EntityId getPayerAccount() {
		return payerAccount;
	}

	public void setPayerAccount(final EntityId payerAccount) {
		this.payerAccount = payerAccount;
	}

	public RichInstant getStartTime() {
		return startTime;
	}

	public void setStartTime(final RichInstant startTime) {
		this.startTime = startTime;
	}

	/**
	 * Custom serialize method. If some field is null then it will set 0 byte for that field otherwise
	 * it add length of the byte first and then actual byte of the field.
	 *
	 * @return serialized byte array of this class
	 */
	private void serialize(final SerializableDataOutputStream outStream) throws IOException {
		outStream.writeLong(CURRENT_VERSION);
		outStream.writeLong(JObjectType.JTransactionID.longValue());

		if (this.payerAccount != null) {
			outStream.writeChar(ApplicationConstants.P);
			outStream.writeSerializable(payerAccount, true);
		} else {
			outStream.writeChar(ApplicationConstants.N);
		}

		if (this.startTime != null) {
			outStream.writeBoolean(true);
			this.startTime.serialize(outStream);
		} else {
			outStream.writeBoolean(false);
		}

	}

	@SuppressWarnings("unchecked")
	public static <T extends FastCopyable> T deserialize(final DataInputStream inStream) throws IOException {
		final JTransactionID transactionID = new JTransactionID();

		deserialize((SerializableDataInputStream)inStream, transactionID);
		return (T) transactionID;
	}

	/**
	 * Custom deserialization  of this class. It read first length of the field if it is 0 then sets
	 * field to null otherwise it read bytes from DataInputStream of specified length and deserialize
	 * those byte for the field.
	 */

	private static void deserialize(
			final SerializableDataInputStream inStream,
			final JTransactionID transactionID
	) throws IOException {

		long version = inStream.readLong();
		if (version < LEGACY_VERSION_1 || version > CURRENT_VERSION) {
			throw new IllegalStateException("Illegal version was read from the stream");
		}

		long objectType = inStream.readLong();
		JObjectType type = JObjectType.valueOf(objectType);
		if (!JObjectType.JTransactionID.equals(type)) {
			throw new IllegalStateException("Illegal JObjectType was read from the stream");
		}

		if (inStream.readChar() == ApplicationConstants.P) {
			transactionID.payerAccount = EntityId.LEGACY_PROVIDER.deserialize(inStream);
		}

		final boolean startTimePresent = inStream.readBoolean();
		if (startTimePresent) {
			transactionID.startTime = RichInstant.LEGACY_PROVIDER.deserialize(inStream);
		}


	}

	@Override
	public FastCopyable copy() {
		return new JTransactionID(this);
	}

	@Override
	public void copyTo(final SerializableDataOutputStream outStream) throws IOException {
		serialize(outStream);
	}

	@Override
	public void copyFrom(final SerializableDataInputStream inStream) throws IOException {

	}

	@Override
	public void copyToExtra(final SerializableDataOutputStream outStream) throws IOException {

	}

	@Override
	public void copyFromExtra(final SerializableDataInputStream inStream) throws IOException {

	}

	@Override
	public void diffCopyTo(final SerializableDataOutputStream outStream, final SerializableDataInputStream inStream) throws IOException {
		serialize(outStream);
	}

	@Override
	public void diffCopyFrom(final SerializableDataOutputStream outStream, final SerializableDataInputStream inStream) throws IOException {
		deserialize(inStream, this);
	}

	@Override
	public void delete() {

	}

	public static JTransactionID convert(final TransactionID transactionID) {
		RichInstant richInstant = transactionID.hasTransactionValidStart() ?
				RichInstant.fromGrpc(transactionID.getTransactionValidStart()) : null;
		EntityId payerAccount = transactionID.hasAccountID() ?
				EntityId.ofNullableAccountId(transactionID.getAccountID()) : null;
		return new JTransactionID(payerAccount, richInstant);
	}

	public static TransactionID convert(final JTransactionID transactionID) {
	    Builder builder = TransactionID.newBuilder();
        if(transactionID.getPayerAccount() != null) {
          AccountID payerAccountID = RequestBuilder.getAccountIdBuild(
                transactionID.getPayerAccount().num(),
                transactionID.getPayerAccount().realm(),
                transactionID.getPayerAccount().shard());
          builder.setAccountID(payerAccountID);
        }
        
        if(transactionID.getStartTime() != null) {
          Timestamp startTime = transactionID.getStartTime().toGrpc();
          builder.setTransactionValidStart(startTime);
        }
        
		return builder.build();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		JTransactionID that = (JTransactionID) o;
		return Objects.equals(payerAccount, that.payerAccount) &&
				Objects.equals(startTime, that.startTime);
	}

	@Override
	public int hashCode() {
		return Objects.hash(payerAccount, startTime);
	}

	@Override
	public String toString() {
		return "JTransactionID{" +
				"payerAccount=" + payerAccount +
				", startTime=" + startTime +
				'}';
	}
}
