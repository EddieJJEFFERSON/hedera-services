package com.hedera.services.context;

import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.state.submerkle.SequenceNumber;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.utility.AbstractMerkleNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.time.Instant;
import java.util.function.Supplier;

public class NetworkContext extends AbstractMerkleNode implements MerkleLeaf {
	private static final Logger log = LogManager.getLogger(NetworkContext.class);

	public static final Instant UNKNOWN_CONSENSUS_TIME = null;

	static final int MERKLE_VERSION = 1;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0x8d4aa0f0a968a9f3L;

	static Supplier<ExchangeRates> ratesSupplier = ExchangeRates::new;
	static Supplier<SequenceNumber> seqNoSupplier = SequenceNumber::new;

	Instant consensusTimeOfLastHandledTxn;
	SequenceNumber seqNo;
	ExchangeRates midnightRateSet;

	public NetworkContext() {}

	public NetworkContext(
			Instant consensusTimeOfLastHandledTxn,
			SequenceNumber seqNo,
			ExchangeRates midnightRateSet
	) {
		this.consensusTimeOfLastHandledTxn = consensusTimeOfLastHandledTxn;
		this.seqNo = seqNo;
		this.midnightRateSet = midnightRateSet;
	}

	public void setConsensusTimeOfLastHandledTxn(Instant consensusTimeOfLastHandledTxn) {
		this.consensusTimeOfLastHandledTxn = consensusTimeOfLastHandledTxn;
	}

	public NetworkContext copy() {
		return new NetworkContext(consensusTimeOfLastHandledTxn, seqNo.copy(), midnightRateSet.copy());
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		consensusTimeOfLastHandledTxn = Instant.ofEpochSecond(in.readLong(), in.readInt());
		seqNo = seqNoSupplier.get();
		seqNo.deserialize(in);
		midnightRateSet = ratesSupplier.get();
		midnightRateSet.deserialize(in);
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeLong(consensusTimeOfLastHandledTxn.getEpochSecond());
		out.writeInt(consensusTimeOfLastHandledTxn.getNano());
		seqNo.serialize(out);
		midnightRateSet.serialize(out);
	}

	public Instant consensusTimeOfLastHandledTxn() {
		return consensusTimeOfLastHandledTxn;
	}

	public SequenceNumber seqNo() {
		return seqNo;
	}

	public ExchangeRates midnightRates() {
		return midnightRateSet;
	}

	@Override
	public long getClassId() {
		return RUNTIME_CONSTRUCTABLE_ID;
	}

	@Override
	public int getVersion() {
		return MERKLE_VERSION;
	}
}
