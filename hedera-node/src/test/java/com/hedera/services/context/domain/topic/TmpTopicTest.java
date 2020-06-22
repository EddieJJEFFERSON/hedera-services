package com.hedera.services.context.domain.topic;

import com.hedera.services.state.merkle.EntityId;
import com.hedera.services.state.merkle.Topic;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hedera.services.legacy.core.jproto.HEntityId;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.legacy.core.jproto.JTimestamp;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@RunWith(JUnitPlatform.class)
class TmpTopicTest {
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

	@Test
	public void readFcMap() throws IOException, NoSuchAlgorithmException {
		// given:
		FCMap<EntityId, Topic> subject = new FCMap<>(new EntityId.Provider(), new Topic.Provider());
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
			var expected = topicFrom(s);

			System.out.println("--- Expected ---");
			System.out.println(expected.toString());
			System.out.println("--- Actual ---");
			System.out.println(actual.toString());

			assertEquals(expected, actual);
		}
	}

	/*
	@Test
	public void dumpFcmap() throws IOException, NoSuchAlgorithmException {
		// setup:
		FCMap<MapKey, Topic> subject = new FCMap<>(MapKey::deserialize, Topic::deserialize);

		// given:
		subject.put(keyFrom(0), topicFrom(0));
		subject.put(keyFrom(1), topicFrom(1));
		subject.put(keyFrom(2), topicFrom(2));

		// when:
		var out = new FCDataOutputStream(Files.newOutputStream(Paths.get("testTopics.fcm")));
		subject.copyTo(out);
	}
	*/

	private Topic topicFrom(int s) throws IOException, NoSuchAlgorithmException {
		long v = 1_234_567L + s * 1_000_000L;
		TopicID id = TopicID.newBuilder().setTopicNum(s).build();
		var topic = new Topic(
				memos[s],
				adminKeys[s],
				submitKeys[s],
				v,
				new HEntityId(s, s, s),
				new JTimestamp(v, s));
		for (int i = 0; i < s; i++) {
			topic.updateRunningHashAndSequenceNumber(
					"Hello world!".getBytes(),
					id,
					Instant.ofEpochSecond(v, i));
		}
		return topic;
	}

	private EntityId idFrom(long s) {
		long t = s + 1;
		return new EntityId(t, 2 * t, 3 * t);
	}
}