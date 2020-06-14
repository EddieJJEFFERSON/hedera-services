package com.hedera.services.state.submerkle;

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
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;

import java.io.IOException;

public class ExchangeRates {
	static final long OBSOLETE_VERSION = 1L;
	static final long OBSOLETE_OBJECT_ID = 10000121L;

	private int currentHbarEquiv;
	private int currentCentEquiv;
	private long currentExpiry;

	private int nextHbarEquiv;
	private int nextCentEquiv;
	private long nextExpiry;

	private boolean initialized = false;

	public ExchangeRates() { }

	public ExchangeRates(
			int currentHbarEquiv,
			int currentCentEquiv,
			long currentExpiry,
			int nextHbarEquiv,
			int nextCentEquiv,
			long nextExpiry
	) {
		this.currentHbarEquiv = currentHbarEquiv;
		this.currentCentEquiv = currentCentEquiv;
		this.currentExpiry = currentExpiry;

		this.nextHbarEquiv = nextHbarEquiv;
		this.nextCentEquiv = nextCentEquiv;
		this.nextExpiry = nextExpiry;

		initialized = true;
	}

	public boolean isInitialized() {
		return initialized;
	}

	public int getCurrentHbarEquiv() {
		return currentHbarEquiv;
	}

	public int getCurrentCentEquiv() {
		return currentCentEquiv;
	}

	public int getNextHbarEquiv() {
		return nextHbarEquiv;
	}

	public int getNextCentEquiv() {
		return nextCentEquiv;
	}

	public long getCurrentExpiry() {
		return currentExpiry;
	}

	public long getNextExpiry() {
		return nextExpiry;
	}

	public void replaceWith(final ExchangeRateSet newRates) {
		this.currentHbarEquiv = newRates.getCurrentRate().getHbarEquiv();
		this.currentCentEquiv = newRates.getCurrentRate().getCentEquiv();
		this.currentExpiry = newRates.getCurrentRate().getExpirationTime().getSeconds();

		this.nextHbarEquiv = newRates.getNextRate().getHbarEquiv();
		this.nextCentEquiv = newRates.getNextRate().getCentEquiv();
		this.nextExpiry = newRates.getNextRate().getExpirationTime().getSeconds();

		initialized = true;
	}

	public ExchangeRates copy() {
		return new ExchangeRates(
				currentHbarEquiv, currentCentEquiv, currentExpiry,
				nextHbarEquiv, nextCentEquiv, nextExpiry);
	}

	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeLong(OBSOLETE_VERSION);
		out.writeLong(OBSOLETE_OBJECT_ID);

		out.writeInt(currentHbarEquiv);
		out.writeInt(currentCentEquiv);
		out.writeLong(currentExpiry);
		out.writeInt(nextHbarEquiv);
		out.writeInt(nextCentEquiv);
		out.writeLong(nextExpiry);
	}

	public void deserialize(SerializableDataInputStream in) throws IOException {
		in.readLong();
		in.readLong();

		currentHbarEquiv = in.readInt();
		currentCentEquiv = in.readInt();
		currentExpiry = in.readLong();
		nextHbarEquiv = in.readInt();
		nextCentEquiv = in.readInt();
		nextExpiry = in.readLong();

		initialized = true;
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.add("currentHbarEquiv", currentHbarEquiv)
				.add("currentCentEquiv", currentCentEquiv)
				.add("currentExpiry", currentExpiry)
				.add("nextHbarEquiv", nextHbarEquiv)
				.add("nextCentEquiv", nextCentEquiv)
				.add("nextExpiry", nextExpiry)
				.toString();
	}
}
