package com.hedera.services.legacy.services.state.initialization;

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

import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.legacy.initialization.NodeAccountsCreation;
import com.hedera.services.state.initialization.SystemAccountsCreator;
import com.hedera.services.state.merkle.MerkleAccount;
import com.swirlds.common.AddressBook;
import com.swirlds.fcmap.FCMap;

public class DefaultSystemAccountsCreator implements SystemAccountsCreator {
	public static final long SUGGESTED_POST_CREATION_PAUSE_MS = 10_000L;

	private final NodeAccountsCreation delegate = new NodeAccountsCreation();

	@Override
	public void createSystemAccounts(FCMap<MerkleEntityId, MerkleAccount> accounts, AddressBook addressBook) throws Exception {
		delegate.initializeNodeAccounts(addressBook, accounts);
	}
}
