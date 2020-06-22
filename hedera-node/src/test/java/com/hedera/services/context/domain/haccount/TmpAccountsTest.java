package com.hedera.services.context.domain.haccount;

import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.legacy.core.jproto.HEntityId;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.legacy.core.jproto.JThresholdKey;
import com.hedera.services.legacy.core.jproto.JTransactionRecord;
import com.hedera.services.state.merkle.EntityId;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import static com.hedera.services.context.domain.serdes.DomainSerdesTest.recordOne;
import static com.hedera.services.context.domain.serdes.DomainSerdesTest.recordTwo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RunWith(JUnitPlatform.class)
class TmpAccountsTest {
//	@Test
//	public void dumpFcmap() throws Exception {
//		// setup:
//		FCMap<MapKey, HederaAccount> subject =
//				new FCMap<>(MapKey::deserialize, HederaAccount::deserialize);
//
//		// given:
//		for (int s = 0; s < 3; s++) {
//			subject.put(keyFrom(s), accountFrom(s));
//		}
//
//		// when:
//		var out = new FCDataOutputStream(Files.newOutputStream(Paths.get("testAccounts.fcm")));
//		subject.copyTo(out);
//		subject.copyToExtra(out);
//	}

	int N = 3;

	@Test
	public void readFcMap() throws Exception {
		// given:
		FCMap<EntityId, HederaAccount> subject = null;
//				new FCMap<>(new EntityId.Provider(), HederaAccount::deserialize);
		// and:
		var in = new SerializableDataInputStream(Files.newInputStream(Paths.get("testTopics.fcm")));

		// when:
		subject.copyFrom(in);
		subject.copyFromExtra(in);

		// then:
		assertEquals(subject.size(), N);
		for (int s = 0; s < N; s++) {
			var id = idFrom(s);
			assertTrue(subject.containsKey(id));
			var actual = subject.get(id);
			var expected = accountFrom(s);

			System.out.println("--- Expected ---");
			System.out.println(expected.toString());
			System.out.println("--- Actual ---");
			System.out.println(actual.toString());

			assertEquals(expected, actual);
		}
	}

	private EntityId idFrom(long s) {
		long t = s + 1;
		return new EntityId(t, 2 * t, 3 * t);
	}

	String[] memos = new String[] {
			"\"This was Mr. Bleaney's room. He stayed,",
			"Where like a pillow on a bed",
			"'Twas brillig, and the slithy toves",
	};
	JKey[] keys = new JKey[] {
			new JEd25519Key("abcdefghijklmnopqrstuvwxyz012345".getBytes()),
			new JKeyList(List.of(new JEd25519Key("ABCDEFGHIJKLMNOPQRSTUVWXYZ543210".getBytes()))),
			new JThresholdKey(
					new JKeyList(List.of(new JEd25519Key("ABCDEFGHIJKLMNOPQRSTUVWXYZ543210".getBytes()))),
					1)
	};
	List<List<JTransactionRecord>> records = List.of(
			Collections.emptyList(),
			List.of(recordOne()),
			List.of(recordOne(), recordTwo()));

	private HederaAccount accountFrom(int s) throws Exception {
		long v = s + 1;
		HederaAccount account = new HederaAccountCustomizer()
				.proxy(new HEntityId(v,2 * v, 3 * v))
				.key(keys[s])
				.memo(memos[s])
				.isSmartContract(s % 2 != 0)
				.isDeleted(s == 2)
				.isReceiverSigRequired(s == 0)
				.fundsSentRecordThreshold(v * 1_234L)
				.fundsReceivedRecordThreshold(v * 5_432L)
				.expiry(1_234_567_890L + v)
				.autoRenewPeriod(666L * v)
				.customizing(new HederaAccount());
		account.setBalance(888L * v);
		account.resetRecordsToContain(records.get(s));
		return account;
	}
}