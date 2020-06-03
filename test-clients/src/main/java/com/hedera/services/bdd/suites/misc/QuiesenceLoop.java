package com.hedera.services.bdd.suites.misc;

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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static com.hedera.services.bdd.spec.HapiApiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static java.util.concurrent.TimeUnit.SECONDS;

public class QuiesenceLoop extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(QuiesenceLoop.class);

	private static final String NODES =
			"35.227.78.100:0.0.3,35.245.38.130:0.0.4,35.192.158.160:0.0.5,35.230.35.99:0.0.6";
//			"35.237.15.212:0.0.3,34.86.9.217:0.0.4,35.194.36.23:0.0.5,35.233.190.230:0.0.6";

	private AtomicLong duration = new AtomicLong(30);
	private AtomicReference<TimeUnit> unit = new AtomicReference<>(SECONDS);
	private AtomicInteger maxOpsPerSec = new AtomicInteger(100);

	public static void main(String... args) {
		new QuiesenceLoop().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
//						repeatQuiesenceTest(),
						checkNode0(),
//						checkOtherNodes(),
//						runHealingTransfers(),
				}
		);
	}

	private HapiApiSpec runHealingTransfers() {
		AtomicInteger nextNode = new AtomicInteger(4);
		return customHapiSpec("runHealingTransfers")
				.withProperties(Map.of(
						"nodes", NODES,
						"default.payer", "0.0.50",
						"startupAccounts.path", "src/main/resource/TestnetStartupAccount.txt"
				))
				.given().when().then(
						inParallel(IntStream.range(0, 10000)
								.mapToObj(i ->
										cryptoTransfer(tinyBarsFromTo(GENESIS, ADDRESS_BOOK_CONTROL, 1L))
												.setNodeFrom(() -> String.format("0.0.%d", nextNodeAccount(nextNode)))
												.deferStatusResolution())
								.toArray(HapiSpecOperation[]::new))
				);
	}

	private int nextNodeAccount(AtomicInteger next) {
		return next.getAndIncrement() % 4 + 3;
	}

	private HapiApiSpec repeatQuiesenceTest() {
		return customHapiSpec("repeatQuiesenceTest")
				.withProperties(Map.of(
						"nodes", NODES,
						"default.payer", "0.0.50",
						"startupAccounts.path", "src/main/resource/TestnetStartupAccount.txt"
				))
				.given().when().then(
						withOpContext((spec, opLog) -> configureFromCi(spec)),
						runWithProvider(quiesenceFactory())
								.sequentially()
								.lasting(duration::get, unit::get)
								.maxOpsPerSec(maxOpsPerSec::get)
				);
	}

	private HapiApiSpec checkOtherNodes() {
		return customHapiSpec("CheckOtherNodes")
				.withProperties(Map.of(
						"nodes", NODES,
						"default.payer", "0.0.50",
						"startupAccounts.path", "src/main/resource/TestnetStartupAccount.txt"
				))
				.given().when().then(
						cryptoTransfer(tinyBarsFromTo(GENESIS, ADDRESS_BOOK_CONTROL, 1)).setNode("0.0.4"),
						cryptoTransfer(tinyBarsFromTo(GENESIS, ADDRESS_BOOK_CONTROL, 1)).setNode("0.0.5"),
						cryptoTransfer(tinyBarsFromTo(GENESIS, ADDRESS_BOOK_CONTROL, 1)).setNode("0.0.6")
				);
	}

	private HapiApiSpec checkNode0() {
		return customHapiSpec("CheckNode0")
				.withProperties(Map.of(
						"nodes", NODES,
						"default.payer", "0.0.50",
						"startupAccounts.path", "src/main/resource/TestnetStartupAccount.txt"
				)).given().when().then(
						cryptoTransfer(tinyBarsFromTo(GENESIS, ADDRESS_BOOK_CONTROL, 1)).setNode("0.0.3")
//						IntStream.range(0, 100).mapToObj(ignore ->
//								cryptoTransfer(tinyBarsFromTo(GENESIS, ADDRESS_BOOK_CONTROL, 1))
//										.deferStatusResolution()
//										.setNode("0.0.3"))
//										.toArray(HapiSpecOperation[]::new)
				);
	}


	private Function<HapiApiSpec, OpProvider> quiesenceFactory() {
		AtomicInteger nextOp = new AtomicInteger(0);
		List<Supplier<HapiSpecOperation>> sequence = List.of(
				() -> cryptoTransfer(tinyBarsFromTo(GENESIS, "A", 1))
						.setNode("0.0.3"),
				() -> cryptoTransfer(tinyBarsFromTo(GENESIS, "A", 1))
						.setNode("0.0.4"),
				() -> cryptoTransfer(tinyBarsFromTo(GENESIS, "A", 1))
						.setNode("0.0.5"),
				() -> cryptoTransfer(tinyBarsFromTo(GENESIS, "A", 1))
						.setNode("0.0.6"),
				() -> sleepFor(120_000L)
		);

		return spec -> new OpProvider() {
			@Override
			public List<HapiSpecOperation> suggestedInitializers() {
				return List.of(
						cryptoCreate("A").setNode("0.0.3")
				);
			}

			@Override
			public Optional<HapiSpecOperation> get() {
				int i = nextOp.getAndIncrement();
				i %= sequence.size();
				return Optional.of(sequence.get(i).get());
			}
		};
	}

	private void configureFromCi(HapiApiSpec spec) {
		HapiPropertySource ciProps = spec.setup().ciPropertiesMap();
		configure("duration", duration::set, ciProps, ciProps::getLong);
		configure("unit", unit::set, ciProps, ciProps::getTimeUnit);
		configure("maxOpsPerSec", maxOpsPerSec::set, ciProps, ciProps::getInteger);
	}

	private <T> void configure(
			String name,
			Consumer<T> configurer,
			HapiPropertySource ciProps,
			Function<String, T> getter
	) {
		if (ciProps.has(name)) {
			configurer.accept(getter.apply(name));
		}
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}