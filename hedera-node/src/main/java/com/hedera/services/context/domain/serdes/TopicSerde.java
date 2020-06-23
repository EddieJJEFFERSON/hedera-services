package com.hedera.services.context.domain.serdes;

import com.hedera.services.state.merkle.MerkleTopic;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.commons.codec.binary.StringUtils;

import java.io.IOException;

public class TopicSerde {
	static DomainSerdes serdes = new DomainSerdes();

	public static int MAX_MEMO_BYTES = 4_096;

	public void deserializeV1(SerializableDataInputStream in, MerkleTopic to) throws IOException {
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
		to.setRunningHash(in.readBoolean() ? in.readByteArray(MerkleTopic.RUNNING_HASH_BYTE_ARRAY_SIZE) : null);
	}

	public void serializeCurrentVersion(MerkleTopic merkleTopic, SerializableDataOutputStream out) throws IOException {
		if (merkleTopic.hasMemo()) {
			out.writeBoolean(true);
			out.writeBytes(merkleTopic.getMemo());
		} else {
			out.writeBoolean(false);
		}

		if (merkleTopic.hasAdminKey()) {
			out.writeBoolean(true);
			serdes.serializeKey(merkleTopic.getAdminKey(), out);
		} else {
			out.writeBoolean(false);
		}

		if (merkleTopic.hasSubmitKey()) {
			out.writeBoolean(true);
			serdes.serializeKey(merkleTopic.getSubmitKey(), out);
		} else {
			out.writeBoolean(false);
		}

		out.writeLong(merkleTopic.getAutoRenewDurationSeconds());

		if (merkleTopic.hasAutoRenewAccountId()) {
			out.writeBoolean(true);
			serdes.serializeId(merkleTopic.getAutoRenewAccountId(), out);
		} else {
			out.writeBoolean(false);
		}

		if (merkleTopic.hasExpirationTimestamp()) {
			out.writeBoolean(true);
			serdes.serializeTimestamp(merkleTopic.getExpirationTimestamp(), out);
		} else {
			out.writeBoolean(false);
		}

		out.writeBoolean(merkleTopic.isDeleted());
		out.writeLong(merkleTopic.getSequenceNumber());

		if (merkleTopic.hasRunningHash()) {
			out.writeBoolean(true);
			out.writeByteArray(merkleTopic.getRunningHash());
		} else {
			out.writeBoolean(false);
		}
	}
}
