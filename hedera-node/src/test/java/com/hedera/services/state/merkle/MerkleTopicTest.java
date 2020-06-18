package com.hedera.services.state.merkle;

import com.hedera.services.context.domain.serdes.TopicSerde;
import com.hedera.services.legacy.core.jproto.JAccountID;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.legacy.core.jproto.JTimestamp;
import com.hedera.services.state.merkle.Topic;
import com.hederahashgraph.api.proto.java.TopicID;
import com.swirlds.common.io.SerializableDataInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(JUnitPlatform.class)
class MerkleTopicTest {
	String[] memos = new String[] {
			"First memo",
			"Second memo",
			"Third memo",
	};
	JKey[] adminKeys = new JKey[] {
			null,
			new JEd25519Key("abcdefghijklmnopqrstuvwxyz012345".getBytes()),
			new JKeyList(List.of(new JEd25519Key("ABCDEFGHIJKLMNOPQRSTUVWXYZ543210".getBytes())))
	};
	JKey[] submitKeys = new JKey[] {
			null,
			new JEd25519Key("aBcDeFgHiJkLmNoPqRsTuVwXyZ012345".getBytes()),
			new JKeyList(List.of(new JEd25519Key("AbCdEfGhIjKlMnOpQrStUvWxYz012345".getBytes())))
	};

	@Test
	public void legacyProviderWorks() throws IOException {
		// setup:
		var serde = mock(TopicSerde.class);
		var in = mock(SerializableDataInputStream.class);
		// and:
		Topic.serde = serde;

		given(in.readShort()).willReturn((short)0).willReturn((short)1);

		// when:
		var topic = (Topic)(new Topic.Provider().deserialize(in));

		// then:
		assertNotNull(topic);
		verify(in, times(2)).readShort();
		verify(serde).deserializeV1(argThat(in::equals), any(Topic.class));
	}

	@AfterEach
	public void cleanup() {
		Topic.serde = new TopicSerde();
	}

	@Test
	public void toStringWorks() throws IOException, NoSuchAlgorithmException {
		// expect:
		assertEquals(
				"Topic{"
						+ "memo=First memo, "
						+ "expiry=1234567.0, "
						+ "deleted=false, "
						+ "adminKey=<N/A>, "
						+ "submitKey=<N/A>, "
						+ "runningHash=<N/A>, "
						+ "sequenceNumber=0, "
						+ "autoRenewSecs=1234567, "
						+ "autoRenewAccount=1.2.3}",
				topicFrom(0).toString());
		// and:
		assertEquals(
				"Topic{" +
						"memo=Second memo, " +
						"expiry=2234567.1, " +
						"deleted=false, " +
						"adminKey=" + Topic.readable(adminKeys[1]) + ", " +
						"submitKey=" + Topic.readable(submitKeys[1]) + ", " +
						"runningHash=71bf381c886baf6ac5628662b3abde8d5a88091c6df1ddd20e60b080e8d3a6fb6cc32f3f3ebc609868054bdd2f71c7ba, " +
						"sequenceNumber=1, " +
						"autoRenewSecs=2234567, " +
						"autoRenewAccount=2.4.6}",
				topicFrom(1).toString());
		// and:
		assertEquals(
				"Topic{" +
						"memo=Third memo, " +
						"expiry=3234567.2, " +
						"deleted=false, " +
						"adminKey=" + Topic.readable(adminKeys[2]) + ", " +
						"submitKey=" + Topic.readable(submitKeys[2]) + ", " +
						"runningHash=763845692fe8df8ccac8d93658333073ee935ad351f28a2e5fc96cdbe682b0b62271eff9990b6a2aa988497d53b4d094, " +
						"sequenceNumber=2, " +
						"autoRenewSecs=3234567, " +
						"autoRenewAccount=3.6.9}",
				topicFrom(2).toString());
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