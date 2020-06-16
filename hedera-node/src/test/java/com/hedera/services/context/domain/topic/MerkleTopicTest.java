package com.hedera.services.context.domain.topic;

import com.hedera.services.legacy.core.jproto.JAccountID;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.legacy.core.jproto.JTimestamp;
import com.hederahashgraph.api.proto.java.TopicID;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;

import com.swirlds.common.io.SerializableDataInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.io.DataInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

@RunWith(JUnitPlatform.class)
class MerkleTopicTest {
	static int N = 3;

	static String[] memos = new String[] {
			"First memo",
			"Second memo",
			"Third memo",
	};
	static JKey[] adminKeys = new JKey[] {
			null,
			new JEd25519Key("abcdefghijklmnopqrstuvwxyz012345".getBytes()),
			new JKeyList(List.of(new JEd25519Key("ABCDEFGHIJKLMNOPQRSTUVWXYZ543210".getBytes())))
	};
	static JKey[] submitKeys = new JKey[] {
			null,
			new JEd25519Key("aBcDeFgHiJkLmNoPqRsTuVwXyZ012345".getBytes()),
			new JKeyList(List.of(new JEd25519Key("AbCdEfGhIjKlMnOpQrStUvWxYz012345".getBytes())))
	};

	Topic subject;

	@Test
	public void toStringWorks() throws IOException, NoSuchAlgorithmException {
		// given:
		subject = topicFrom(0);

		// when:
		var readable = subject.toString();

		// then:
		assertEquals(
				"Topic{"
						+ "memo=Hello world!, "
						+ "expiry=1234567, "
						+ "deleted=false, "
						+ "adminKey=<N/A>, "
						+ "submitKey=<N/A>, "
						+ "runningHash=<N/A>, "
						+ "sequenceNumber=0, "
						+ "autoRenewSecs=1234567, "
						+ "autoRenewAccount=1.2.3}",
				readable
		);
	}

	private Topic topicFrom(int s) throws IOException, NoSuchAlgorithmException {
		long v = 1_234_567L + s * 1_000_000L;
		long t = s + 1;
		TopicID id = TopicID.newBuilder().setTopicNum(s).build();
		var topic = new Topic(
				memos[s],
				adminKeys[s],
				submitKeys[s],
				v,
				new JAccountID(t, t * 2, t * 3),
				new JTimestamp(v, s));
		for (int i = 0; i < s; i++) {
			topic.updateRunningHashAndSequenceNumber(
					"Hello world!".getBytes(),
					id,
					Instant.ofEpochSecond(v, i));
		}
		return topic;
	}
}