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

import com.google.protobuf.ByteString;
import com.hedera.services.context.domain.serdes.DomainSerdes;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.HbarAdjustments;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.submerkle.SolidityFnResult;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.swirlds.common.FastCopyable;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.io.SerializedObjectProvider;
import com.swirlds.fcqueue.FCQueueElement;
import org.apache.commons.codec.binary.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static java.util.stream.Collectors.toList;

public class ExpirableTxnRecord implements FCQueueElement<ExpirableTxnRecord> {
	private static final Logger log = LogManager.getLogger(ExpirableTxnRecord.class);

	static final int MERKLE_VERSION = 1;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0x8b9ede7ca8d8db93L;

	@Deprecated
	public static final Provider LEGACY_PROVIDER = new Provider();

	static DomainSerdes serdes = new DomainSerdes();
	static TxnId.Provider legacyTxnIdProvider = TxnId.LEGACY_PROVIDER;
	static TxnReceipt.Provider legacyReceiptProvider = TxnReceipt.LEGACY_PROVIDER;
	static RichInstant.Provider legacyInstantProvider = RichInstant.LEGACY_PROVIDER;
	static HbarAdjustments.Provider legacyAdjustmentsProvider = HbarAdjustments.LEGACY_PROVIDER;
	static SolidityFnResult.Provider legacyFnResultProvider = SolidityFnResult.LEGACY_PROVIDER;

	private static final long LEGACY_VERSION_1 = 1;
	private static final long LEGACY_VERSION_2 = 2;
	private static final long CURRENT_VERSION = 3;

	private long fee;
	private long expiry;
	private Hash hash;
	private TxnId txnId;
	private byte[] txHash;
	private String memo;
	private RichInstant consensusTimestamp;
	private HbarAdjustments hbarAdjustments;
	private SolidityFnResult contractCallResult;
	private SolidityFnResult contractCreateResult;
	private TxnReceipt txReceipt;

	@Deprecated
	public static class Provider implements SerializedObjectProvider<ExpirableTxnRecord> {
		@Override
		public ExpirableTxnRecord deserialize(DataInputStream in) throws IOException {
			throw new AssertionError("Not implemented");
		}
	}

	// track deserialized version to ensure hashes match when serializing later
	private OptionalLong versionToSerialize = OptionalLong.empty();

	public ExpirableTxnRecord() {
	}

	public ExpirableTxnRecord(
			TxnReceipt txReceipt,
			byte[] txHash,
			TxnId txnId,
			RichInstant consensusTimestamp,
			String memo,
			long fee,
			HbarAdjustments transferList,
			SolidityFnResult contractCallResult,
			SolidityFnResult createResult
	) {
		this.txReceipt = txReceipt;
		this.txHash = txHash;
		this.txnId = txnId;
		this.consensusTimestamp = consensusTimestamp;
		this.memo = memo;
		this.fee = fee;
		this.hbarAdjustments = transferList;
		this.contractCallResult = contractCallResult;
		this.contractCreateResult = createResult;
	}

	public ExpirableTxnRecord(ExpirableTxnRecord that) {
		this.txReceipt = that.txReceipt;
		this.txHash = (that.txHash != null) ? Arrays.copyOf(that.txHash, that.txHash.length) : null;
		this.txnId = that.txnId;
		this.consensusTimestamp = that.consensusTimestamp;
		this.memo = that.memo;
		this.fee = that.fee;
		this.expiry = that.expiry;
		this.hbarAdjustments = that.hbarAdjustments;
		this.contractCallResult = that.contractCallResult;
		this.contractCreateResult = that.contractCreateResult;
	}

	/* --- Object --- */

	@Override
	public boolean equals(Object o) {
		System.out.println("In equals!");
		if (this == o) {
			return true;
		}
		if (o == null || ExpirableTxnRecord.class != o.getClass()) {
			return false;
		}
		var that = (ExpirableTxnRecord) o;
		return fee == that.fee &&
				txReceipt.equals(that.txReceipt) &&
				Arrays.equals(txHash, that.txHash) &&
				txnId.equals(that.txnId) &&
				Objects.equals(consensusTimestamp, that.consensusTimestamp) &&
				Objects.equals(memo, that.memo) &&
				Objects.equals(contractCallResult, that.contractCallResult) &&
				Objects.equals(contractCreateResult, that.contractCreateResult) &&
				Objects.equals(hbarAdjustments, that.hbarAdjustments) &&
				Objects.equals(expiry, that.expiry);
	}

	@Override
	public int hashCode() {
		var result = Objects.hash(
				txReceipt,
				txnId,
				consensusTimestamp,
				memo,
				fee,
				contractCallResult,
				contractCreateResult,
				hbarAdjustments,
				expiry);
		return result * 31 + Arrays.hashCode(txHash);
	}

	/* --- SelfSerializable --- */

	@Override
	public long getClassId() {
		return RUNTIME_CONSTRUCTABLE_ID;
	}

	@Override
	public int getVersion() {
		return MERKLE_VERSION;
	}

	@Override
	public Hash getHash() {
		return this.hash;
	}

	@Override
	public void setHash(Hash hash) {
		this.hash = hash;
	}

	@Override
	public void deserialize(SerializableDataInputStream serializableDataInputStream, int version) throws IOException {
		deserialize(serializableDataInputStream, this);
	}

	/* --- Object --- */

	public TxnReceipt getTxReceipt() {
		return txReceipt;
	}

	public byte[] getTxHash() {
		return txHash;
	}

	public TxnId getTxnId() {
		return txnId;
	}

	public RichInstant getConsensusTimestamp() {
		return consensusTimestamp;
	}

	public String getMemo() {
		return memo;
	}

	public long getFee() {
		return fee;
	}

	public SolidityFnResult getContractCallResult() {
		return contractCallResult;
	}

	public SolidityFnResult getContractCreateResult() {
		return contractCreateResult;
	}

	public HbarAdjustments getHbarAdjustments() {
		return hbarAdjustments;
	}

	public long getExpiry() {
		return expiry;
	}

	public void setExpiry(long expiry) {
		this.expiry = expiry;
	}

	@Override
	public void serialize(final SerializableDataOutputStream out) throws IOException {
		out.writeLong(versionToSerialize.orElse(CURRENT_VERSION));
		out.writeLong(JObjectType.JTransactionRecord.longValue());

		serdes.writeNullableSerializable(txReceipt, out);

		if (this.txHash != null && this.txHash.length > 0) {
			out.writeInt(this.txHash.length);
			out.write(this.txHash);
		} else {
			out.writeInt(0);
		}

		serdes.writeNullableSerializable(txnId, out);

		if (this.consensusTimestamp != null) {
			out.writeBoolean(true);
			this.consensusTimestamp.serialize(out);
		} else {
			out.writeBoolean(false);
		}

		if (this.memo != null) {
			byte[] bytes = StringUtils.getBytesUtf8(this.memo);
			out.writeInt(bytes.length);
			out.write(bytes);
		} else {
			out.writeInt(0);
		}

		out.writeLong(this.fee);

		serdes.writeNullableSerializable(hbarAdjustments, out);
		serdes.writeNullableSerializable(contractCallResult, out);
		serdes.writeNullableSerializable(contractCreateResult, out);

		if (versionToSerialize.orElse(CURRENT_VERSION) > LEGACY_VERSION_2) {
			out.writeLong(this.expiry);
		}
	}

	@SuppressWarnings("unchecked")
	public static <T extends FastCopyable> T deserialize(final DataInputStream inStream) throws IOException {
		ExpirableTxnRecord expirableTxnRecord = new ExpirableTxnRecord();

		deserialize(inStream, expirableTxnRecord);
		return (T) expirableTxnRecord;
	}

	private static void deserialize(final DataInputStream inStream,
			final ExpirableTxnRecord expirableTxnRecord) throws IOException {
		final long version = inStream.readLong();
		if (version < LEGACY_VERSION_1 || version > CURRENT_VERSION) {
			throw new IllegalStateException("Illegal version was read from the stream");
		}

		if (version != CURRENT_VERSION) {
			expirableTxnRecord.versionToSerialize = OptionalLong.of(version);
		}

		final long objectType = inStream.readLong();
		final JObjectType type = JObjectType.valueOf(objectType);
		if (!JObjectType.JTransactionRecord.equals(type)) {
			throw new IllegalStateException("Illegal JObjectType was read from the stream");
		}

		boolean tBytes;
		if (version == LEGACY_VERSION_1) {
			tBytes = inStream.readInt() > 0;
		} else {
			tBytes = inStream.readBoolean();
		}

		if (tBytes) {
			expirableTxnRecord.txReceipt = legacyReceiptProvider.deserialize(inStream);
		} else {
			expirableTxnRecord.txReceipt = null;
		}

		byte[] hBytes = new byte[inStream.readInt()];
		if (hBytes.length > 0) {
			inStream.readFully(hBytes);
		}
		expirableTxnRecord.txHash = hBytes;

		boolean txBytes;
		if (version == LEGACY_VERSION_1) {
			txBytes = inStream.readInt() > 0;
		} else {
			txBytes = inStream.readBoolean();
		}

		if (txBytes) {
			expirableTxnRecord.txnId = legacyTxnIdProvider.deserialize(inStream);
		} else {
			expirableTxnRecord.txnId = null;
		}

		boolean cBytes;
		if (version == LEGACY_VERSION_1) {
			cBytes = inStream.readInt() > 0;
		} else {
			cBytes = inStream.readBoolean();
		}

		if (cBytes) {
			expirableTxnRecord.consensusTimestamp = legacyInstantProvider.deserialize(inStream);
		} else {
			expirableTxnRecord.consensusTimestamp = null;
		}

		byte[] mBytes = new byte[inStream.readInt()];
		if (mBytes.length > 0) {
			inStream.readFully(mBytes);
			expirableTxnRecord.memo = new String(mBytes);
		} else {
			expirableTxnRecord.memo = null;
		}

		expirableTxnRecord.fee = inStream.readLong();

		boolean trBytes;

		if (version == LEGACY_VERSION_1) {
			trBytes = inStream.readInt() > 0;
		} else {
			trBytes = inStream.readBoolean();
		}

		if (trBytes) {
			expirableTxnRecord.hbarAdjustments = legacyAdjustmentsProvider.deserialize(inStream);
		} else {
			expirableTxnRecord.hbarAdjustments = null;
		}

		boolean clBytes;
		if (version == LEGACY_VERSION_1) {
			clBytes = inStream.readInt() > 0;
		} else {
			clBytes = inStream.readBoolean();
		}

		if (clBytes) {
			expirableTxnRecord.contractCallResult = legacyFnResultProvider.deserialize(inStream);
		} else {
			expirableTxnRecord.contractCallResult = null;
		}

		boolean ccBytes;
		if (version == LEGACY_VERSION_1) {
			ccBytes = inStream.readInt() > 0;
		} else {
			ccBytes = inStream.readBoolean();
		}

		if (ccBytes) {
			expirableTxnRecord.contractCreateResult = legacyFnResultProvider.deserialize(inStream);
		} else {
			expirableTxnRecord.contractCreateResult = null;
		}

		if (version >= CURRENT_VERSION) {
			expirableTxnRecord.expiry = inStream.readLong();
		}
	}

	/* --- FastCopyable --- */

	@Override
	public ExpirableTxnRecord copy() {
		return new ExpirableTxnRecord(this);
	}

	@Override
	public void delete() { }

	@Override
	@Deprecated
	public void copyTo(SerializableDataOutputStream out) {
		throw new UnsupportedOperationException();
	}

	@Override
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

	/* --- Helpers --- */

	public static ExpirableTxnRecord fromGprc(TransactionRecord record) {
		try {
			TxnReceipt txnReceipt =
					TxnReceipt.fromGrpc(record.getReceipt());
			TxnId txnId = TxnId.fromGrpc(record.getTransactionID());

			HbarAdjustments hbarAdjustments = null;
			if (record.hasTransferList()) {
				hbarAdjustments = HbarAdjustments.fromGrpc(record.getTransferList());
			}

			SolidityFnResult callResult = null;
			if (record.hasContractCallResult()) {
				callResult = SolidityFnResult.fromGrpc(record.getContractCallResult());
			}

			SolidityFnResult createResult = null;
			if (record.hasContractCreateResult()) {
				createResult = SolidityFnResult.fromGrpc(record.getContractCreateResult());
			}

			return new ExpirableTxnRecord(txnReceipt,
					record.getTransactionHash().toByteArray(), txnId,
					RichInstant.fromGrpc(record.getConsensusTimestamp()),
					record.getMemo(),
					record.getTransactionFee(), hbarAdjustments, callResult, createResult);
		} catch (Exception ex) {
			log.error("Conversion Of TransactionRecord to JTransactionRecord  Failed..", ex);
		}
		return new ExpirableTxnRecord();
	}

	public static List<TransactionRecord> allToGrpc(List<ExpirableTxnRecord> records) {
		return records.stream()
				.map(ExpirableTxnRecord::toGrpc)
				.collect(toList());
	}

	public static TransactionRecord toGrpc(ExpirableTxnRecord expirableTxnRecord) {
		TransactionRecord.Builder builder = TransactionRecord.newBuilder();
		try {
			TransactionReceipt txReceipt = TxnReceipt.convert(expirableTxnRecord.getTxReceipt());
			if (expirableTxnRecord.getTxnId() != null) {
				EntityId payer = expirableTxnRecord.getTxnId().getPayerAccount();
				RichInstant timestamp = expirableTxnRecord.getTxnId().getValidStart();
				if ((payer != null) || (timestamp != null)) {
					builder.setTransactionID(expirableTxnRecord.getTxnId().toGrpc());
				}
			}

			Timestamp timestamp = Timestamp.newBuilder().build();
			if (expirableTxnRecord.getConsensusTimestamp() != null) {
				timestamp = expirableTxnRecord.getConsensusTimestamp().toGrpc();
			}
			builder.setConsensusTimestamp(timestamp)
					.setTransactionFee(expirableTxnRecord.getFee())
					.setReceipt(txReceipt);
			if (expirableTxnRecord.getMemo() != null) {
				builder.setMemo(expirableTxnRecord.getMemo());
			}
			if (expirableTxnRecord.getTxHash().length > 0) {
				builder.setTransactionHash(ByteString.copyFrom(expirableTxnRecord.getTxHash()));
			}
			if (expirableTxnRecord.getHbarAdjustments() != null) {
				builder.setTransferList(expirableTxnRecord.getHbarAdjustments().toGrpc());
			}

			if (expirableTxnRecord.getContractCallResult() != null) {
				var contractCallResult = expirableTxnRecord.getContractCallResult().toGrpc();
				builder.setContractCallResult(contractCallResult);
			}

			if (expirableTxnRecord.getContractCreateResult() != null) {
				ContractFunctionResult contractCreateResult = expirableTxnRecord.getContractCreateResult().toGrpc();
				builder.setContractCreateResult(contractCreateResult);
			}
		} catch (Exception ex) {
			log.error("JTransactionRecord to TransactionRecord Proto Failed..", ex);
		}
		return builder.build();
	}
}
