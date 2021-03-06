package com.hedera.services.bdd.spec.assertions;

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
import org.junit.Assert;

import java.util.Arrays;
import java.util.Collections;
import java.util.function.Function;

public class EqualityAssertsProviderFactory {
	public static <T> ErroringAssertsProvider<T> shouldBe(T expected) {
		return ignore -> actual -> {
			try {
				Assert.assertEquals(expected, actual);
			} catch (Throwable t) {
				return Arrays.asList(t);
			}
			return Collections.EMPTY_LIST;
		};
	}

	public static <T> ErroringAssertsProvider<T> shouldBe(Function<HapiApiSpec, T> expectation) {
		return spec -> actual -> {
			try {
				T expected = expectation.apply(spec);
				Assert.assertEquals(expected, actual);
			} catch (Throwable t) {
				return Arrays.asList(t);
			}
			return Collections.EMPTY_LIST;
		};
	}
}
