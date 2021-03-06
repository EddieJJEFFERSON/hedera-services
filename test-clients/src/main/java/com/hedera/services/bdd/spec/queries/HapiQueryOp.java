package com.hedera.services.bdd.spec.queries;

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

import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.keys.SigStyle;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransferList;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.fees.Payment;
import com.hedera.services.bdd.spec.keys.ControlForKey;
import com.hedera.services.bdd.spec.keys.SigMapGenerator;
import com.hedera.services.bdd.spec.stats.QueryObs;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hedera.services.bdd.spec.HapiApiSpec.CostSnapshotMode.OFF;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.*;
import static com.hedera.services.bdd.spec.queries.QueryUtils.*;
import static java.util.stream.Collectors.toList;
import static com.hedera.services.bdd.spec.fees.Payment.Reason.*;

public abstract class HapiQueryOp<T extends HapiQueryOp<T>> extends HapiSpecOperation {
	private static final Logger log = LogManager.getLogger(HapiQueryOp.class);

	private String nodePaymentName;
	protected boolean recordsNodePayment = false;
	protected boolean stopAfterCostAnswer = false;
	protected Response response = null;
	protected Optional<Long> nodePayment = Optional.empty();
	protected Optional<ResponseCodeEnum> costAnswerPrecheck = Optional.empty();
	protected Optional<ResponseCodeEnum> answerOnlyPrecheck = Optional.empty();
	protected Optional<Function<HapiApiSpec, Long>> nodePaymentFn = Optional.empty();
	protected Optional<EnumSet<ResponseCodeEnum>> permissibleAnswerOnlyPrechecks = Optional.empty();
	protected Optional<EnumSet<ResponseCodeEnum>> permissibleCostAnswerPrechecks = Optional.empty();

	protected ResponseCodeEnum expectedCostAnswerPrecheck() { return costAnswerPrecheck.orElse(OK); }
	protected ResponseCodeEnum expectedAnswerOnlyPrecheck() { return answerOnlyPrecheck.orElse(OK); }

	/* WARNING: Must set `response` as a side effect! */
	protected abstract void submitWith(HapiApiSpec spec, Transaction payment) throws Throwable;
	protected abstract boolean needsPayment();

	protected long lookupCostWith(HapiApiSpec spec, Transaction payment) throws Throwable { return 0L; }
	protected long costOnlyNodePayment(HapiApiSpec spec) throws Throwable { return 0L; };

	public Response getResponse() {
		return response;
	}

	protected long costFrom(Response response) throws Throwable {
		ResponseCodeEnum actualPrecheck = reflectForPrecheck(response);
		if (permissibleCostAnswerPrechecks.isPresent()) {
			if (permissibleCostAnswerPrechecks.get().contains(actualPrecheck)) {
				costAnswerPrecheck = Optional.of(actualPrecheck);
			} else {
				Assert.fail(
						String.format(
								"Cost-answer precheck was %s, not one of %s!",
								actualPrecheck,
								permissibleCostAnswerPrechecks.get()));
			}
		} else {
			Assert.assertEquals("Bad costAnswerPrecheck!", expectedCostAnswerPrecheck(), actualPrecheck);
		}
		return reflectForCost(response);
	}

	protected abstract T self();

	@Override
	protected boolean submitOp(HapiApiSpec spec) throws Throwable {
		fixNodeFor(spec);
		configureTlsFor(spec);

		/* Note that HapiQueryOp#fittedPayment makes a COST_ANSWER query if necessary. */
		Transaction payment = needsPayment() ? fittedPayment(spec) : Transaction.getDefaultInstance();

		if (stopAfterCostAnswer) {
			return false;
		}

		/* If the COST_ANSWER query was expected to fail, we will not do anything else for this query. */
		if (needsPayment() && !nodePayment.isPresent() && expectedCostAnswerPrecheck() != OK) {
			return false;
		}

		if (needsPayment() && !loggingOff) {
			log.info(spec.logPrefix() + "Paying for " + this + " with " + txnToString(payment));
		}
		timedSubmitWith(spec, payment);

		ResponseCodeEnum actualPrecheck = reflectForPrecheck(response);
		if (permissibleAnswerOnlyPrechecks.isPresent()) {
			if (permissibleAnswerOnlyPrechecks.get().contains(actualPrecheck)) {
				answerOnlyPrecheck = Optional.of(actualPrecheck);
			} else {
				Assert.fail(
						String.format(
								"Answer-only precheck was %s, not one of %s!",
								actualPrecheck,
								permissibleAnswerOnlyPrechecks.get()));
			}
		} else {
			Assert.assertEquals("Bad answerOnlyPrecheck!", expectedAnswerOnlyPrecheck(), actualPrecheck);
		}
		if (expectedCostAnswerPrecheck() != OK || expectedAnswerOnlyPrecheck() != OK) { return false; }
		txnSubmitted = payment;
		return true;
	}
	private void timedSubmitWith(HapiApiSpec spec, Transaction payment) throws Throwable {
		if (suppressStats) {
			submitWith(spec, payment);
		} else {
			long before = System.currentTimeMillis();
			submitWith(spec, payment);
			long after = System.currentTimeMillis();

			QueryObs stats = new QueryObs(ResponseType.ANSWER_ONLY, type());
			stats.setAccepted(reflectForPrecheck(response) == OK);
			stats.setResponseLatency(after - before);
			considerRecording(spec, stats);
		}
	}

	@Override
	protected long feeFor(HapiApiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
		return spec.fees().forActivityBasedOp(
				HederaFunctionality.CryptoTransfer,
				cryptoFees::getCryptoTransferTxFeeMatrices,
				txn, numPayerKeys);
	}

	private Transaction fittedPayment(HapiApiSpec spec) throws Throwable {
		if (nodePaymentFn.isPresent()) {
			return finalizedTxn(spec, opDef(spec, nodePaymentFn.get().apply(spec)));
		} else if (nodePayment.isPresent()) {
			return finalizedTxn(spec, opDef(spec, nodePayment.get()));
		} else {
			long initNodePayment = costOnlyNodePayment(spec);
			Transaction payment = finalizedTxn(spec, opDef(spec, initNodePayment), true);
			if (!loggingOff) {
				log.info(spec.logPrefix() + "Paying for COST_ANSWER of " + this + " with " + txnToString(payment));
			}
			long realNodePayment = timedCostLookupWith(spec, payment);
			if (recordsNodePayment) {
				spec.registry().saveAmount(nodePaymentName, realNodePayment);
			}
			if (!suppressStats) { spec.incrementNumLedgerOps(); }
			if (expectedCostAnswerPrecheck() != OK) {
				return null;
			}
			if (spec.setup().costSnapshotMode() != OFF) {
				spec.recordPayment(new Payment(
						initNodePayment,
						self().getClass().getSimpleName(),
						COST_ANSWER_QUERY_COST));
				spec.recordPayment(new Payment(
						realNodePayment,
						self().getClass().getSimpleName(),
						ANSWER_ONLY_QUERY_COST));
			}
			txnSubmitted = payment;
			if (!loggingOff) {
				log.info(spec.logPrefix() + "--> Node payment for " + this + " is " + realNodePayment + " tinyBars.");
			}
			return finalizedTxn(spec, opDef(spec, realNodePayment));
		}
	}
	private long timedCostLookupWith(HapiApiSpec spec, Transaction payment)	throws Throwable {
		if (suppressStats) {
			return lookupCostWith(spec, payment);
		} else {
			long before = System.currentTimeMillis();
			long cost = lookupCostWith(spec, payment);
			long after = System.currentTimeMillis();

			QueryObs stats = new QueryObs(ResponseType.COST_ANSWER, type());
			stats.setAccepted(expectedCostAnswerPrecheck() == OK);
			stats.setResponseLatency(after - before);
			considerRecording(spec, stats);

			return cost;
		}
	}

	private Consumer<TransactionBody.Builder> opDef(HapiApiSpec spec, long amount) throws Throwable {
		TransferList transfers = asTransferList(
				tinyBarsFromTo(
						amount,
						spec.registry().getAccountID(effectivePayer(spec)),
						targetNodeFor(spec)));
		CryptoTransferTransactionBody opBody = spec
				.txns()
				.<CryptoTransferTransactionBody, CryptoTransferTransactionBody.Builder>
						body(CryptoTransferTransactionBody.class, b -> b.setTransfers(transfers));
		return b -> b.setCryptoTransfer(opBody);
	}

	public T hasCostAnswerPrecheck(ResponseCodeEnum precheck) {
		costAnswerPrecheck = Optional.of(precheck);
		return self();
	}
	public T hasCostAnswerPrecheckFrom(ResponseCodeEnum... prechecks) {
		permissibleCostAnswerPrechecks = Optional.of(EnumSet.copyOf(List.of(prechecks)));
		return self();
	}
	public T hasAnswerOnlyPrecheck(ResponseCodeEnum precheck) {
		answerOnlyPrecheck = Optional.of(precheck);
		return self();
	}
	public T hasAnswerOnlyPrecheckFrom(ResponseCodeEnum... prechecks) {
		permissibleAnswerOnlyPrechecks = Optional.of(EnumSet.copyOf(List.of(prechecks)));
		return self();
	}
	public T nodePayment(Function<HapiApiSpec, Long> fn) {
		nodePaymentFn = Optional.of(fn);
		return self();
	}
	public T nodePayment(long amount) {
		nodePayment = Optional.of(amount);
		return self();
	}
	public T stoppingAfterCostAnswer() {
		stopAfterCostAnswer = true;
		return self();
	}
	public T via(String name) {
		txnName = name;
		shouldRegisterTxnId = true;
		return self();
	}
	public T fee(long amount) {
		if (amount >= 0) {
			fee = Optional.of(amount);
		}
		return self();
	}
	public T logged() {
		verboseLoggingOn = true;
		return self();
	}
	public T payingWith(String name) {
		payer = Optional.of(name);
		return self();
	}
	public T signedBy(String... keys) {
		signers = Optional.of(
				Stream.of(keys)
						.<Function<HapiApiSpec, Key>>map(k -> spec -> spec.registry().getKey(k))
						.collect(toList()));
		return self();
	}
	public T record(Boolean isGenerated) {
		genRecord = Optional.of(isGenerated);
		return self();
	}
	public T sigMapPrefixes(SigMapGenerator.Nature nature) {
		sigMapGen = Optional.of(nature);
		return self();
	}
	public T sigStyle(SigStyle style) {
		useLegacySignature = (style == SigStyle.LIST);
		return self();
	}
	public T sigControl(ControlForKey... overrides) {
		controlOverrides = Optional.of(overrides);
		return self();
	}
	public T numPayerSigs(int hardcoded) {
		this.hardcodedNumPayerKeys = Optional.of(hardcoded);
		return self();
	}
	public T suppressStats(boolean flag) {
		suppressStats = flag;
		return self();
	}
	public T noLogging() {
		loggingOff = true;
		return self();
	}
	public T recordNodePaymentAs(String s) {
		recordsNodePayment = true;
		nodePaymentName = s;
		return self();
	}
	public T useEmptyTxnAsCostPayment() {
		useDefaultTxnAsCostAnswerPayment = true;
		return self();
	}
	public T useEmptyTxnAsAnswerPayment() {
		useDefaultTxnAsAnswerOnlyPayment = true;
		return self();
	}
	public T randomNode() {
		useRandomNode = true;
		return self();
	}
	public T setNode(String account) {
		node = Optional.of(HapiPropertySource.asAccount(account));
		return self();
	}
	public T setNodeFrom(Supplier<String> accountSupplier) {
		nodeSupplier = Optional.of(() -> HapiPropertySource.asAccount(accountSupplier.get()));
		return self();
	}
}
