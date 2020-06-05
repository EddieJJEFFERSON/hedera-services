package com.hedera.services.context.domain.haccount;

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

import com.hedera.services.context.domain.serdes.DomainSerdes;
import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializedObjectProvider;

import java.io.DataInputStream;
import java.io.IOException;

import static com.hedera.services.legacy.logic.ApplicationConstants.P;

public enum HederaAccountDeserializer implements SerializedObjectProvider {
	HEDERA_ACCOUNT_DESERIALIZER;

	DomainSerdes serdes = new DomainSerdes();

	@Override
	@SuppressWarnings("unchecked")
	public FastCopyable deserialize(DataInputStream in) throws IOException {
		HederaAccount account = new HederaAccount();
		SerializableDataInputStream fcIn = (SerializableDataInputStream)in;
		deserializeInto(fcIn, account);
		return account;
	}

	public void deserializeInto(SerializableDataInputStream in, HederaAccount to) throws IOException {
		long version = readVersionHeader(in, to);

		if (version == 5) {
			readVersion5(in , to);
		} else {
			throw new IOException(String.format("invalid version %d", version));
		}
	}

	private void readVersion5(SerializableDataInputStream in, HederaAccount to) throws IOException {
		readSigReqDeletedExpiryMemoContract(in, to);
		serdes.deserializeIntoRecords(in, to.records);
	}

	private void readSigReqDeletedExpiryMemoContract(SerializableDataInputStream in, HederaAccount to) throws IOException {
		to.receiverSigRequired = (in.readByte() == 1);
		readKeysProxyAutoRenew(in, to);
		to.deleted = (in.readByte() == 1);
		to.expirationTime = in.readLong();
		to.memo = in.readUTF();
		to.isSmartContract = (in.readByte() == 1);
	}

	private void readKeysProxyAutoRenew(SerializableDataInputStream in, HederaAccount to) throws IOException {
		to.accountKeys = serdes.deserializeKey(in);
		boolean hasProxy = (in.readChar() == P);
		if (hasProxy) {
			to.proxyAccount = serdes.deserializeId(in);
		}
		to.autoRenewPeriod = in.readLong();
	}

	private long readVersionHeader(SerializableDataInputStream in, HederaAccount to) throws IOException {
		long version = in.readLong();
		/* No current use for the object id! */
		in.readLong();

		to.balance = in.readLong();
		to.senderThreshold = in.readLong();
		to.receiverThreshold = in.readLong();

		return version;
	}
}
