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

import com.hedera.services.context.domain.serdes.DomainSerdes;
import com.hedera.services.legacy.core.jproto.HEntityId;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JTransactionRecord;
import com.hedera.services.legacy.exception.NegativeAccountBalanceException;
import com.hedera.services.legacy.logic.ApplicationConstants;
import com.hedera.services.state.merkle.AccountState;
import com.swirlds.fcqueue.FCQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static com.hedera.services.context.domain.serdes.DomainSerdesTest.recordOne;
import static com.hedera.services.context.domain.serdes.DomainSerdesTest.recordTwo;
import static com.hedera.services.legacy.core.jproto.JKey.equalUpToDecodability;
import static java.util.Comparator.comparingLong;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.*;

@RunWith(JUnitPlatform.class)
class MerkleAccountTest {
	JKey key = new JEd25519Key("abcdefghijklmnopqrstuvwxyz012345".getBytes());
	long expiry = 1_234_567L;
	long balance = 555_555L;
	long autoRenewSecs = 234_567L;
	long senderThreshold = 1_234L;
	long receiverThreshold = 4_321L;
	String memo = "A memo";
	boolean deleted = true;
	boolean smartContract = true;
	boolean receiverSigRequired = true;
	HEntityId proxy = new HEntityId(1L, 2L, 3L);

	JKey otherKey = new JEd25519Key("aBcDeFgHiJkLmNoPqRsTuVwXyZ012345".getBytes());
	long otherExpiry = 7_234_567L;
	long otherBalance = 666_666L;
	long otherAutoRenewSecs = 432_765L;
	long otherSenderThreshold = 4_321L;
	long otherReceiverThreshold = 1_234L;
	String otherMemo = "Another memo";
	boolean otherDeleted = false;
	boolean otherSmartContract = false;
	boolean otherReceiverSigRequired = false;
	HEntityId otherProxy = new HEntityId(3L, 2L, 1L);

	AccountState state;
	AccountState otherState;
	FCQueue<JTransactionRecord> records;
	FCQueue<JTransactionRecord> payerRecords;
	DomainSerdes serdes;

	HederaAccount subject;

	public static void offerRecordsInOrder(HederaAccount account, List<JTransactionRecord> _records) {
		List<JTransactionRecord> recordList = new ArrayList<>(_records);
		recordList.sort(comparingLong(JTransactionRecord::getExpirationTime));
		var records = account.records();
		for (JTransactionRecord record : recordList) {
			records.offer(record);
		}
	}

	@BeforeEach
	public void setup() {
		serdes = mock(DomainSerdes.class);
		HederaAccount.serdes = serdes;

		records = mock(FCQueue.class);
		given(records.copy(anyBoolean())).willReturn(records);
		given(records.isImmutable()).willReturn(false);
		payerRecords = mock(FCQueue.class);
		given(payerRecords.copy(anyBoolean())).willReturn(payerRecords);
		given(payerRecords.isImmutable()).willReturn(false);

		state = new AccountState(
				key,
				expiry, balance, autoRenewSecs, senderThreshold, receiverThreshold,
				memo,
				deleted, smartContract, receiverSigRequired,
				proxy);
		otherState = new AccountState(
				otherKey,
				otherExpiry, otherBalance, otherAutoRenewSecs, otherSenderThreshold, otherReceiverThreshold,
				otherMemo,
				otherDeleted, otherSmartContract, otherReceiverSigRequired,
				otherProxy);

		subject = new HederaAccount(List.of(state, records, payerRecords));
	}

	@AfterEach
	public void cleanup() {
		HederaAccount.serdes = new DomainSerdes();
	}

	@Test
	public void merkleMethodsWork() {
		// expect;
		assertEquals(HederaAccount.NUM_V1_CHILDREN, subject.getMinimumChildCount(HederaAccount.MERKLE_VERSION));
		assertEquals(HederaAccount.MERKLE_VERSION, subject.getVersion());
		assertEquals(HederaAccount.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
		assertFalse(subject.isLeaf());
	}

	@Test
	public void toStringWorks() {
		given(records.size()).willReturn(2);
		given(payerRecords.size()).willReturn(3);

		// expect:
		assertEquals(
				"HederaAccount{state=" + state.toString()
						+ ", # records=" + 2
						+ ", # payer records=" + 3 + "}",
				subject.toString());
	}

	@Test
	public void gettersDelegate() {
		// expect:
		assertEquals(state.expiry(), subject.getExpiry());
		assertEquals(state.balance(), subject.getBalance());
		assertEquals(state.autoRenewSecs(), subject.getAutoRenewSecs());
		assertEquals(state.senderThreshold(), subject.getSenderThreshold());
		assertEquals(state.receiverThreshold(), subject.getReceiverThreshold());
		assertEquals(state.isDeleted(), subject.isDeleted());
		assertEquals(state.isSmartContract(), subject.isSmartContract());
		assertEquals(state.isReceiverSigRequired(), subject.isReceiverSigRequired());
		assertEquals(state.memo(), subject.getMemo());
		assertEquals(state.proxy(), subject.getProxy());
		assertTrue(equalUpToDecodability(state.key(), subject.getKey()));
	}

	@Test
	public void settersDelegate() throws NegativeAccountBalanceException {
		// when:
		subject.setExpiry(otherExpiry);
		subject.setBalance(otherBalance);
		subject.setAutoRenewSecs(otherAutoRenewSecs);
		subject.setSenderThreshold(otherSenderThreshold);
		subject.setReceiverThreshold(otherReceiverThreshold);
		subject.setDeleted(otherDeleted);
		subject.setSmartContract(otherSmartContract);
		subject.setReceiverSigRequired(otherReceiverSigRequired);
		subject.setMemo(otherMemo);
		subject.setProxy(otherProxy);
		subject.setKey(otherKey);

		// then:
		assertEquals(otherState, subject.state());
	}

	@Test
	public void unsupportedOperationsThrow() {
		// expect:
		assertThrows(UnsupportedOperationException.class, () -> subject.copyTo(null));
		assertThrows(UnsupportedOperationException.class, () -> subject.copyToExtra(null));
		assertThrows(UnsupportedOperationException.class, () -> subject.copyFrom(null));
		assertThrows(UnsupportedOperationException.class, () -> subject.copyFromExtra(null));
		assertThrows(UnsupportedOperationException.class, () -> subject.diffCopyTo(null, null));
		assertThrows(UnsupportedOperationException.class, () -> subject.diffCopyFrom(null, null));
	}

	@Test
	public void objectContractMet() {
		// given:
		var one = new HederaAccount();
		var two = new HederaAccount(List.of(state, payerRecords, records));
		var three = new HederaAccount(two);

		// then:
		verify(records, never()).copy(anyBoolean());
		verify(payerRecords, never()).copy(anyBoolean());
		assertNotEquals(null, one);
		assertNotEquals(new Object(), one);
		assertNotEquals(two, one);
		assertEquals(one, one);
		assertEquals(two, three);
		// and:
		assertNotEquals(one.hashCode(), two.hashCode());
		assertEquals(two.hashCode(), three.hashCode());
	}

	@Test
	public void copyConstructorFastCopiesMutableFcqs() {
		given(records.isImmutable()).willReturn(false);
		given(payerRecords.isImmutable()).willReturn(false);

		// when:
		var copy = subject.copy();

		// then:
		verify(records).copy(false);
		verify(payerRecords).copy(false);
		// and:
		assertEquals(records, copy.records());
		assertEquals(payerRecords, copy.payerRecords());
	}

	@Test
	public void copyConstructorReusesImmutableFcqs() {
		given(records.isImmutable()).willReturn(true);
		given(payerRecords.isImmutable()).willReturn(true);

		// when:
		var copy = subject.copy();

		// then:
		verify(records, never()).copy(anyBoolean());
		verify(payerRecords, never()).copy(anyBoolean());
		// and:
		assertEquals(records, copy.records());
		assertEquals(payerRecords, copy.payerRecords());
	}

	@Test
	public void recordsHelpersWork() {
		// setup:
		var defaultAccount = new HederaAccount();
		defaultAccount.setRecords(new FCQueue<>(JTransactionRecord::deserialize));

		// given:
		offerRecordsInOrder(defaultAccount, List.of(recordOne(), recordTwo()));

		// when:
		Iterator<JTransactionRecord> iterator = defaultAccount.recordIterator();
		// and:
		var recordsCopy = defaultAccount.recordList();

		// then:
		assertEquals(recordOne(), iterator.next());
		assertEquals(recordTwo(), iterator.next());
		assertFalse(iterator.hasNext());
		// and:
		assertEquals(List.of(recordOne(), recordTwo()), recordsCopy);
	}

	@Test
	public void throwsOnNegativeBalance() {
		// expect:
		assertThrows(NegativeAccountBalanceException.class, () -> subject.setBalance(-1L));
	}

	@Test
	public void legacyProviderWorks() throws IOException {
		// setup:
		var expectedState = new AccountState(
				key,
				expiry, balance, autoRenewSecs, senderThreshold, receiverThreshold,
				memo,
				deleted, smartContract, receiverSigRequired,
				proxy);
		// and:
		var in = mock(DataInputStream.class);

		given(in.readLong())
				.willReturn(1L)
				.willReturn(2L)
				.willReturn(balance)
				.willReturn(senderThreshold)
				.willReturn(receiverThreshold)
				.willReturn(autoRenewSecs)
				.willReturn(expiry);
		given(in.readChar())
				.willReturn(ApplicationConstants.P);
		given(in.readUTF())
				.willReturn(memo);
		given(in.readByte())
				.willReturn((byte)(receiverSigRequired ? 1 : 0))
				.willReturn((byte)1)
				.willReturn((byte)(smartContract ? 1 : 0));
		given(serdes.deserializeKey(in)).willReturn(key);
		given(serdes.deserializeId(in)).willReturn(proxy);
		will(invoke -> {
			@SuppressWarnings("unchecked")
			FCQueue<JTransactionRecord> records = invoke.getArgument(1);
			records.offer(new JTransactionRecord());
			records.offer(new JTransactionRecord());
			return null;
		}).given(serdes).deserializeIntoRecords(argThat(in::equals), any());

		// when:
		var providedSubject = (HederaAccount)new HederaAccount.Provider().deserialize(in);

		// then:
		assertEquals(expectedState, providedSubject.state());
		// and:
		assertEquals(2, providedSubject.records().size());
	}

	@Test
	public void isMutable() {
		// expect:
		assertFalse(subject.isImmutable());
	}

	@Test
	public void delegatesDelete() {
		// when:
		subject.delete();

		// then:
		verify(records).delete();
		verify(payerRecords).delete();
	}

	@Test
	public void negOneIfEmptyRecords() {
		given(records.isEmpty()).willReturn(true);

		// then:
		assertEquals(-1, subject.expiryOfEarliestRecord());
	}

	@Test
	public void getsEarliestExpiry() {
		// setup:
		var record = mock(JTransactionRecord.class);

		given(record.getExpirationTime()).willReturn(expiry);
		given(records.peek()).willReturn(record);

		// then:
		assertEquals(expiry, subject.expiryOfEarliestRecord());
	}
}