package com.hedera.services.state.merkle;

import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.InOrder;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.Mockito.inOrder;

@RunWith(JUnitPlatform.class)
class BlobMetaTest {
	String path = "/a/b/c123";

	BlobMeta subject;

	@BeforeEach
	private void setup() {
		subject = new BlobMeta(path);
	}

	@Test
	public void merkleMethodsWork() {
		// expect;
		assertEquals(BlobMeta.MERKLE_VERSION, subject.getVersion());
		assertEquals(BlobMeta.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
		assertTrue(subject.isLeaf());
	}

	@Test
	public void deserializeWorks() throws IOException {
		// setup:
		var in = mock(SerializableDataInputStream.class);
		// and:
		var defaultSubject = new BlobMeta();

		given(in.readNormalisedString(BlobMeta.MAX_PATH_LEN)).willReturn(path);

		// when:
		defaultSubject.deserialize(in, BlobMeta.MERKLE_VERSION);

		// then:
		assertEquals(subject, defaultSubject);
	}

	@Test
	public void serializeWorks() throws IOException {
		// setup:
		var out = mock(SerializableDataOutputStream.class);
		// and:
		InOrder inOrder = inOrder(out);

		// when:
		subject.serialize(out);

		// then:
		inOrder.verify(out).writeNormalisedString(path);
	}

	@Test
	public void legacyProviderWorks() throws IOException {
		// setup:
		var in = mock(SerializableDataInputStream.class);

		given(in.readLong()).willReturn(0l).willReturn(1l);
		given(in.readNormalisedString(BlobMeta.MAX_PATH_LEN)).willReturn(path);

		// when:
		var deSubject = (BlobMeta)(new BlobMeta.Provider().deserialize(in));

		// then:
		assertEquals(deSubject, subject);
	}

	@Test
	public void objectContractMet() {
		// given:
		var one = new BlobMeta();
		var two = new BlobMeta(path);
		var three = new BlobMeta();

		// when:
		three.setPath(path);

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
		var defaultSubject = new BlobMeta();

		// expect:
		assertThrows(UnsupportedOperationException.class, () -> defaultSubject.copyTo(null));
		assertThrows(UnsupportedOperationException.class, () -> defaultSubject.copyToExtra(null));
		assertThrows(UnsupportedOperationException.class, () -> defaultSubject.copyFrom(null));
		assertThrows(UnsupportedOperationException.class, () -> defaultSubject.copyFromExtra(null));
		assertThrows(UnsupportedOperationException.class, () -> defaultSubject.diffCopyTo(null, null));
		assertThrows(UnsupportedOperationException.class, () -> defaultSubject.diffCopyFrom(null, null));
	}

	@Test
	public void toStringWorks() {
		// expect:
		assertEquals(
				"BlobMeta{path=" + path + "}",
				subject.toString());
	}

	@Test
	public void copyWorks() {
		// when:
		var subjectCopy = subject.copy();

		// then:
		assertTrue(subjectCopy != subject);
		assertEquals(subject, subjectCopy);
	}

	@Test
	public void deleteIsNoop() {
		// expect:
		assertDoesNotThrow(subject::delete);
	}
}