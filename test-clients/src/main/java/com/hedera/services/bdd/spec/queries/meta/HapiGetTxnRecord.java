package com.hedera.services.bdd.spec.queries.meta;

/*-
 * ‌
 * Hedera Services Test Clients
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
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionGetRecordQuery;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.assertions.ErroringAsserts;
import com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts;
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

import static com.hedera.services.bdd.spec.queries.QueryUtils.answerCostHeader;
import static com.hedera.services.bdd.spec.queries.QueryUtils.answerHeader;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.rethrowSummaryError;

public class HapiGetTxnRecord extends HapiQueryOp<HapiGetTxnRecord> {
	private static final Logger log = LogManager.getLogger(HapiGetTxnRecord.class);

	private static final TransactionID defaultTxnId = TransactionID.getDefaultInstance();

	String txn;
	boolean useDefaultTxnId = false;
	Optional<TransactionID> explicitTxnId = Optional.empty();
	Optional<TransactionRecordAsserts> expectations = Optional.empty();
	Optional<BiConsumer<TransactionRecord, Logger>> format = Optional.empty();
	Optional<String> registryEntry = Optional.empty();

	public HapiGetTxnRecord(String txn) {
		this.txn = txn;
	}
	public HapiGetTxnRecord(TransactionID txnId) {
		this.explicitTxnId = Optional.of(txnId);
	}

	@Override
	public HederaFunctionality type() {
		return HederaFunctionality.TransactionGetRecord;
	}

	@Override
	protected HapiGetTxnRecord self() {
		return this;
	}

	public HapiGetTxnRecord saveCreatedContractListToRegistry(String registryEntry) {
		this.registryEntry = Optional.of(registryEntry);
		return this;
	}

	public HapiGetTxnRecord useDefaultTxnId() {
		useDefaultTxnId = true;
		return this;
	}

	public HapiGetTxnRecord has(TransactionRecordAsserts provider) {
		expectations = Optional.of(provider);
		return this;
	}
	public HapiGetTxnRecord loggedWith(BiConsumer<TransactionRecord, Logger> customFormat) {
		super.logged();
		format = Optional.of(customFormat);
		return this;
	}

	public TransactionRecord getResponseRecord() {
		return response.getTransactionGetRecord().getTransactionRecord();
	}

	@Override
	protected void assertExpectationsGiven(HapiApiSpec spec) throws Throwable {
		TransactionRecord actualRecord = response.getTransactionGetRecord().getTransactionRecord();
		if (expectations.isPresent()) {
			ErroringAsserts<TransactionRecord> asserts = expectations.get().assertsFor(spec);
			List<Throwable> errors = asserts.errorsIn(actualRecord);
			rethrowSummaryError(log, "Bad transaction record!", errors);
		}
	}

	@Override
	protected void submitWith(HapiApiSpec spec, Transaction payment) {
		Query query = getRecordQuery(spec, payment, false);
		response = spec.clients().getCryptoSvcStub(targetNodeFor(spec), useTls).getTxRecordByTxID(query);
		TransactionRecord record = response.getTransactionGetRecord().getTransactionRecord();
		if (verboseLoggingOn) {
			if (format.isPresent()) {
				format.get().accept(record, log);
			} else {
				log.info("Record: " + record);
			}
		}
		if (registryEntry.isPresent()) {
			spec.registry().saveContractList(
					registryEntry.get() + "CreateResult",
					record.getContractCreateResult().getCreatedContractIDsList());
			spec.registry().saveContractList(
					registryEntry.get() + "CallResult",
					record.getContractCallResult().getCreatedContractIDsList());
		}
	}

	@Override
	protected long lookupCostWith(HapiApiSpec spec, Transaction payment) throws Throwable {
		Query query = getRecordQuery(spec, payment, true);
		Response response = spec.clients().getCryptoSvcStub(targetNodeFor(spec), useTls).getTxRecordByTxID(query);
		return costFrom(response);
	}

	private Query getRecordQuery(HapiApiSpec spec, Transaction payment, boolean costOnly) {
		TransactionID txnId = useDefaultTxnId
				? defaultTxnId
				: explicitTxnId.orElseGet(() -> spec.registry().getTxnId(txn));
		TransactionGetRecordQuery getRecordQuery = TransactionGetRecordQuery.newBuilder()
				.setHeader(costOnly ? answerCostHeader(payment) : answerHeader(payment))
				.setTransactionID(txnId)
				.build();
		return Query.newBuilder().setTransactionGetRecord(getRecordQuery).build();
	}

	@Override
	protected long costOnlyNodePayment(HapiApiSpec spec) throws Throwable {
		return spec.fees().forOp(
				HederaFunctionality.TransactionGetRecord,
				cryptoFees.getCostTransactionRecordQueryFeeMatrices());
	}

	@Override
	protected boolean needsPayment() {
		return true;
	}

	@Override
	protected MoreObjects.ToStringHelper toStringHelper() {
		if (explicitTxnId.isPresent()) {
			return super.toStringHelper().add("explicitTxnId", true);
		} else {
			return super.toStringHelper().add("txn", txn);
		}
	}
}
