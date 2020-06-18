package com.hedera.services.state.merkle;

import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

@RunWith(JUnitPlatform.class)
class TmpStorageTest {
	@Test
	public void readFcmap() throws IOException {
		// given:
		FCMap<BlobMeta, OptionalBlob> subject = new FCMap<>(new BlobMeta.Provider(), new OptionalBlob.Provider());
		// and:
		var in = new SerializableDataInputStream(Files.newInputStream(Paths.get("testStorage.fcm")));

		// when:
		subject.copyFrom(in);
		subject.copyFromExtra(in);

		// then:
		assertEquals(3, subject.size());
		for (int s = 0; s < 3; s++)	 {
			var key = keyFrom(s);
			var expectedBlob = blobFrom(s);
			assertEquals(expectedBlob, subject.get(key));
		}
	}

	private String[] paths = {
			"/a/b123",
			"/b/c234",
			"/c/d345",
	};
	private BlobMeta keyFrom(int s) {
		return new BlobMeta(paths[s]);
	}

	private byte[][] blobs = {
			"\"This was Mr. Bleaney's room. He stayed,".getBytes(),
			"Where like a pillow on a bed".getBytes(),
			"'Twas brillig, and the slithy toves".getBytes(),
	};
	private OptionalBlob blobFrom(int s) {
		return new OptionalBlob(blobs[s]);
	}
}