package com.hedera.services.config;

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

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.services.context.properties.PropertySource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.*;

@RunWith(JUnitPlatform.class)
class AccountNumbersTest {
	PropertySource properties;
	AccountNumbers subject;

	@BeforeEach
	private void setup() {
		properties = mock(PropertySource.class);
		given(properties.getLongProperty("files.addressBookAdmin.idNum")).willReturn(55L);
		given(properties.getLongProperty("files.feeSchedulesAdmin.idNum")).willReturn(56L);
		given(properties.getLongProperty("files.exchangeRatesAdmin.idNum")).willReturn(57L);
		given(properties.getIntProperty("hedera.masterAccount.idNum")).willReturn(50);
		given(properties.getIntProperty("hedera.treasuryAccount.idNum")).willReturn(2);

		subject = new AccountNumbers(properties);
	}

	@Test
	public void hasExpectedNumbers() {
		// expect:
		assertEquals(2, subject.treasury());
		assertEquals(50, subject.master());
		assertEquals(55, subject.addressBookAdmin());
		assertEquals(56, subject.feeSchedulesAdmin());
		assertEquals(57, subject.exchangeRatesAdmin());
	}

	@Test
	public void recognizesAdmins() {
		// expect:
		assertTrue(subject.isSysAdmin(2));
		assertTrue(subject.isSysAdmin(50));
		assertFalse(subject.isSysAdmin(3));
		assertFalse(subject.isSysAdmin(55));
	}
}
