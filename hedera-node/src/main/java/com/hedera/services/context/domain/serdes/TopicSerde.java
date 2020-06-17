package com.hedera.services.context.domain.serdes;

import com.hedera.services.context.domain.topic.Topic;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.commons.codec.binary.StringUtils;

import java.io.IOException;

public class TopicSerde {
	static DomainSerdes serdes = new DomainSerdes();

	public static int MAX_MEMO_BYTES = 4_096;

	public void deserializeV1(SerializableDataInputStream in, Topic to) throws IOException {
		to.setMemo(null);
		if (in.readBoolean()) {
			var bytes = in.readByteArray(MAX_MEMO_BYTES);
			if (null != bytes) {
				to.setMemo(StringUtils.newStringUtf8(bytes));
			}
		}

		to.setAdminKey(in.readBoolean() ? serdes.deserializeKey(in) : null);
		to.setSubmitKey(in.readBoolean() ? serdes.deserializeKey(in) : null);
		to.setAutoRenewDurationSeconds(in.readLong());
		to.setAutoRenewAccountId(in.readBoolean() ? serdes.deserializeId(in) : null);
		to.setExpirationTimestamp(in.readBoolean() ? serdes.deserializeTimestamp(in) : null);
		to.setDeleted(in.readBoolean());
		to.setSequenceNumber(in.readLong());
		to.setRunningHash(in.readBoolean() ? in.readByteArray(Topic.RUNNING_HASH_BYTE_ARRAY_SIZE) : null);
	}

	public void serializeCurrentVersion(Topic topic, SerializableDataOutputStream out) throws IOException {
		if (topic.hasMemo()) {
			out.writeBoolean(true);
			out.writeBytes(topic.getMemo());
		} else {
			out.writeBoolean(false);
		}

		if (topic.hasAdminKey()) {
			out.writeBoolean(true);
			serdes.serializeKey(topic.getAdminKey(), out);
		} else {
			out.writeBoolean(false);
		}

		if (topic.hasSubmitKey()) {
			out.writeBoolean(true);
			serdes.serializeKey(topic.getSubmitKey(), out);
		} else {
			out.writeBoolean(false);
		}

		out.writeLong(topic.getAutoRenewDurationSeconds());

		if (topic.hasAutoRenewAccountId()) {
			out.writeBoolean(true);
			serdes.serializeId(topic.getAutoRenewAccountId(), out);
		} else {
			out.writeBoolean(false);
		}

		if (topic.hasExpirationTimestamp()) {
			out.writeBoolean(true);
			serdes.serializeTimestamp(topic.getExpirationTimestamp(), out);
		} else {
			out.writeBoolean(false);
		}

		out.writeBoolean(topic.isDeleted());
		out.writeLong(topic.getSequenceNumber());

		if (topic.hasRunningHash()) {
			out.writeBoolean(true);
			out.writeByteArray(topic.getRunningHash());
		} else {
			out.writeBoolean(false);
		}
	}
}
