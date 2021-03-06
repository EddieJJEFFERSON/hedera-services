package com.hedera.services.bdd.spec.infrastructure;

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

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.infrastructure.meta.ActionableContractCall;
import com.hedera.services.bdd.spec.infrastructure.meta.ActionableContractCallLocal;
import com.hedera.services.bdd.spec.infrastructure.meta.SupportedContract;
import com.hedera.services.bdd.spec.stats.OpObs;
import com.hedera.services.bdd.spec.stats.ThroughputObs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusCreateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusUpdateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ContractGetInfoResponse;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hedera.services.legacy.core.HexUtils;
import com.hedera.services.legacy.core.KeyPairObj;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static com.hedera.services.bdd.spec.HapiPropertySource.asAccountString;
import static com.hedera.services.bdd.spec.keys.KeyFactory.firstStartupKp;
import static java.util.stream.Collectors.*;

public class HapiSpecRegistry {
	static final Logger log = LogManager.getLogger(HapiSpecRegistry.class);

	Map<String, Object> registry = new HashMap<>();
	private final HapiSpecSetup setup;
	private final List<OpObs> obs = new ArrayList<>();
	private final List<ThroughputObs> throughputObs = new ArrayList<>();
	private Map<Class, List<RegistryChangeListener>> listenersByType = new HashMap<>();

	private static final Integer ZERO = Integer.valueOf(0);

	public HapiSpecRegistry(HapiSpecSetup setup) throws Exception {
		this.setup = setup;
		KeyPairObj genesisKp = firstStartupKp(setup);
		Key genesisKey = asPublicKey(genesisKp.getPublicKeyAbyteStr());
		saveKey(setup.genesisAccountName(), asKeyList(genesisKey));
		saveAccountId(setup.defaultPayerName(), setup.defaultPayer());
		saveAccountId(setup.defaultNodeName(), setup.defaultNode());
		saveAccountId(setup.fundingAccountName(), setup.fundingAccount());
		saveContractId(setup.invalidContractName(), setup.invalidContract());

		saveAccountId(setup.strongControlName(), setup.strongControlAccount());
		saveKey(setup.strongControlName(), asKeyList(genesisKey));

		/* (system file 1) :: Address Book */
		saveFileId(setup.addressBookName(), setup.addressBookId());
		saveKey(setup.addressBookName(), asKeyList(genesisKey));
		saveAccountId(setup.addressBookControlName(), setup.addressBookControl());
		saveKey(setup.addressBookControlName(), asKeyList(genesisKey));
		/* (system file 2) :: Node Details */
		saveFileId(setup.nodeDetailsName(), setup.nodeDetailsId());
		saveKey(setup.nodeDetailsName(), asKeyList(genesisKey));
		/* (system file 3) :: Exchange Rates */
		saveFileId(setup.exchangeRatesName(), setup.exchangeRatesId());
		saveKey(setup.exchangeRatesName(), asKeyList(genesisKey));
		saveAccountId(setup.exchangeRatesControlName(), setup.exchangeRatesControl());
		saveKey(setup.exchangeRatesControlName(), asKeyList(genesisKey));
		/* (system 4) :: Fee Schedule */
		saveFileId(setup.feeScheduleName(), setup.feeScheduleId());
		saveKey(setup.feeScheduleName(), asKeyList(genesisKey));
		saveAccountId(setup.feeScheduleControlName(), setup.feeScheduleControl());
		saveKey(setup.feeScheduleControlName(), asKeyList(genesisKey));
		/* (system 5) :: API Permissions */
		saveFileId(setup.apiPermissionsFile(), setup.apiPermissionsId());
		saveKey(setup.apiPermissionsFile(), asKeyList(genesisKey));
		/* (system 6) :: App Properties */
		saveFileId(setup.appPropertiesFile(), setup.appPropertiesId());
		saveKey(setup.appPropertiesFile(), asKeyList(genesisKey));
		/* Migration :: File */
		saveFileId(setup.migrationFileName(), setup.migrationFileID());
		saveKey(setup.migrationFileName(), asKeyList(genesisKey));
		/* Migration :: Crypto Account A */
		saveAccountId(setup.migrationAccountAName(), setup.migrationAccountAID());
		saveKey(setup.migrationAccountAName(), asKeyList(genesisKey));
		/* Migration :: Crypto Account B */
		saveAccountId(setup.migrationAccountBName(), setup.migrationAccountBID());
		saveKey(setup.migrationAccountBName(), asKeyList(genesisKey));
		/* Migration :: Smart Contract */
		saveContractId(setup.migrationSmartContractName(), setup.migrationSmartContractID());
		saveKey(setup.migrationSmartContractName(), asKeyList(genesisKey));

		saveKey(HapiApiSuite.NONSENSE_KEY, nonsenseKey());
	}

	private Key nonsenseKey() {
		return Key.getDefaultInstance();
	}

	private Key asPublicKey(String pubKeyHex) throws Exception {
		return Key.newBuilder()
				.setEd25519(ByteString.copyFrom(HexUtils.hexToBytes(pubKeyHex)))
				.build();
	}

	private Key asKeyList(Key key) {
		return Key.newBuilder().setKeyList(KeyList.newBuilder().addKeys(key)).build();
	}

	public void register(RegistryChangeListener<?> listener) {
		Class<?> type = listener.forType();
		listenersByType.computeIfAbsent(type, ignore -> new ArrayList<>()).add(listener);
	}

	synchronized public void record(OpObs stat) {
		obs.add(stat);
	}

	public List<OpObs> stats() {
		return obs;
	}

	public void saveThroughputObs(ThroughputObs obs) {
		put(obs.getName(), obs);
		throughputObs.add(obs);
	}

	public ThroughputObs getThroughputObs(String name) {
		return get(name, ThroughputObs.class);
	}

	public List<ThroughputObs> throughputObs() {
		return throughputObs;
	}

	public void saveContractChoice(String name, SupportedContract choice) {
		put(name, choice);
	}

	public SupportedContract getContractChoice(String name) {
		return get(name, SupportedContract.class);
	}

	public boolean hasContractChoice(String name) {
		return hasVia(this::getContractChoice, name);
	}

	public void removeContractChoice(String name) {
		remove(name, SupportedContract.class);
	}

	public ActionableContractCall getActionableCall(String name) {
		return get(name, ActionableContractCall.class);
	}

	public void saveActionableCall(String name, ActionableContractCall call) {
		put(name, call);
	}

	public void removeActionableCall(String name) {
		remove(name, ActionableContractCall.class);
	}

	public void saveActionableLocalCall(String name, ActionableContractCallLocal call) {
		put(name, call);
	}

	public void removeActionableLocalCall(String name) {
		remove(name, ActionableContractCallLocal.class);
	}

	public ActionableContractCallLocal getActionableLocalCall(String name) {
		return get(name, ActionableContractCallLocal.class);
	}

	public void saveBalanceSnapshot(String name, Long balance) {
		put(name, balance);
	}

	public long getBalanceSnapshot(String name) {
		return get(name, Long.class);
	}

	public boolean hasTimestamp(String label) {
		return hasVia(this::getTimestamp, label);
	}

	public Timestamp getTimestamp(String label) {
		return get(label, Timestamp.class);
	}

	public void saveTimestamp(String label, Timestamp when) {
		put(label, when, Timestamp.class);
	}

	public void removeTimestamp(String label) {
		try {
			remove(label, Timestamp.class);
		} catch (Exception ignore) {
		}
	}

	public void saveKey(String name, Key key) {
		put(name, key, Key.class);
	}

	public Key getKey(String name) {
		return get(name, Key.class);
	}

	public boolean hasKey(String name) {
		return hasVia(this::getKey, name);
	}

	public void removeKey(String name) {
		try {
			remove(name, Key.class);
		} catch (Exception ignore) {
		}
	}

	public void saveTopicMeta(String name, ConsensusCreateTopicTransactionBody meta, Long approxConsensusTime) {
		put(name, meta);
		put(name, approxConsensusTime + meta.getAutoRenewPeriod().getSeconds() + 60);
	}

	public void saveTopicMeta(String name, ConsensusUpdateTopicTransactionBody txn) {
		ConsensusCreateTopicTransactionBody.Builder meta;
		if (hasTopicMeta(name)) {
			meta = getTopicMeta(name).toBuilder();
		} else {
			meta = ConsensusCreateTopicTransactionBody.newBuilder();
		}
		if (txn.hasAdminKey()) {
			meta.setAdminKey(txn.getAdminKey());
		}
		if (txn.hasAutoRenewAccount()) {
			meta.setAutoRenewAccount(txn.getAutoRenewAccount());
		}
		if (txn.hasAutoRenewPeriod()) {
			meta.setAutoRenewPeriod(txn.getAutoRenewPeriod());
		}
		if (txn.hasSubmitKey()) {
			meta.setSubmitKey(txn.getSubmitKey());
		}
		if (txn.hasMemo()) {
			meta.setMemo(txn.getMemo().getValue());
		}
		put(name, meta.build());
		if (txn.hasExpirationTime()) {
			put(name, txn.getExpirationTime().getSeconds());
		}
	}

	public ConsensusCreateTopicTransactionBody getTopicMeta(String name) {
		return get(name, ConsensusCreateTopicTransactionBody.class);
	}

	public long getTopicExpiry(String name) {
		return get(name, Long.class);
	}

	public boolean hasTopicMeta(String name) {
		return hasVia(this::getTopicMeta, name);
	}

	public void saveBytes(String name, ByteString bytes) {
		put(name, bytes, ByteString.class);
	}

	public byte[] getBytes(String name) {
		return get(name, ByteString.class).toByteArray();
	}

	public void saveAmount(String name, Long amount) {
		put(name, amount);
	}

	public Long getAmount(String name) {
		return get(name, Long.class);
	}

	public void saveSigRequirement(String name, Boolean isRequired) {
		put(name, isRequired);
	}

	public void removeSigRequirement(String name) {
		remove(name, Boolean.class);
	}

	public boolean isSigRequired(String name) {
		try {
			return get(name, Boolean.class);
		} catch (Throwable ignore) {
		}
		return setup.defaultReceiverSigRequired();
	}

	public boolean hasSigRequirement(String name) {
		return hasVia(this::isSigRequired, name);
	}

	private <T> boolean hasVia(Function<String, T> tGetter, String thing) {
		try {
			tGetter.apply(thing);
			return true;
		} catch (Throwable ignore) {
			return false;
		}
	}

	public void saveTxnId(String name, TransactionID txnId) {
		put(name, txnId);
	}

	public TransactionID getTxnId(String name) {
		return get(name, TransactionID.class);
	}

	public void saveAccountId(String name, AccountID id) {
		put(name, id);
		put(asAccountString(id), name);
	}

	public void setRecharging(String account, long amount) {
		put(account, Boolean.TRUE);
		put(account + "Recharge", amount);
	}

	public boolean isRecharging(String account) {
		return registry.containsKey(full(account, Boolean.class));
	}

	public Long getRechargeAmount(String account) {
		return get(account + "Recharge", Long.class);
	}

	public void setRechargingTime(String account, Instant time) {
		put(account + "RechargeTime", time);
	}

	public Instant getRechargingTime(String account) {
		try {
			return get(account + "RechargeTime", Instant.class);
		} catch (Exception ignore) {
			return Instant.MIN;
		}
	}

	public void setRechargingWindow(String account, Integer seconds) {
		put(account + "RechargeWindow", seconds);
	}

	public boolean hasRechargingWindow(String rechargingAccount) {
		return registry.get(full(rechargingAccount + "RechargeWindow", Integer.class)) != null;
	}

	public Integer getRechargingWindow(String account) {
		return getOrElse(account + "RechargeWindow", Integer.class, ZERO);
	}

	public boolean hasAccountId(String name) {
		return hasVia(this::getAccountID, name);
	}

	public AccountID getAccountID(String name) {
		return get(name, AccountID.class);
	}

	public String getAccountIdName(AccountID account) {
		return get(asAccountString(account), String.class);
	}

	public void removeAccount(String name) {
		try {
			var id = getAccountID(name);
			remove(name, AccountID.class);
			remove(asAccountString(id), String.class);
		} catch (Exception ignore) {
		}
	}

	public void saveTopicId(String name, TopicID id) {
		put(name, id);
		put(HapiPropertySource.asTopicString(id), name);
	}

	public TopicID getTopicID(String name) {
		return get(name, TopicID.class);
	}

	public boolean hasFileId(String name) {
		return hasVia(this::getFileId, name);
	}

	public void saveFileId(String name, FileID id) {
		put(name, id);
	}

	public FileID getFileId(String name) {
		return get(name, FileID.class);
	}

	public void removeFileId(String name) {
		try {
			remove(name, FileID.class);
		} catch (Exception ignore) {
		}
	}

	public void saveContractList(String name, List<ContractID> list) {
		long listSize = list.size();
		saveAmount(name + "Size", listSize);
		for (int i = 0; i < listSize; i++) {
			saveContractId(name + i, list.get(i));
		}
	}

	public void saveContractId(String name, ContractID id) {
		put(name, id);
	}

	public ContractID getContractId(String name) {
		return get(name, ContractID.class);
	}

	public boolean hasContractId(String name) {
		return hasVia(this::getContractId, name);
	}

	public void removeContractId(String name) {
		try {
			remove(name, ContractID.class);
		} catch (Exception ignore) {
		}
	}

	public void saveContractInfo(String name, ContractGetInfoResponse.ContractInfo info) {
		put(name, info);
	}

	public void removeContractInfo(String name) {
		try {
			remove(name, ContractGetInfoResponse.ContractInfo.class);
		} catch (Exception ignore) {
		}
	}

	public ContractGetInfoResponse.ContractInfo getContractInfo(String name) {
		return get(name, ContractGetInfoResponse.ContractInfo.class);
	}

	public <T> T getId(String name, Class<T> type) {
		return get(name, type);
	}

	private synchronized void remove(String name, Class<?> type, Optional<HapiSpecOperation> cause) {
		registry.remove(full(name, type));
		notifyAllOnDelete(type, name, cause);
	}

	private synchronized void remove(String name, Class<?> type) {
		remove(name, type, Optional.empty());
	}

	private void notifyAllOnDelete(Class type, String name, Optional<HapiSpecOperation> cause) {
		Optional.ofNullable(listenersByType.get(type)).ifPresent(a -> a.forEach(l -> l.onDelete(name, cause)));
	}

	private synchronized void put(String name, Object obj, Optional<HapiSpecOperation> cause, Class type) {
		if (obj == null) {
			return;
		}
		registry.put(full(name, type), obj);
		notifyAllOnPut(type, name, obj, cause);
	}

	private synchronized void put(String name, Object obj, Class<?> type) {
		put(name, obj, Optional.empty(), type);
	}

	private synchronized void put(String name, Object obj) {
		put(name, obj, obj.getClass());
	}

	private void notifyAllOnPut(Class type, String name, Object value, Optional<HapiSpecOperation> cause) {
		Optional.ofNullable(listenersByType.get(type)).ifPresent(a -> a.forEach(l -> {
			Class<?> lType = l.forType();
			notifyOnPut(l, lType, name, value, cause);
		}));
	}

	private <T> void notifyOnPut(
			RegistryChangeListener<T> listener,
			Class<T> type,
			String name,
			Object value,
			Optional<HapiSpecOperation> cause
	) {
		listener.onPut(name, type.cast(value), cause);
	}

	private synchronized <T> T get(String name, Class<T> type) {
		Object v = registry.get(full(name, type));
		if (v == null) {
			throw new RegistryNotFound("Missing " + type.getSimpleName() + " '" + name + "'!");
		}
		return type.cast(v);
	}

	private synchronized <T> T getOrElse(String name, Class<T> type, T defaultValue) {
		Object v = registry.get(full(name, type));
		if (v == null) {
			return defaultValue;
		}
		return type.cast(v);
	}

	private String full(String name, Class<?> type) {
		String typeName = type.getSimpleName();
		return typeName + "-" + name;
	}

	public Map<Class, Long> typeCounts() {
		return registry.values()
				.stream()
				.collect(groupingBy(Object::getClass, counting()));
	}

	public List<String> stringValues() {
		return registry.entrySet()
				.stream()
				.filter(entry -> entry.getValue().getClass().equals(String.class))
				.map(entry -> String.format("%s -> %s", entry.getKey(), entry.getValue().toString()))
				.collect(toList());
	}

	public void save(String path) {
		FileOutputStream fos = null;
		log.info("Serialize registry to : " + path);
		try {
			fos = new FileOutputStream(path);
			ObjectOutputStream oos = new ObjectOutputStream(fos);
			oos.writeObject(registry);
			fos.close();
			fos = null;
		} catch (NotSerializableException e) {
			log.error("Serializable exception catched while saving registry to " + path + ":" + e);
		} catch (FileNotFoundException e) {
			log.error("File not found exception catched while serializing registry to " + path + ":" + e);
		} catch (Exception e) {
			log.error("Other exception catched while serializing registry to " + path + ":" + e);
		} finally {
			try {
				if (fos != null) {
					fos.close();
				}
			} catch (IOException e) {
				log.error("IO exception catched while serializing registry to " + path + ":" + e);
			}
		}
	}

	public void load(String path) {
		FileInputStream fis = null;

		log.info("Deserialize registry from : " + path);
		try {
			fis = new FileInputStream(path);
			ObjectInputStream ois = new ObjectInputStream(fis);
			Map newValues = (Map<String, Object>) ois.readObject();

			registry.putAll(newValues);

			fis.close();
			fis = null;
		} catch (Exception e) {
			log.error("Deserializable exception catched while deserializing registry from " + path + ":" + e);
		} finally {
			try {
				if (fis != null) {
					fis.close();
				}
			} catch (IOException e) {
				log.error("IO exception catched while deserializing registry from " + path + ":" + e);
			}
		}
		log.info("Successfully deserialized registry from " + path);
	}
}
