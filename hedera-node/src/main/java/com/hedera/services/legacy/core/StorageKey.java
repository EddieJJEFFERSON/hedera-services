package com.hedera.services.legacy.core;

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

import com.swirlds.common.CommonUtils;
import com.swirlds.common.FCMKey;
import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.utility.AbstractMerkleNode;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * @author Plynn
 * @Date : 4/26/2019
 */
public class StorageKey extends AbstractMerkleNode implements FCMKey {
	private static final long CURRENT_VERSION = 1;
	private static final long OBJECT_ID = 15487002;
	private String path;

	public StorageKey() {
	}

	public StorageKey(final String path) {
		this.path = path;
	}

	public StorageKey(final StorageKey other) {
		this.path = other.path;
	}

	@SuppressWarnings("unchecked")
	public static <T extends FastCopyable> T deserialize(final DataInputStream inStream) throws IOException {
		StorageKey key = new StorageKey();

		deserialize(inStream, key);
		return (T)key;
	}

	private static void deserialize(final DataInputStream inStream, final StorageKey key) throws IOException {
		long version = inStream.readLong();
		long objectId = inStream.readLong();

		key.path = ((SerializableDataInputStream)inStream).readNormalisedString(4_096);
	}

	public String getPath() {
		return path;
	}

	@Override
	public boolean isLeaf() {
		return true;
	}

	@Override
	public boolean equals(final Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		StorageKey storageKey = (StorageKey) o;
		return Arrays.equals(
				CommonUtils.getNormalisedStringBytes(path),
				CommonUtils.getNormalisedStringBytes(storageKey.path));
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(CommonUtils.getNormalisedStringBytes(path));
	}

	@Override
	public String toString() {
		return path;
	}

	private void serialize(final SerializableDataOutputStream outStream) throws IOException {
		outStream.writeLong(CURRENT_VERSION);
		outStream.writeLong(OBJECT_ID);
		outStream.writeNormalisedString(path);
	}

	@Override
	public StorageKey copy() {
		return new StorageKey(this);
	}

	@Override
	public void copyTo(final SerializableDataOutputStream outStream) throws IOException {
		serialize(outStream);
	}

	@Override
	public void copyFrom(final SerializableDataInputStream inStream) throws IOException {
		//NoOp method
	}

	@Override
	public void copyToExtra(final SerializableDataOutputStream outStream) throws IOException {
		//NoOp method
	}

	@Override
	public void copyFromExtra(final SerializableDataInputStream inStream) throws IOException {
		//NoOp method
	}

	@Override
	public void diffCopyTo(final SerializableDataOutputStream outStream, final SerializableDataInputStream inStream) throws IOException {
		serialize(outStream);
	}

	@Override
	public void diffCopyFrom(final SerializableDataOutputStream outStream, final SerializableDataInputStream inStream) throws IOException {
		deserialize(inStream, this);
	}

	@Override
	public void delete() {
		//NoOp method
	}

	@Override
	public long getClassId() {
		return 0;
	}

	@Override
	public int getVersion() {
		return 0;
	}
}
