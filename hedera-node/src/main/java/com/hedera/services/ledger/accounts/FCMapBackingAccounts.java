package com.hedera.services.ledger.accounts;

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
import com.hedera.services.state.merkle.EntityId;
import com.hedera.services.context.domain.haccount.HederaAccount;
import com.swirlds.fcmap.FCMap;

public class FCMapBackingAccounts implements BackingAccounts<AccountID, HederaAccount> {
	private final FCMap<EntityId, HederaAccount> delegate;

	public FCMapBackingAccounts(FCMap<EntityId, HederaAccount> delegate) {
		this.delegate = delegate;
	}

	@Override
	public HederaAccount getRef(AccountID id) {
		return delegate.get(EntityId.fromPojoAccountId(id));
	}

	@Override
	public HederaAccount getCopy(AccountID id) {
		HederaAccount ref = delegate.get(EntityId.fromPojoAccountId(id));

		return (ref == null) ? null : new HederaAccount(ref);
	}

	@Override
	public void replace(AccountID id, HederaAccount account) {
		EntityId delegateId = EntityId.fromPojoAccountId(id);
		if (!delegate.containsKey(delegateId)) {
			delegate.put(delegateId, account);
		} else {
			delegate.replace(delegateId, account);
		}
	}

	@Override
	public boolean contains(AccountID id) {
		return delegate.containsKey(EntityId.fromPojoAccountId(id));
	}

	@Override
	public void remove(AccountID id) {
		delegate.remove(EntityId.fromPojoAccountId(id));
	}
}
