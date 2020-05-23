package com.hedera.services.txns.duplicity;

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

import com.hedera.services.legacy.core.jproto.JTransactionRecord;
import com.hedera.services.txns.diligence.DuplicateClassification;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_EXPIRED;
import static java.util.stream.Collectors.toList;

public class PayerRecordHistory {
	private static EnumSet<ResponseCodeEnum> INELIGIBLE_STATUSES = EnumSet.of(
		INVALID_NODE_ACCOUNT, INVALID_PAYER_SIGNATURE, TRANSACTION_EXPIRED
	);

	private int numDuplicates = 0;
	private List<RecordWithOrigin> duplicates = new LinkedList<>();
	private List<JTransactionRecord> broken = new LinkedList<>();

	public static Predicate<ResponseCodeEnum> IS_DUPLICATE_ELIGIBLE =
			((Predicate<ResponseCodeEnum>)INELIGIBLE_STATUSES::contains).negate();

	public DuplicateClassification duplicityFor(long submittingMember) {
		throw new AssertionError("Not implemented!");
	}

	public void observe(long submittingMember, JTransactionRecord payerRecord) {
		var status = ResponseCodeEnum.valueOf(payerRecord.getTxReceipt().getStatus());
		if (IS_DUPLICATE_ELIGIBLE.test(status)) {
			addToDuplicates(submittingMember, payerRecord);
		} else {
			broken.add(payerRecord);
		}
	}

	private void addToDuplicates(long submittingMember, JTransactionRecord payerRecord) {
		int i = 0;
		var iter = duplicates.listIterator();
		var historyRecord = new RecordWithOrigin(submittingMember, payerRecord);
		boolean isNodeDuplicate = false;
		while (i < numDuplicates) {
			if (submittingMember == iter.next().submittingMember) {
				isNodeDuplicate = true;
				break;
			}
			i++;
		}
		if (isNodeDuplicate) {
			duplicates.add(historyRecord);
		} else {
			numDuplicates++;
			iter.add(historyRecord);
		}
	}

	public void forgetExpired(long now) {
		throw new AssertionError("Not implemented!");
	}

	public List<JTransactionRecord> duplicateRecords() {
		return duplicates.stream().map(RecordWithOrigin::payerRecord).collect(toList());
	}

	public List<JTransactionRecord> brokenRecords() {
		return Collections.unmodifiableList(broken);
	}

	private static class RecordWithOrigin {
		final long submittingMember;
		final JTransactionRecord payerRecord;

		public RecordWithOrigin(long submittingMember, JTransactionRecord payerRecord) {
			this.submittingMember = submittingMember;
			this.payerRecord = payerRecord;
		}

		public JTransactionRecord payerRecord() {
			return payerRecord;
		}
	}
}
