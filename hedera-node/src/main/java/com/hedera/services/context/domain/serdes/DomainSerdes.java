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

import com.hedera.services.legacy.core.jproto.HEntityId;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeySerializer;
import com.hedera.services.legacy.core.jproto.JTimestamp;
import com.hedera.services.legacy.core.jproto.JTransactionRecord;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.fcqueue.FCQueue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class DomainSerdes {
	private static final Logger log = LogManager.getLogger(DomainSerdes.class);

	public JKey deserializeKey(DataInputStream in) throws IOException {
		return JKeySerializer.deserialize(in);
	}

	public void serializeKey(JKey key, DataOutputStream out) throws IOException {
		out.write(key.serialize());
	}

	public <T> void writeNullable(
			T data,
			SerializableDataOutputStream out,
			BiConsumer<T, DataOutputStream> writer
	) throws IOException {
		if (data == null) {
			out.writeBoolean(false);
		} else {
			out.writeBoolean(true);
			writer.accept(data, out);
		}
	}

	public <T> T readNullable(
			SerializableDataInputStream in,
			Function<DataInputStream, T> reader
	) throws IOException {
		return in.readBoolean() ? reader.apply(in) : null;
	}

	public <T extends SelfSerializable> void writeNullableSerializable(
			T data,
			SerializableDataOutputStream out
	) throws IOException {
		if (data == null) {
			out.writeBoolean(false);
		} else {
			out.writeBoolean(true);
			out.writeSerializable(data, true);
		}
	}

	public <T extends SelfSerializable> T readNullableSerializable(
			SerializableDataInputStream in
	) throws IOException {
		return in.readBoolean() ? in.readSerializable() : null;
	}

	@SuppressWarnings("unchecked")
	public void serializeId(HEntityId id, DataOutputStream _out) throws IOException {
		var out = (SerializableDataOutputStream) _out;
		out.writeSerializable(id, true);
	}

	public JTimestamp deserializeTimestamp(DataInputStream in) throws IOException {
		return JTimestamp.deserialize(in);
	}

	@SuppressWarnings("unchecked")
	public void serializeTimestamp(JTimestamp ts, DataOutputStream out) throws IOException {
		SerializableDataOutputStream fcOut = (SerializableDataOutputStream)out;
		ts.copyTo(fcOut);
		ts.copyToExtra(fcOut);
	}

	public HEntityId deserializeId(DataInputStream _in) throws IOException {
		var in = (SerializableDataInputStream)_in;
		return in.readSerializable();
	}

	@SuppressWarnings("unchecked")
	public void serializeRecords(FCQueue<JTransactionRecord> records, DataOutputStream out) throws IOException {
		SerializableDataOutputStream fcOut = (SerializableDataOutputStream)out;
		records.copyTo(fcOut);
		records.copyToExtra(fcOut);
	}

	@SuppressWarnings("unchecked")
	public void deserializeIntoRecords(DataInputStream in, FCQueue<JTransactionRecord> to) throws IOException {
		SerializableDataInputStream fcIn = (SerializableDataInputStream)in;
		to.copyFrom(fcIn);
		to.copyFromExtra(fcIn);
	}
}
