package com.hedera.services.state.merkle;

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

import com.google.common.base.MoreObjects;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.TopicID;
import com.swirlds.common.FCMKey;
import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.io.SerializedObjectProvider;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.utility.AbstractMerkleNode;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Objects;

public class EntityId extends AbstractMerkleNode implements FCMKey, MerkleLeaf {
	static final int MERKLE_VERSION = 1;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0xd5dd2ebaa0bde03L;

	private long shardNum;
	private long realmNum;
	private long idNum;

	public EntityId() { }

	public EntityId(long shardNum, long realmNum, long idNum) {
		this.shardNum = shardNum;
		this.realmNum = realmNum;
		this.idNum = idNum;
	}

	public static EntityId fromPojoAccount(AccountID pojo) {
		return new EntityId(pojo.getShardNum(), pojo.getRealmNum(), pojo.getAccountNum());
	}

	public static EntityId fromPojoTopic(TopicID pojo) {
		return new EntityId(pojo.getShardNum(), pojo.getRealmNum(), pojo.getTopicNum());
	}

	public static EntityId fromPojoContract(ContractID pojo) {
		return new EntityId(pojo.getShardNum(), pojo.getRealmNum(), pojo.getContractNum());
	}

	@Deprecated
	public static class Provider implements SerializedObjectProvider {
		@Override
		public FastCopyable deserialize(DataInputStream in) throws IOException {
			var id = new EntityId();

			in.readLong();
			in.readLong();

			id.realmNum = in.readLong();
			id.shardNum = in.readLong();
			id.idNum = in.readLong();

			return id;
		}
	}

	/* --- MerkleLeaf --- */
	@Override
	public long getClassId() {
		return RUNTIME_CONSTRUCTABLE_ID;
	}

	@Override
	public int getVersion() {
		return MERKLE_VERSION;
	}

	@Override
	public boolean isLeaf() {
		return true;
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		shardNum = in.readLong();
		realmNum = in.readLong();
		idNum = in.readLong();
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeLong(shardNum);
		out.writeLong(realmNum);
		out.writeLong(idNum);
	}

	/* --- FastCopyable --- */
	@Override
	public EntityId copy() {
		return new EntityId(shardNum, realmNum, idNum);
	}

	@Override
	public void delete() { }

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || EntityId.class != o.getClass()) {
			return false;
		}

		var that = (EntityId)o;
		return shardNum == that.shardNum && realmNum == that.realmNum && idNum == that.idNum;
	}

	@Override
	public int hashCode() {
		return Objects.hash(shardNum, realmNum, idNum);
	}

	@Override
	@Deprecated
	public void copyTo(SerializableDataOutputStream out) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void copyToExtra(SerializableDataOutputStream out) {
		throw new UnsupportedOperationException();
	}

	@Override
	@Deprecated
	public void copyFrom(SerializableDataInputStream in) {
		throw new UnsupportedOperationException();
	}

	@Override
	@Deprecated
	public void copyFromExtra(SerializableDataInputStream in) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void diffCopyTo(SerializableDataOutputStream out, SerializableDataInputStream in) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void diffCopyFrom(SerializableDataOutputStream out, SerializableDataInputStream in) {
		throw new UnsupportedOperationException();
	}

	/* --- Bean --- */

	public long getShardNum() {
		return shardNum;
	}

	public void setShardNum(final long shardNum) {
		this.shardNum = shardNum;
	}

	public long getRealmNum() {
		return realmNum;
	}

	public void setRealmNum(final long realmNum) {
		this.realmNum = realmNum;
	}

	public long getIdNum() {
		return idNum;
	}

	public void setIdNum(final long idNum) {
		this.idNum = idNum;
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.add("shard", shardNum)
				.add("realm", realmNum)
				.add("entity", idNum)
				.toString();
	}
}
