package com.hedera.services.state.merkle;

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
class EntityIdTest {
	long shard = 13;
	long realm = 25;
	long num = 7;

	EntityId subject;

	@BeforeEach
	private void setup() {
		subject = new EntityId(shard, realm, num);
	}

	@Test
	public void objectContractMet() {
		// given:
		var one = new EntityId();
		var two = new EntityId(1, 2,3);
		var three = new EntityId();

		// when:
		three.setShardNum(1);
		three.setRealmNum(2);
		three.setIdNum(3);

		// then:
		assertNotEquals(null, one);
		assertNotEquals(two, one);
		assertEquals(one, one);
		assertEquals(two, three);
		// and:
		assertNotEquals(one.hashCode(), two.hashCode());
		assertEquals(two.hashCode(), three.hashCode());
	}

	@Test
	public void unsupportedOperationsThrow() {
		// given:
		var defaultSubject = new EntityId();

		// expect:
		assertThrows(UnsupportedOperationException.class, () -> defaultSubject.copyTo(null));
		assertThrows(UnsupportedOperationException.class, () -> defaultSubject.copyToExtra(null));
		assertThrows(UnsupportedOperationException.class, () -> defaultSubject.copyFrom(null));
		assertThrows(UnsupportedOperationException.class, () -> defaultSubject.copyFromExtra(null));
		assertThrows(UnsupportedOperationException.class, () -> defaultSubject.diffCopyTo(null, null));
		assertThrows(UnsupportedOperationException.class, () -> defaultSubject.diffCopyFrom(null, null));
	}

	@Test
	public void legacyProviderWorks() throws IOException {
		// setup:
		var in = mock(DataInputStream.class);

		given(in.readLong()).willReturn(0l).willReturn(0l).willReturn(1l).willReturn(2l).willReturn(3l);

		// when:
		var id = (EntityId)(new EntityId.Provider().deserialize(in));

		// then:
		assertEquals(new EntityId(2, 1, 3), id);
	}

	@Test
	public void merkleMethodsWork() {
		// expect;
		assertEquals(EntityId.MERKLE_VERSION, subject.getVersion());
		assertEquals(EntityId.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
		assertTrue(subject.isLeaf());
	}

	@Test
	public void deserializeWorks() throws IOException {
		// setup:
		var in = mock(SerializableDataInputStream.class);
		// and:
		var defaultSubject = new EntityId();

		given(in.readLong()).willReturn(shard).willReturn(realm).willReturn(num);

		// when:
		defaultSubject.deserialize(in, EntityId.MERKLE_VERSION);

		// then:
		assertEquals(subject, defaultSubject);
	}
}