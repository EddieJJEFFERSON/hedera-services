package com.hedera.services.txns.file;

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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.files.HederaFs;
import com.hedera.services.txns.TransitionLogic;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hedera.services.legacy.core.jproto.JFileInfo;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class FileSysUndelTransitionLogic implements TransitionLogic {
	private static final Function<TransactionBody, ResponseCodeEnum> SYNTAX_RUBBER_STAMP = ignore -> OK;

	private final HederaFs hfs;
	private final Map<FileID, Long> oldExpiries;
	private final TransactionContext txnCtx;

	public FileSysUndelTransitionLogic(
			HederaFs hfs,
			Map<FileID, Long> oldExpiries,
			TransactionContext txnCtx
	) {
		this.hfs = hfs;
		this.oldExpiries = oldExpiries;
		this.txnCtx = txnCtx;
	}

	@Override
	public void doStateTransition() {
		var op = txnCtx.accessor().getTxn().getSystemUndelete();
		var tbu = op.getFileID();
		var attr = new AtomicReference<JFileInfo>();

		var validity = tryLookup(tbu, attr);
		if (validity != OK)	 {
			txnCtx.setStatus(validity);
			return;
		}

		var info = attr.get();
		var oldExpiry = oldExpiries.get(tbu);
		if (oldExpiry <= txnCtx.consensusTime().getEpochSecond()) {
			hfs.rm(tbu);
		} else {
			info.setDeleted(false);
			info.setExpirationTimeSeconds(oldExpiry);
			hfs.sudoSetattr(tbu, info);
		}
		oldExpiries.remove(tbu);
		txnCtx.setStatus(SUCCESS);
	}

	private ResponseCodeEnum tryLookup(FileID tbu, AtomicReference<JFileInfo> attr) {
		if (!oldExpiries.containsKey(tbu) || !hfs.exists(tbu)) {
			return INVALID_FILE_ID;
		}

		var info = hfs.getattr(tbu);
		if (info.isDeleted()) {
			attr.set(info);
			return OK;
		} else {
			return INVALID_FILE_ID;
		}
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return txn -> txn.hasSystemUndelete() && txn.getSystemUndelete().hasFileID();
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> syntaxCheck() {
		return SYNTAX_RUBBER_STAMP;
	}
}
