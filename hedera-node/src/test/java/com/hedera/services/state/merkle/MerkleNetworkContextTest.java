package com.hedera.services.state.merkle;

import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.state.submerkle.SequenceNumber;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.InOrder;

import java.io.IOException;
import java.time.Instant;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.booleanThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.mock;

@RunWith(JUnitPlatform.class)
class MerkleNetworkContextTest {
	Instant consensusTimeOfLastHandledTxn;
	SequenceNumber seqNo;
	SequenceNumber seqNoCopy;
	ExchangeRates midnightRateSet;
	ExchangeRates midnightRateSetCopy;

	MerkleNetworkContext subject;

	@BeforeEach
	private void setup() {
		consensusTimeOfLastHandledTxn = Instant.now();

		seqNo = mock(SequenceNumber.class);
		seqNoCopy = mock(SequenceNumber.class);
		given(seqNo.copy()).willReturn(seqNoCopy);
		midnightRateSet = mock(ExchangeRates.class);
		midnightRateSetCopy = mock(ExchangeRates.class);
		given(midnightRateSet.copy()).willReturn(midnightRateSetCopy);

		subject = new MerkleNetworkContext(consensusTimeOfLastHandledTxn, seqNo, midnightRateSet);
	}

	@Test
	public void copyWorks() {
		// given:
		var subjectCopy = subject.copy();

		// expect:
		assertTrue(subjectCopy.consensusTimeOfLastHandledTxn == subject.consensusTimeOfLastHandledTxn);
		assertEquals(seqNoCopy, subjectCopy.seqNo);
		assertEquals(midnightRateSetCopy, subjectCopy.midnightRateSet);
	}

	@Test
	public void deserializeWorks() throws IOException {
		// setup:
		var in = mock(SerializableDataInputStream.class);
		MerkleNetworkContext.ratesSupplier = () -> midnightRateSet;
		MerkleNetworkContext.seqNoSupplier = () -> seqNo;
		InOrder inOrder = inOrder(in, midnightRateSet, seqNo);

		given(in.readLong()).willReturn(consensusTimeOfLastHandledTxn.getEpochSecond());
		given(in.readInt()).willReturn(consensusTimeOfLastHandledTxn.getNano());

		// when:
		subject.deserialize(in, MerkleNetworkContext.MERKLE_VERSION);

		// then:
		assertEquals(consensusTimeOfLastHandledTxn, subject.consensusTimeOfLastHandledTxn);
		// and:
		inOrder.verify(seqNo).deserialize(in);
		inOrder.verify(in).readSerializable(booleanThat(Boolean.TRUE::equals), any(Supplier.class));
	}

	@Test
	public void serializeWorks() throws IOException {
		// setup:
		var out = mock(SerializableDataOutputStream.class);
		InOrder inOrder = inOrder(out, seqNo, midnightRateSet);

		// when:
		subject.serialize(out);

		// expect:
		inOrder.verify(out).writeLong(consensusTimeOfLastHandledTxn.getEpochSecond());
		inOrder.verify(out).writeInt(consensusTimeOfLastHandledTxn.getNano());
		inOrder.verify(seqNo).serialize(out);
		inOrder.verify(midnightRateSet).serialize(out);
	}

	@Test
	public void sanityChecks() {
		assertEquals(MerkleNetworkContext.MERKLE_VERSION, subject.getVersion());
		assertEquals(MerkleNetworkContext.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
	}
}