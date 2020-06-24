package com.hedera.services.legacy.core.jproto;

import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.SolidityFnResult;
import com.hedera.test.utils.IdUtils;
import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransferList;
import com.swirlds.common.io.SerializableDataInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.List;

import static com.hedera.services.state.submerkle.EntityId.ofNullableAccountId;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@RunWith(JUnitPlatform.class)
class HbarAdjustmentsTest {
	AccountID a = IdUtils.asAccount("0.0.13257");
	EntityId aEntity = EntityId.ofNullableAccountId(a);
	AccountID b = IdUtils.asAccount("0.0.13258");
	EntityId bEntity = EntityId.ofNullableAccountId(b);
	AccountID c = IdUtils.asAccount("0.0.13259");
	EntityId cEntity = EntityId.ofNullableAccountId(c);

	long aAmount = 1L, bAmount = 2L, cAmount = -3L;

	TransferList grpcAdjustments = TxnUtils.withAdjustments(a, aAmount, b, bAmount, c, cAmount);

	DataInputStream din;
	EntityId.Provider idProvider;

	HbarAdjustments subject;

	@BeforeEach
	public void setup() {
		din = mock(DataInputStream.class);
		idProvider = mock(EntityId.Provider.class);

		HbarAdjustments.legacyIdProvider = idProvider;

		subject = new HbarAdjustments();
		subject.adjustments = List.of(
				new HbarAdjustments.Adjustment(aAmount, ofNullableAccountId(a)),
				new HbarAdjustments.Adjustment(bAmount, ofNullableAccountId(b)),
				new HbarAdjustments.Adjustment(cAmount, ofNullableAccountId(c)));
	}

	@AfterEach
	public void cleanup() {
		HbarAdjustments.legacyIdProvider = EntityId.LEGACY_PROVIDER;
	}

	@Test
	public void legacyProviderWorks() throws IOException {
		given(din.readLong())
				.willReturn(-1L).willReturn(-2L)
				.willReturn(-1L).willReturn(-2L).willReturn(aAmount)
				.willReturn(-1L).willReturn(-2L).willReturn(bAmount)
				.willReturn(-1L).willReturn(-2L).willReturn(cAmount);
		given(din.readInt()).willReturn(3);
		given(idProvider.deserialize(din))
				.willReturn(aEntity)
				.willReturn(bEntity)
				.willReturn(cEntity);

		// when:
		var subjectRead = HbarAdjustments.LEGACY_PROVIDER.deserialize(din);

		// then:
		assertEquals(subject, subjectRead);
	}

	@Test
	public void viewWorks() {
		// expect:
		assertEquals(grpcAdjustments, subject.toGrpc());
	}

	@Test
	public void factoryWorks() {
		// expect:
		assertEquals(subject, HbarAdjustments.fromGrpc(grpcAdjustments));
	}

	@Test
	public void serializableDetWorks() {
		// expect;
		assertEquals(HbarAdjustments.MERKLE_VERSION, subject.getVersion());
		assertEquals(HbarAdjustments.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
	}
}