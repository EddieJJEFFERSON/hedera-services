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

import com.google.common.base.MoreObjects;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.TransferList;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.hedera.services.state.submerkle.EntityId.ofNullableAccountId;
import static com.hedera.services.utils.MiscUtils.readableTransferList;
import static java.util.stream.Collectors.toList;

public class HbarAdjustments implements SelfSerializable {
	private static final Logger log = LogManager.getLogger(HbarAdjustments.class);

	static final int MERKLE_VERSION = 1;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0xd8b06bd46e12a466L;

	static EntityId.Provider legacyIdProvider = EntityId.LEGACY_PROVIDER;

	public static final HbarAdjustments.Provider LEGACY_PROVIDER = new Provider();

	@Deprecated
	public static class Provider {
		public HbarAdjustments deserialize(DataInputStream in) throws IOException {
			var pojo = new HbarAdjustments();

			in.readLong();
			in.readLong();

			int numAdjustments = in.readInt();
			if (numAdjustments > 0) {
				List<Adjustment> adjustments = new ArrayList<>();
				for (int i = 0; i < numAdjustments; i++) {
					in.readLong();
					in.readLong();
					var accountId = legacyIdProvider.deserialize(in);
					adjustments.add(new Adjustment(in.readLong(), accountId));
				}
				pojo.adjustments = adjustments;
			}

			return pojo;
		}
	}

	public static class Adjustment {
		private final long hbars;
		private final EntityId accountId;

		public Adjustment(long hbars, EntityId accountId) {
			this.hbars = hbars;
			this.accountId = accountId;
		}

		public long getHbars() {
			return hbars;
		}

		public EntityId getAccountId() {
			return accountId;
		}

		@Override
		public boolean equals(Object o) {
			if (o == this) {
				return true;
			}
			if (o == null || Adjustment.class != o.getClass()) {
				return false;
			}
			var that = (Adjustment)o;
			return hbars == that.hbars && accountId.equals(that.accountId);
		}

		@Override
		public int hashCode() {
			int result = Long.hashCode(hbars);
			return result * 31 + accountId.hashCode();
		}
	}

	List<Adjustment> adjustments = Collections.emptyList();

	public HbarAdjustments() { }

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
		throw new AssertionError("Not implemented");
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeSerializableList(
				adjustments.stream().map(Adjustment::getAccountId).collect(toList()),
				true,
				true);
		out.writeLongArray(adjustments.stream().mapToLong(Adjustment::getHbars).toArray());
	}

	@Override
	public boolean equals(final Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		HbarAdjustments that = (HbarAdjustments) o;
		return adjustments.equals(that.adjustments);
	}

	@Override
	public int hashCode() {
		int result = Long.hashCode(RUNTIME_CONSTRUCTABLE_ID);
		result = result * 31 + Integer.hashCode(MERKLE_VERSION);
		return result * 31 + Objects.hash(adjustments);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.add("readable", readableTransferList(toGrpc()))
				.toString();
	}

	/* --- Helpers --- */

	public TransferList toGrpc() {
		var grpc = TransferList.newBuilder();
		adjustments.stream()
				.map(adjustment -> AccountAmount.newBuilder()
						.setAmount(adjustment.getHbars())
						.setAccountID(EntityIdUtils.asAccount(adjustment.getAccountId())))
				.forEach(grpc::addAccountAmounts);
		return grpc.build();
	}

	public static HbarAdjustments fromGrpc(TransferList grpc) {
		var pojo = new HbarAdjustments();
		pojo.adjustments = grpc.getAccountAmountsList().stream()
				.map(subGprc -> new Adjustment(subGprc.getAmount(), ofNullableAccountId(subGprc.getAccountID())))
				.collect(toList());
		return pojo;
	}
}
