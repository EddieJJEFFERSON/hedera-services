package com.hedera.services.bdd.suites.utils.validation;

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
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.HapiApiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;

public class OneOffValidation extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(OneOffValidation.class);

	private static final String NODES = "34.94.106.61:0.0.3,35.196.34.86:0.0.4,35.194.75.187:0.0.5,34.82.241.226:0.0.6";
	private static final String STARTUP_ACCOUNT = "0.0.50";
	private static final String STARTUP_ACCOUNT_LOC = "src/main/resource/TestnetStartupAccount.txt";

	public static void main(String... args) {
		new OneOffValidation().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
				xferWithTls(),
		});
	}

	private HapiApiSpec xferWithTls() {
		return customHapiSpec("tryWithTls").withProperties(Map.of(
				"nodes", NODES,
				"tls", "on",
				"default.payer", STARTUP_ACCOUNT,
				"startupAccounts.path", STARTUP_ACCOUNT_LOC
		)).given().when().then(
				cryptoTransfer(tinyBarsFromTo(GENESIS, ADDRESS_BOOK_CONTROL, 1))
		);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}

