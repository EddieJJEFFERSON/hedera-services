package com.hedera.services.legacy.core.jproto;

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

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.builder.RequestBuilder;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Objects;

public class HEntityId implements SelfSerializable {
	private static final Logger log = LogManager.getLogger(HEntityId.class);

	static final int MERKLE_VERSION = 1;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0xf35ba643324efa37L;

	private long shard;
	private long realm;
	private long num;

	@Deprecated
	public static HEntityId legacyProvider(SerializableDataInputStream in) throws IOException {
		in.readLong();
		in.readLong();

		return new HEntityId(in.readLong(), in.readLong(), in.readLong());
	};

	public HEntityId() { }

	public HEntityId(long shard, long realm, long num) {
		this.shard = shard;
		this.realm = realm;
		this.num = num;
	}

	public HEntityId(HEntityId that) {
		this(that.shard, that.realm, that.num);
	}

	/* --- SelfSerializable --- */
	@Override
	public long getClassId() {
		return RUNTIME_CONSTRUCTABLE_ID;
	}

	@Override
	public int getVersion() {
		return MERKLE_VERSION;
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		shard = in.readLong();
		realm = in.readLong();
		num = in.readLong();
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeLong(shard);
		out.writeLong(realm);
		out.writeLong(num);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || HEntityId.class != o.getClass()) {
			return false;
		}
		HEntityId that = (HEntityId)o;
		return shard == that.shard && realm == that.realm && num == that.num;
	}

	@Override
	public int hashCode() {
		return Objects.hash(shard, realm, num);
	}

	public HEntityId copy() {
		return new HEntityId(this);
	}

	public String toString() {
		return "<JAccountID: " + shard + "." + realm + "." + num + ">";
	}

	public long getShard() {
		return shard;
	}

	public long getRealm() {
		return realm;
	}

	public long getNum() {
		return num;
	}

	/* --- Helpers --- */
	public static HEntityId convert(AccountID accID) {
		if (accID == null) {
			return null;
		}
		return new HEntityId(accID.getShardNum(), accID.getRealmNum(), accID.getAccountNum());
	}

	public static HEntityId ofNullableFileId(FileID fileId) {
		return (fileId == null )
				? null
				: new HEntityId(fileId.getShardNum(), fileId.getRealmNum(), fileId.getFileNum());
	}

	public static HEntityId ofNullableTopicId(TopicID topicId) {
		return (topicId == null )
				? null
				: new HEntityId(topicId.getShardNum(), topicId.getRealmNum(), topicId.getTopicNum());
	}

	public static HEntityId ofNullableContractId(ContractID contractId) {
		return (contractId == null )
				? null
				: new HEntityId(contractId.getShardNum(), contractId.getRealmNum(), contractId.getContractNum());
	}

	public static ContractID convert(HEntityId accountId) {
		return RequestBuilder.getContractIdBuild(
				accountId.getNum(),
				accountId.getRealm(),
				accountId.getShard());
	}
}
