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

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.*;

import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.legacy.core.jproto.JTransactionRecord;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.hedera.test.utils.IdUtils.asAccount;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.BDDMockito.*;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

@RunWith(JUnitPlatform.class)
class PayerRecordHistoryTest {
	private Instant now = Instant.now();

	PayerRecordHistory subject;

	@BeforeEach
	private void setup() {
		subject = new PayerRecordHistory();
	}

	@Test
	public void organizesRecordsAsExpected() {
		// given:
		subject.observe(1, recordOf(1, 0, INVALID_PAYER_SIGNATURE));
		subject.observe(1, recordOf(1, 1, SUCCESS));
		subject.observe(1, recordOf(1, 2, DUPLICATE_TRANSACTION));
		subject.observe(2, recordOf(2, 3, DUPLICATE_TRANSACTION));
		subject.observe(2, recordOf(2, 4, DUPLICATE_TRANSACTION));
		subject.observe(3, recordOf(3, 5, DUPLICATE_TRANSACTION));
		subject.observe(1, recordOf(1, 6, TRANSACTION_EXPIRED));
		subject.observe(2, recordOf(2, 7, INVALID_NODE_ACCOUNT));

		// expect:
		assertEquals(
				List.of(
						memoIdentifying(1, 1, SUCCESS),
						memoIdentifying(2, 3, DUPLICATE_TRANSACTION),
						memoIdentifying(3, 5, DUPLICATE_TRANSACTION),
						memoIdentifying(1, 2, DUPLICATE_TRANSACTION),
						memoIdentifying(2, 4, DUPLICATE_TRANSACTION)
				), subject.duplicateRecords().stream().map(JTransactionRecord::getMemo).collect(toList()));
		// and:
		assertEquals(
				List.of(
						memoIdentifying(1, 0, INVALID_PAYER_SIGNATURE),
						memoIdentifying(1, 6, TRANSACTION_EXPIRED),
						memoIdentifying(2, 7, INVALID_NODE_ACCOUNT)
				), subject.brokenRecords().stream().map(JTransactionRecord::getMemo).collect(toList()));
	}

	private JTransactionRecord recordOf(
			long submittingMember,
			long consensusOffsetSecs,
			ResponseCodeEnum status) {
		var payerRecord = TransactionRecord.newBuilder()
				.setMemo(memoIdentifying(submittingMember, consensusOffsetSecs, status))
				.setReceipt(TransactionReceipt.newBuilder().setStatus(status))
				.build();
		var jPayerRecord = JTransactionRecord.convert(payerRecord);
		jPayerRecord.setExpirationTime(now.getEpochSecond() + 1 + consensusOffsetSecs);
		return jPayerRecord;
	}

	private String memoIdentifying(long submittingMember, long consensusOffsetSecs, ResponseCodeEnum status) {
		return String.format("%d submitted @ %d past -> %s", submittingMember, consensusOffsetSecs, status);
	}
}