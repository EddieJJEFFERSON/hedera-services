package com.hedera.services.context.domain.serdes;

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
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.ExpirableTxnRecord;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.fcqueue.FCQueue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.hedera.test.utils.TxnUtils.withAdjustments;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT;
import static com.hedera.test.utils.SerdeUtils.*;
import static com.hedera.test.utils.IdUtils.*;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.BDDMockito.*;

@RunWith(JUnitPlatform.class)
public class DomainSerdesTest {
	private DomainSerdes subject = new DomainSerdes();

	@BeforeAll
	public static void setupAll() throws ConstructableRegistryException {
		/* Per Cody, this will be unnecessary at some point. */
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(ExpirableTxnRecord.class, ExpirableTxnRecord::new));
	}

	@Test
	public void readsExpectedForNonNullableSerializable() throws IOException {
		// setup:
		var in = mock(SerializableDataInputStream.class);
		// and:
		var data = new EntityId(1L, 2L, 3L);

		given(in.readBoolean()).willReturn(true);
		given(in.readSerializable()).willReturn(data);

		// when:
		var dataIn = subject.readNullableSerializable(in);

		// then:
		verify(in).readBoolean();
		assertEquals(data, dataIn);
	}

	@Test
	public void readsNullForNullableSerializable() throws IOException {
		// setup:
		var in = mock(SerializableDataInputStream.class);
		// and:
		EntityId data;

		given(in.readBoolean()).willReturn(false);

		// when:
		data = subject.readNullableSerializable(in);

		// then:
		verify(in).readBoolean();
		verifyNoMoreInteractions(in);
		// and:
		assertNull(data);
	}

	@Test
	public void writesFalseForNullWritable() throws IOException {
		// setup:
		var out = mock(SerializableDataOutputStream.class);
		// and:
		var writer = mock(BiConsumer.class);

		// when:
		subject.writeNullable(null, out, (BiConsumer<? extends Object, SerializableDataOutputStream>)writer);

		// then:
		verify(out).writeBoolean(false);
		verifyNoMoreInteractions(out);
		verifyNoInteractions(writer);
	}

	@Test
	public void readsNullForNullReadable() throws IOException {
		// setup:
		var in = mock(SerializableDataInputStream.class);
		// and:
		var reader = mock(Function.class);

		given(in.readBoolean()).willReturn(false);

		// when:
		Object data = subject.readNullable(in, reader);

		// then:
		verify(in).readBoolean();
		verifyNoMoreInteractions(in);
		// and:
		assertNull(data);
	}

	@Test
	public void readsExpectedForNonNullReadable() throws IOException {
		// setup:
		var in = mock(SerializableDataInputStream.class);
		// and:
		var reader = (Function<SerializableDataInputStream, EntityId>)mock(Function.class);
		var data = new EntityId(1L, 2L, 3L);

		given(in.readBoolean()).willReturn(true);
		given(reader.apply(in)).willReturn(data);

		// when:
		EntityId dataIn = subject.readNullable(in, reader);

		// then:
		verify(in).readBoolean();
		assertEquals(data, dataIn);
	}

	@Test
	public void writesForNonNullWritable() throws IOException {
		// setup:
		var out = mock(SerializableDataOutputStream.class);
		// and:
		var writer = mock(BiConsumer.class);

		// given:
		var data = new EntityId(1L, 2L, 3L);

		// when:
		subject.writeNullable(data, out, writer);

		// then:
		verify(out).writeBoolean(true);
		verify(writer).accept(data, out);
	}

	@Test
	public void writesFalseForNullSerializable() throws IOException {
		// setup:
		var out = mock(SerializableDataOutputStream.class);

		// when:
		subject.writeNullableSerializable(null, out);

		// then:
		verify(out).writeBoolean(false);
		verifyNoMoreInteractions(out);
	}

	@Test
	public void writesExpectedForNonNullSerializable() throws IOException {
		// setup:
		var out = mock(SerializableDataOutputStream.class);
		// and:
		var data = new EntityId(1L, 2L, 3L);

		// when:
		subject.writeNullableSerializable(data, out);

		// then:
		verify(out).writeBoolean(true);
		verify(out).writeSerializable(data, true);
	}


	@Test
	public void idSerdesWork() throws Exception {
		// given:
		EntityId idIn = new EntityId(1,2, 3);

		// when:
		byte[] repr = serOutcome(out -> subject.serializeId(idIn, out));
		// and:
		EntityId idOut = deOutcome(in -> subject.deserializeId(in), repr);

		// then:
		assertEquals(idIn, idOut);
	}

	@Test
	public void keySerdesWork() throws Exception {
		// given:
		JKey keyIn = COMPLEX_KEY_ACCOUNT_KT.asJKey();

		// when:
		byte[] repr = serOutcome(out -> subject.serializeKey(keyIn, out));
		// and:
		JKey keyOut = deOutcome(in -> subject.deserializeKey(in), repr);

		// then:
		assertEquals(JKey.mapJKey(keyIn), JKey.mapJKey(keyOut));
	}


	public static ExpirableTxnRecord recordOne() {
		TransactionRecord record = TransactionRecord.newBuilder()
				.setReceipt(TransactionReceipt.newBuilder()
						.setStatus(INVALID_ACCOUNT_ID)
						.setAccountID(asAccount("0.0.3")))
				.setTransactionID(TransactionID.newBuilder()
						.setTransactionValidStart(Timestamp.newBuilder().setSeconds(9_999_999_999L)))
				.setMemo("Alpha bravo charlie")
				.setConsensusTimestamp(Timestamp.newBuilder().setSeconds(9_999_999_999L))
				.setTransactionFee(555L)
				.setTransferList(withAdjustments(
						asAccount("0.0.2"), -4L,
						asAccount("0.0.1001"), 2L,
						asAccount("0.0.1002"), 2L))
				.setContractCallResult(ContractFunctionResult.newBuilder()
						.setContractID(asContract("1.2.3"))
						.setErrorMessage("Couldn't figure it out!")
						.setGasUsed(55L)
						.addLogInfo(ContractLoginfo.newBuilder()
								.setData(ByteString.copyFrom("Nonsense!".getBytes()))))
				.build();
		ExpirableTxnRecord jRecord = ExpirableTxnRecord.fromGprc(record);
		return jRecord;
	}

	public static ExpirableTxnRecord recordTwo() {
		TransactionRecord record = TransactionRecord.newBuilder()
				.setReceipt(TransactionReceipt.newBuilder()
						.setStatus(INVALID_CONTRACT_ID)
						.setAccountID(asAccount("0.0.4")))
				.setTransactionID(TransactionID.newBuilder()
						.setTransactionValidStart(Timestamp.newBuilder().setSeconds(7_777_777_777L)))
				.setMemo("Alpha bravo charlie")
				.setConsensusTimestamp(Timestamp.newBuilder().setSeconds(7_777_777_777L))
				.setTransactionFee(556L)
				.setTransferList(withAdjustments(
						asAccount("0.0.2"),-6L,
						asAccount("0.0.1001"), 3L,
						asAccount("0.0.1002"), 3L))
				.setContractCallResult(ContractFunctionResult.newBuilder()
						.setContractID(asContract("4.3.2"))
						.setErrorMessage("Couldn't figure it out immediately!")
						.setGasUsed(55L)
						.addLogInfo(ContractLoginfo.newBuilder()
								.setData(ByteString.copyFrom("Nonsensical!".getBytes()))))
				.build();
		ExpirableTxnRecord jRecord = ExpirableTxnRecord.fromGprc(record);
		return jRecord;
	}
}
