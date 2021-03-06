package com.hedera.services.legacy.handler;

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

import com.google.common.annotations.VisibleForTesting;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.contracts.execution.SolidityLifecycle;
import com.hedera.services.contracts.execution.SoliditySigsVerifier;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.files.store.FcBlobsBytesStore;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.txns.validation.PureValidation;

import static com.hedera.services.utils.EntityIdUtils.asSolidityAddress;
import com.hedera.services.utils.MiscUtils;
import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hedera.services.legacy.evm.SolidityExecutor;
import com.swirlds.fcmap.FCMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.core.Transaction;
import org.ethereum.db.ServicesRepositoryRoot;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.program.Program;
import org.spongycastle.pqc.math.linearalgebra.ByteUtils;
import org.spongycastle.util.encoders.DecoderException;
import com.google.common.primitives.Longs;
import com.google.protobuf.ByteString;
import com.google.protobuf.TextFormat;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCallLocalQuery;
import com.hederahashgraph.api.proto.java.ContractCallLocalResponse;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractGetInfoResponse.ContractInfo;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractLoginfo;
import com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseHeader;
import com.hederahashgraph.api.proto.java.SystemDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.SystemUndeleteTransactionBody;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.fee.FeeBuilder;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.SequenceNumber;
import com.hedera.services.state.merkle.MerkleBlobMeta;
import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.logic.ApplicationConstants;
import com.hedera.services.contracts.sources.LedgerAccountsSource;
import com.hedera.services.legacy.config.PropertiesLoader;

import static com.hedera.services.contracts.execution.DomainUtils.fakeBlock;
import static com.hedera.services.contracts.sources.AddressKeyedMapFactory.bytecodeMapFrom;
import static com.hedera.services.contracts.sources.AddressKeyedMapFactory.storageMapFrom;
import static com.hedera.services.utils.EntityIdUtils.asAccount;
import static com.hedera.services.utils.EntityIdUtils.asSolidityAddressHex;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.*;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.builder.RequestBuilder.getTimestamp;
import static com.hederahashgraph.builder.RequestBuilder.getTransactionReceipt;
import static com.hederahashgraph.builder.RequestBuilder.getTransactionRecord;
import static com.hedera.services.legacy.core.jproto.JKey.convertJKey;
import static com.hedera.services.legacy.core.jproto.JKey.convertKey;

/**
 * Post-consensus execution of smart contract api calls
 */
public class SmartContractRequestHandler {
	private static final Logger log = LogManager.getLogger(SmartContractRequestHandler.class);

	private Map<byte[], byte[]> storageView;
	private Map<byte[], byte[]> bytecodeView;

	private AccountID funding;
	private HederaLedger ledger;
	private LedgerAccountsSource ledgerSource;
	private ServicesRepositoryRoot repository;
	private FCMap<MerkleEntityId, MerkleAccount> accounts;
	private FCMap<MerkleBlobMeta, MerkleOptionalBlob> storageMap;
	private HbarCentExchange exchange;
	private TransactionContext txnCtx;
	private UsagePricesProvider usagePrices;
	private PropertySource properties;
	private Supplier<ServicesRepositoryRoot> newPureRepo;
	private SolidityLifecycle lifecycle;
	private SoliditySigsVerifier sigsVerifier;

	public SmartContractRequestHandler(
			ServicesRepositoryRoot repository,
			AccountID funding,
			HederaLedger ledger,
			FCMap<MerkleEntityId, MerkleAccount> accounts,
			FCMap<MerkleBlobMeta, MerkleOptionalBlob> storageMap,
			LedgerAccountsSource ledgerSource,
			TransactionContext txnCtx,
			HbarCentExchange exchange,
			UsagePricesProvider usagePrices,
			PropertySource properties,
			Supplier<ServicesRepositoryRoot> newPureRepo,
			SolidityLifecycle lifecycle,
			SoliditySigsVerifier sigsVerifier
	) {
		this.repository = repository;
		this.newPureRepo = newPureRepo;
		this.accounts = accounts;
		this.funding = funding;
		this.ledger = ledger;
		this.exchange = exchange;
		this.storageMap = storageMap;
		this.ledgerSource = ledgerSource;
		this.txnCtx = txnCtx;
		this.usagePrices = usagePrices;
		this.properties = properties;
		this.lifecycle = lifecycle;
		this.sigsVerifier = sigsVerifier;

		var blobStore = new FcBlobsBytesStore(MerkleOptionalBlob::new, storageMap);
		storageView = storageMapFrom(blobStore);
		bytecodeView = bytecodeMapFrom(blobStore);
	}

	/**
	 * Create a new contract
	 *
	 * @param transaction
	 * 		API request to create the contract
	 * @param consensusTime
	 * 		Platform consensus time
	 * @param contractByteCode
	 * 		Byte code to execute to get the contract code
	 * @param sequenceNum
	 * 		To generate the next ContractID
	 * @return Details of contract creation result
	 */
	public TransactionRecord createContract(
			TransactionBody transaction,
			Instant consensusTime,
			byte[] contractByteCode,
			SequenceNumber sequenceNum
	) {
		Transaction tx;
		ContractCreateTransactionBody createContract = transaction.getContractCreateInstance();
		TransactionID transactionID = transaction.getTransactionID();
		Instant startTime = RequestBuilder.convertProtoTimeStamp(transactionID.getTransactionValidStart());
		AccountID senderAccount = transactionID.getAccountID();
		String senderAccountEthAddress = asSolidityAddressHex(senderAccount);
		BigInteger gas;
		if (createContract.getGas() <= PropertiesLoader.getMaxGasLimit()) {
			gas = BigInteger.valueOf(createContract.getGas());
		} else {
			gas = BigInteger.valueOf(PropertiesLoader.getMaxGasLimit());
			log.debug("Gas offered: {} reduced to maxGasLimit: {} in create",
					() -> createContract.getGas(), () -> PropertiesLoader.getMaxGasLimit());
		}
		String contractByteCodeString = new String(contractByteCode);
		if (createContract.getConstructorParameters() != null && !createContract.getConstructorParameters().isEmpty()) {
			String constructorParamsHexString = ByteUtils.toHexString(
					createContract.getConstructorParameters().toByteArray());
			contractByteCodeString += constructorParamsHexString;
		}
		BigInteger value = BigInteger.ZERO;
		if (createContract.getInitialBalance() > 0) {
			value = BigInteger.valueOf(createContract.getInitialBalance());
		}
		if (createContract.hasAdminKey()) {
			Key adminKey = createContract.getAdminKey();
			if (!adminKey.hasContractID()) {
				try {
					serializeAdminKey(adminKey);
				} catch (Exception ex) {
					return getFailureTransactionRecord(transaction, consensusTime, SERIALIZATION_FAILED);
				}
			}
		}
		Timestamp consensusTimeStamp = Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond()).build();
		BigInteger biGasPrice;
		try {
			long createGasPrice = getContractCreateGasPriceInTinyBars(consensusTimeStamp);
			biGasPrice = BigInteger.valueOf(createGasPrice);
		} catch (Exception e1) {
			e1.printStackTrace();
			if (log.isDebugEnabled()) {
				log.debug("ContractCreate gas coefficient could not be found in fee schedule ", e1);
			}
			return getFailureTransactionRecord(transaction, consensusTime, CONTRACT_EXECUTION_EXCEPTION);
		}
		try {
			tx = new Transaction(
					null,
					biGasPrice,
					gas,
					senderAccountEthAddress,
					null,
					value,
					contractByteCodeString);
		} catch (DecoderException e) {
			return getFailureTransactionRecord(transaction, consensusTime, ERROR_DECODING_BYTESTRING);
		}

		TransactionRecord result;
		try {
			long rbhInTinybars = getContractCreateRbhInTinyBars(consensusTimeStamp);
			long sbhInTinybars = getContractCreateSbhInTinyBars(consensusTimeStamp);
			result = run(
					tx,
					senderAccountEthAddress,
					transaction,
					consensusTime,
					startTime,
					sequenceNum,
					rbhInTinybars,
					sbhInTinybars,
					true);
		} catch (Exception e) {
			e.printStackTrace();
			result = getFailureTransactionRecord(transaction, consensusTime, CONTRACT_EXECUTION_EXCEPTION);
		}
		if (result.getReceipt().getStatus() == SUCCESS) {
			ResponseCodeEnum respCode = createMemoAdminKey(transaction, result);
			if (respCode != SUCCESS) {
				return TransactionRecord.newBuilder()
						.setReceipt(getTransactionReceipt(respCode, exchange.activeRates()))
						.setConsensusTimestamp(MiscUtils.asTimestamp(consensusTime))
						.setTransactionID(transactionID).setMemo(createContract.getMemo())
						.setTransactionFee(transaction.getTransactionFee()).build();
			}
			setParentPropertiesForChildrenContracts(
					asAccount(result.getReceipt().getContractID()),
					result.getContractCreateResult().getCreatedContractIDsList());
		}
		return result;
	}

	private byte[] serializeAdminKey(Key adminKey) throws Exception {
		JKey jKey = convertKey(adminKey, 1);
		return jKey.serialize();
	}

	/**
	 * Builds a failure result to be returned by the caller
	 *
	 * @param transaction
	 * 		API request that caused the error
	 * @param consensusTime
	 * 		Platform consensus time
	 * @param responseCode
	 * 		Error code to be build into the result
	 * @return Simple return record for the failed API call
	 */
	public TransactionRecord getFailureTransactionRecord(
			TransactionBody transaction, Instant consensusTime, ResponseCodeEnum responseCode
	) {
		TransactionReceipt transactionReceipt = getTransactionReceipt(responseCode, exchange.activeRates());

		return getTransactionRecord(
				transaction.getTransactionFee(),
				transaction.getMemo(),
				transaction.getTransactionID(),
				getTimestamp(consensusTime),
				transactionReceipt).build();
	}

	private ContractCallLocalResponse runPure(
			Transaction solidityTxn,
			Instant startTime,
			long maxResultSize
	) {
		var mockConsensusTime = Timestamp.newBuilder().setSeconds(startTime.getEpochSecond()).build();
		var pureRepository = newPureRepo.get();
		var executor = new SolidityExecutor(
				solidityTxn,
				pureRepository,
				fakeBlock(startTime),
				null,
				null,
				null,
				startTime,
				null,
				getContractCallRbhInTinyBars(mockConsensusTime),
				getContractCallSbhInTinyBars(mockConsensusTime),
				txnCtx,
		true,
				sigsVerifier);

		var result = lifecycle.runPure(maxResultSize, executor);

		var header = RequestBuilder.getResponseHeader(result.getValue(), 0L, ANSWER_ONLY, ByteString.EMPTY);
		return ContractCallLocalResponse.newBuilder()
				.setHeader(header)
				.setFunctionResult(result.getKey())
				.build();
	}

	private TransactionRecord run(
			Transaction solidityTxn,
			String payerAddress,
			TransactionBody txn,
			Instant consensusTime,
			Instant startTime,
			SequenceNumber sequenceNum,
			long rbh,
			long sbh,
			boolean isCreate
	) {
		var executor = new SolidityExecutor(
				solidityTxn,
				this.repository,
				fakeBlock(consensusTime),
				payerAddress,
				asSolidityAddressHex(this.funding),
				txn,
				startTime,
				sequenceNum,
				rbh,
				sbh,
				txnCtx,
				false,
				sigsVerifier);
		var result = lifecycle.run(executor, repository);

		var receiptBuilder = RequestBuilder.getTransactionReceipt(
				result.getValue(),
				exchange.activeRates()
		).toBuilder();
		var recordBuilder = RequestBuilder.getTransactionRecord(
				txn.getTransactionFee(),
				txn.getMemo(),
				txn.getTransactionID(),
				RequestBuilder.getTimestamp(consensusTime),
				com.hederahashgraph.api.proto.java.TransactionReceipt.getDefaultInstance());
		if (isCreate) {
			if (result.getValue() == SUCCESS) {
				receiptBuilder.setContractID(result.getKey().getContractID());
			}
			recordBuilder.setContractCreateResult(result.getKey());
		} else {
			recordBuilder.setContractCallResult(result.getKey());
			receiptBuilder.setContractID(txn.getContractCall().getContractID());
		}
		recordBuilder.setReceipt(receiptBuilder);

		return recordBuilder.build();
	}

	private ResponseCodeEnum createMemoAdminKey(TransactionBody transaction, TransactionRecord txRecord) {
		ContractCreateTransactionBody op = transaction.getContractCreateInstance();

		String memo = op.getMemo();

		JKey adminJKey = null;
		try {
			if (op.hasAdminKey()) {
				adminJKey = convertKey(op.getAdminKey(), 1);
			}
		} catch (Exception ex) {
			log.error("Admin key serialization Failed " + ex.getMessage());
			return ResponseCodeEnum.SERIALIZATION_FAILED;
		}

		AccountID id = asAccount(txRecord.getReceipt().getContractID());
		if (!ledger.exists(id)) {
			return CONTRACT_EXECUTION_EXCEPTION;
		}
		HederaAccountCustomizer customizer = new HederaAccountCustomizer().memo(memo);
		if (adminJKey != null) {
			customizer.key(adminJKey);
		}
		ledger.customize(id, customizer);
		return SUCCESS;
	}

	private void setParentPropertiesForChildrenContracts(AccountID parent, List<ContractID> children) {
		MerkleAccount parentAccount = ledger.get(parent);
		HederaAccountCustomizer customizer = new HederaAccountCustomizer().key(parentAccount.getKey())
				.memo(parentAccount.getMemo())
				.expiry(parentAccount.getExpiry())
				.autoRenewPeriod(parentAccount.getAutoRenewSecs())
				.proxy(parentAccount.getProxy())
				.fundsSentRecordThreshold(parentAccount.getSenderThreshold())
				.fundsReceivedRecordThreshold(parentAccount.getReceiverThreshold());
		for (ContractID child : children) {
			ledger.customize(asAccount(child), customizer);
		}
	}

	/**
	 * Execute a smart contract call
	 *
	 * @param transaction
	 * 		API request to execute the contract method
	 * @param consensusTime
	 * 		Platform consensus time
	 * @param sequenceNum
	 * 		Incrementing counter in case an entity is created
	 * @return Details of contract call result
	 */
	public TransactionRecord contractCall(TransactionBody transaction, Instant consensusTime,
			SequenceNumber sequenceNum) {
		Transaction tx = null;
		ContractCallTransactionBody contractCall = transaction.getContractCall();
		TransactionID transactionID = transaction.getTransactionID();
		AccountID senderAccount = transactionID.getAccountID();
		Instant startTime =
				RequestBuilder.convertProtoTimeStamp(transactionID.getTransactionValidStart());
		String senderAccountEthAddress = asSolidityAddressHex(senderAccount);
		AccountID receiverAccount =
				AccountID.newBuilder().setAccountNum(contractCall.getContractID().getContractNum())
						.setRealmNum(contractCall.getContractID().getRealmNum())
						.setShardNum(contractCall.getContractID().getShardNum()).build();
		String receiverAccountEthAddress = asSolidityAddressHex(receiverAccount);
		ResponseCodeEnum callResponseStatus = validateContractExistence(contractCall.getContractID());
		if (callResponseStatus == ResponseCodeEnum.OK) {
			byte[] senderAccAddressBytes = ByteUtil.hexStringToBytes(senderAccountEthAddress);
			BigInteger gas;
			if (contractCall.getGas() <= PropertiesLoader.getMaxGasLimit()) {
				gas = BigInteger.valueOf(contractCall.getGas());
			} else {
				gas = BigInteger.valueOf(PropertiesLoader.getMaxGasLimit());
				log.debug("Gas offered: {} reduced to maxGasLimit: {} in call", () -> contractCall.getGas(),
						() -> PropertiesLoader.getMaxGasLimit());
			}

			BigInteger senderNonce = repository.getNonce(senderAccAddressBytes);

			String data = "";
			if (contractCall.getFunctionParameters() != null
					&& !contractCall.getFunctionParameters().isEmpty()) {
				data = ByteUtil.toHexString(contractCall.getFunctionParameters().toByteArray());
			}
			BigInteger value = BigInteger.ZERO;
			if (contractCall.getAmount() > 0) {
				value = BigInteger.valueOf(contractCall.getAmount());
			}

			BigInteger biGasPrice = BigInteger.ZERO;
			Timestamp consensusTimeStamp = Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond()).build();
			long rbhInTinybars = 0L;
			long sbhInTinybars = 0L;
			try {

				long callGasPrice = getContractCallGasPriceInTinyBars(consensusTimeStamp);
				biGasPrice = BigInteger.valueOf(callGasPrice);
			} catch (Exception e1) {
				if (log.isDebugEnabled())
					log.debug("ContractCall gas coefficient could not be found in fee schedule " + e1.getMessage());
				return getFailureTransactionRecord(transaction, consensusTime,
						ResponseCodeEnum.CONTRACT_EXECUTION_EXCEPTION);
			}
			tx = new Transaction(senderNonce, biGasPrice, gas, senderAccountEthAddress,
					receiverAccountEthAddress, value, data);

			try {
				rbhInTinybars = getContractCallRbhInTinyBars(consensusTimeStamp);
				sbhInTinybars = getContractCallSbhInTinyBars(consensusTimeStamp);
				var record = run(
					tx,
					senderAccountEthAddress,
					transaction,
					consensusTime,
					startTime,
					sequenceNum,
					rbhInTinybars,
					sbhInTinybars,
					false);
				setParentPropertiesForChildrenContracts(
						receiverAccount,
						record.getContractCallResult().getCreatedContractIDsList());
				return record;
			} catch (Exception e) {
				e.printStackTrace();
				return getFailureTransactionRecord(transaction, consensusTime, CONTRACT_EXECUTION_EXCEPTION);
			}
		} else {
			com.hederahashgraph.api.proto.java.TransactionReceipt transactionReceipt =
					getTransactionReceipt(transaction.getContractCall().getContractID(),
							callResponseStatus, exchange.activeRates());

			TransactionRecord.Builder recordBuilder = getTransactionRecord(
					transaction.getTransactionFee(), transaction.getMemo(), transaction.getTransactionID(),
					getTimestamp(consensusTime),
					com.hederahashgraph.api.proto.java.TransactionReceipt.getDefaultInstance());

			ContractFunctionResult.Builder resultsBuilder = ContractFunctionResult.newBuilder();
			resultsBuilder.setContractID(contractCall.getContractID());
			resultsBuilder.setErrorMessage(
					"Invalid Contract Address: contractNum=" + contractCall.getContractID().getContractNum());
			recordBuilder = recordBuilder.setContractCallResult(resultsBuilder.build());

			recordBuilder.setReceipt(transactionReceipt);
			return recordBuilder.build();
		}
	}

	/**
	 * Execute a smart contract local call.  This does not go through consensus and may not
	 * change any state.
	 *
	 * @param transactionContractCallLocal
	 * 		API request to execute the contract method
	 * @param currentTimeMs
	 * 		Execution timestamp, not a platform consensus time
	 * @return Details of local execution result
	 * @throws Exception
	 * 		Passes through lower-level exceptions; does not generate any.
	 */
	public ContractCallLocalResponse contractCallLocal(
			ContractCallLocalQuery transactionContractCallLocal, long currentTimeMs) throws Exception {
		ContractCallLocalResponse responseToReturn = ContractCallLocalResponse.getDefaultInstance();
		Transaction tx = null;
		TransactionBody body = com.hedera.services.legacy.proto.utils.CommonUtils
				.extractTransactionBody(transactionContractCallLocal.getHeader().getPayment());
		AccountID senderAccount = body.getTransactionID().getAccountID();
		String senderAccountEthAddress = asSolidityAddressHex(senderAccount);
		AccountID receiverAccount = AccountID.newBuilder()
				.setAccountNum(transactionContractCallLocal.getContractID().getContractNum())
				.setRealmNum(transactionContractCallLocal.getContractID().getRealmNum())
				.setShardNum(transactionContractCallLocal.getContractID().getShardNum()).build();
		String receiverAccountEthAddress = asSolidityAddressHex(receiverAccount);
		ResponseCodeEnum callResponseStatus =
				validateContractExistence(transactionContractCallLocal.getContractID());
		if (callResponseStatus == ResponseCodeEnum.OK) {
			byte[] senderAccAddressBytes = ByteUtil.hexStringToBytes(senderAccountEthAddress);
			BigInteger gas;
			if (transactionContractCallLocal.getGas() <= PropertiesLoader.getMaxGasLimit()) {
				gas = BigInteger.valueOf(transactionContractCallLocal.getGas());
			} else {
				gas = BigInteger.valueOf(PropertiesLoader.getMaxGasLimit());
				log.debug("Gas offered: {} reduced to maxGasLimit: {} in local call",
						() -> transactionContractCallLocal.getGas(), () -> PropertiesLoader.getMaxGasLimit());
			}
			BigInteger senderNonce = repository.getNonce(senderAccAddressBytes);

			String data = "";
			if (transactionContractCallLocal.getFunctionParameters() != null
					&& !transactionContractCallLocal.getFunctionParameters().isEmpty()) {
				data = ByteUtil
						.toHexString(transactionContractCallLocal.getFunctionParameters().toByteArray());
			}
			BigInteger value = BigInteger.ZERO;

			tx = new Transaction(senderNonce, BigInteger.ONE, gas, senderAccountEthAddress,
					receiverAccountEthAddress, value, data);
			responseToReturn = runPure(
					tx,
					Instant.ofEpochMilli(currentTimeMs),
					transactionContractCallLocal.getMaxResultSize());
		} else {
			ResponseHeader responseHeader = RequestBuilder.getResponseHeader(callResponseStatus, 0l,
					ANSWER_ONLY, ByteString.EMPTY);
			responseToReturn = ContractCallLocalResponse.newBuilder().setHeader(responseHeader).build();
			if (log.isDebugEnabled()) {
				log.debug("contractCallLocal  -Invalid Contract ID "
						+ TextFormat.shortDebugString(transactionContractCallLocal.getContractID()));
			}
		}
		return responseToReturn;
	}

	/**
	 * Return available information about the contract
	 *
	 * @param cid
	 * @return Contract information
	 * @throws Exception
	 * 		Passes through lower-level exceptions; does not generate any
	 */
	public ContractInfo getContractInfo(ContractID cid) {
		ContractInfo infoToReturn = null;
		AccountID id = asAccount(cid);
		String contractEthAddress = asSolidityAddressHex(id);
		if (!StringUtils.isEmpty(contractEthAddress)) {
			ContractInfo.Builder builder = ContractInfo.newBuilder();
			MerkleAccount contract = accounts.get(MerkleEntityId.fromPojoAccountId(id));
			if (contract != null && contract.isSmartContract()) {
				builder.setContractID(cid)
						.setBalance(contract.getBalance())
						.setMemo(contract.getMemo())
						.setAccountID(id)
						.setAutoRenewPeriod(Duration.newBuilder().setSeconds(contract.getAutoRenewSecs()))
						.setExpirationTime(Timestamp.newBuilder().setSeconds(contract.getExpiry()))
						.setContractAccountID(contractEthAddress);
				var address = asSolidityAddress(cid);
				long bytesUsed = lengthIfPresent(storageView.get(address)) + lengthIfPresent(bytecodeView.get(address));
				builder.setStorage(bytesUsed);

				JKey key = contract.getKey();
				if (key != null) {
					try {
						builder.setAdminKey(convertJKey(key, 1));
					} catch (Exception ignore) {
					}
				}
				infoToReturn = builder.build();
			}
		}
		return infoToReturn;
	}

	private int lengthIfPresent(byte[] data) {
		return Optional.ofNullable(data).map(bytes -> bytes.length).orElse(0);
	}

	/**
	 * Modify an existing contract
	 *
	 * @param transaction
	 * 		API request to modify the contract
	 * @param consensusTime
	 * 		Platform consensus time
	 * @return Details of contract update result
	 */
	public TransactionRecord updateContract(TransactionBody transaction, Instant consensusTime) {
		TransactionReceipt receipt;
		ContractUpdateTransactionBody op = transaction.getContractUpdateInstance();
		ContractID cid = op.getContractID();
		ResponseCodeEnum validity = validateContractExistence(cid);
		if (validity == OK) {
			AccountID id = asAccount(cid);
			try {
				MerkleAccount contract = ledger.get(id);
				if (contract != null) {
					boolean memoProvided = op.getMemo().length() > 0;
					boolean adminKeyExist = Optional.ofNullable(contract.getKey())
							.map(key -> !key.hasContractID())
							.orElse(false);
					if (!adminKeyExist &&
							(op.hasProxyAccountID() ||
									op.hasAutoRenewPeriod() || op.hasFileID() || op.hasAdminKey() || memoProvided)) {
						receipt = getTransactionReceipt(MODIFYING_IMMUTABLE_CONTRACT, exchange.activeRates());
					} else if (op.hasExpirationTime() && contract.getExpiry() > op.getExpirationTime().getSeconds()) {
						receipt = getTransactionReceipt(EXPIRATION_REDUCTION_NOT_ALLOWED, exchange.activeRates());
					} else {
						HederaAccountCustomizer customizer = new HederaAccountCustomizer();
						if (op.hasProxyAccountID()) {
							customizer.proxy(EntityId.ofNullableAccountId(op.getProxyAccountID()));
						}
						if (op.hasAutoRenewPeriod()) {
							customizer.autoRenewPeriod(op.getAutoRenewPeriod().getSeconds());
						}
						if (op.hasExpirationTime()) {
							customizer.expiry(op.getExpirationTime().getSeconds());
						}
						if (memoProvided) {
							customizer.memo(op.getMemo());
						}
						if (op.hasAdminKey()) {
							JKey newAdminKey = convertKey(op.getAdminKey(), 1);
							if (!(newAdminKey instanceof JContractIDKey)) {
								customizer.key(newAdminKey);
							}
						}
						ledger.customize(id, customizer);

						receipt = getTransactionReceipt(SUCCESS, exchange.activeRates());
					}
				} else {
					receipt = getTransactionReceipt(FAIL_INVALID, exchange.activeRates());
				}
			} catch (Exception ex) {
				log.warn("Admin key serialization Failed: tx={}", transaction, ex);
				receipt = getTransactionReceipt(SERIALIZATION_FAILED, exchange.activeRates());
			}
		} else {
			receipt = getTransactionReceipt(validity, exchange.activeRates());
		}

		return getTransactionRecord(
				transaction.getTransactionFee(),
				transaction.getMemo(),
				transaction.getTransactionID(),
				getTimestamp(consensusTime),
				receipt).build();
	}

	/**
	 * Return the stored bytecode for this contract
	 *
	 * @param cid
	 * @return Contract bytecode as a Bytestring
	 */
	public ByteString getContractBytecode(ContractID cid) {
		AccountID id = asAccount(cid);
		MerkleAccount contract = accounts.get(MerkleEntityId.fromPojoAccountId(id));
		if (contract != null && contract.isSmartContract()) {
			String contractEthAddress = asSolidityAddressHex(id);
			byte[] contractEthAddressBytes = ByteUtil.hexStringToBytes(contractEthAddress);
			return ByteString.copyFrom(repository.getCode(contractEthAddressBytes));
		}
		return ByteString.EMPTY;
	}

	/**
	 * check if a contract with given contractId exists
	 *
	 * @param cid
	 * @return CONTRACT_DELETED if deleted, INVALID_CONTRACT_ID if doesn't exist, OK otherwise
	 */
	public ResponseCodeEnum validateContractExistence(ContractID cid) {
		return PureValidation.queryableContractStatus(cid, accounts);
	}

	/**
	 * System account deletes any contract. This simply marks the contract as deleted.
	 *
	 * @param txBody
	 * 		API request to delete the contract
	 * @param consensusTimestamp
	 * 		Platform consensus time
	 * @return Details of contract deletion result
	 */
	public TransactionRecord systemDelete(TransactionBody txBody, Instant consensusTimestamp) {
		SystemDeleteTransactionBody op = txBody.getSystemDelete();
		ContractID cid = op.getContractID();
		long newExpiry = op.getExpirationTime().getSeconds();
		TransactionReceipt receipt;
		FCStorageWrapper storageWrapper = new FCStorageWrapper(storageMap);
		receipt = updateDeleteFlag(cid, true);
		try {
			if (receipt.getStatus().equals(ResponseCodeEnum.SUCCESS)) {
				AccountID id = asAccount(cid);
				long oldExpiry = ledger.expiry(id);
				String path = getSystemTempFilePath(cid);
				storageWrapper.fileCreate(
						path,
						Long.toString(oldExpiry).getBytes(),
						consensusTimestamp.getEpochSecond(),
						consensusTimestamp.getNano(),
						newExpiry,
						null);

				HederaAccountCustomizer customizer = new HederaAccountCustomizer().expiry(newExpiry);
				ledger.customize(id, customizer);
			}
		} catch (Exception e) {
			e.printStackTrace();
			log.debug("File System Exception {} tx= {}", () -> e, () -> TextFormat.shortDebugString(op));
			receipt = getTransactionReceipt(ResponseCodeEnum.FILE_SYSTEM_EXCEPTION, exchange.activeRates());
		}

		TransactionRecord.Builder transactionRecord =
				getTransactionRecord(txBody.getTransactionFee(), txBody.getMemo(),
						txBody.getTransactionID(), getTimestamp(consensusTimestamp), receipt);
		return transactionRecord.build();

	}

	/**
	 * System account undoes the deletion marker on a smart contract that has been deleted but
	 * not yet removed.
	 *
	 * @param txBody
	 * 		API reuest to undelete the contract
	 * @param consensusTimestamp
	 * 		Platform consensus time
	 * @return Details of contract undeletion result
	 */
	public TransactionRecord systemUndelete(TransactionBody txBody, Instant consensusTimestamp) {
		FCStorageWrapper storageWrapper = new FCStorageWrapper(storageMap);
		SystemUndeleteTransactionBody op = txBody.getSystemUndelete();
		ContractID cid = op.getContractID();
		TransactionReceipt receipt = getTransactionReceipt(SUCCESS, exchange.activeRates());

		String path = getSystemTempFilePath(cid);
		long oldExpiry = 0;
		try {
			if (storageWrapper.fileExists(path)) {
				byte[] oldBytes = storageWrapper.fileRead(path);
				oldExpiry = Longs.fromByteArray(oldBytes);
			} else {
				receipt = getTransactionReceipt(INVALID_FILE_ID, exchange.activeRates());
			}
			if (oldExpiry > 0) {
				HederaAccountCustomizer customizer = new HederaAccountCustomizer().expiry(oldExpiry);
				ledger.customize(asAccount(cid), customizer);
			}
			if (receipt.getStatus() == SUCCESS) {
				try {
					receipt = updateDeleteFlag(cid, false);
				} catch (Exception e) {
					receipt = getTransactionReceipt(FAIL_INVALID, exchange.activeRates());
					if (log.isDebugEnabled()) {
						log.debug("systemUndelete exception: can't serialize or deserialize! tx=" + txBody, e);
					}
				}
			}
			storageWrapper.delete(path, consensusTimestamp.getEpochSecond(), consensusTimestamp.getNano());
		} catch (Exception e) {
			log.debug("File System Exception {} tx= {}", () -> e, () -> TextFormat.shortDebugString(op));
			receipt = getTransactionReceipt(FILE_SYSTEM_EXCEPTION, exchange.activeRates());
		}
		TransactionRecord.Builder transactionRecord =
				getTransactionRecord(txBody.getTransactionFee(), txBody.getMemo(),
						txBody.getTransactionID(), getTimestamp(consensusTimestamp), receipt);
		return transactionRecord.build();
	}

	private TransactionReceipt updateDeleteFlag(ContractID cid, boolean deleted) {
		ledger.customize(asAccount(cid), new HederaAccountCustomizer().isDeleted(deleted));
		return getTransactionReceipt(SUCCESS, exchange.activeRates());
	}

	private String getSystemTempFilePath(ContractID contractID) {
		return ApplicationConstants.buildPath(ApplicationConstants.SYSTEM_TEMP_FILE_PATH,
				Long.toString(contractID.getRealmNum()), Long.toString(contractID.getContractNum()));
	}

	/**
	 * Delete an existing contract
	 *
	 * @param transaction
	 * 		API request to delete the contract.
	 * @param consensusTime
	 * 		Platform consensus time
	 * @return Details of contract deletion result
	 * @throws Exception
	 * 		Passes through lower-level exceptions; does not generate any.
	 */
	public TransactionRecord deleteContract(TransactionBody transaction, Instant consensusTime) {
		TransactionReceipt transactionReceipt;
		ContractDeleteTransactionBody op = transaction.getContractDeleteInstance();

		ContractID cid = op.getContractID();
		ResponseCodeEnum validity = validateContractExistence(cid);
		if (validity == ResponseCodeEnum.OK) {
			AccountID beneficiary = Optional.ofNullable(getBeneficiary(op)).orElse(funding);
			validity = validateContractDelete(op);
			if (validity == SUCCESS) {
				validity = ledger.exists(beneficiary) ? SUCCESS : OBTAINER_DOES_NOT_EXIST;
				if (validity == SUCCESS) {
					validity = ledger.isDeleted(beneficiary)
							? (ledger.isSmartContract(beneficiary) ? CONTRACT_DELETED : ACCOUNT_DELETED)
							: SUCCESS;
				}
			}
			if (validity == SUCCESS) {
				AccountID id = asAccount(cid);
				ledger.delete(id, beneficiary);
			}
			transactionReceipt = getTransactionReceipt(validity, exchange.activeRates());
		} else {
			transactionReceipt = getTransactionReceipt(validity, exchange.activeRates());
		}
		return getTransactionRecord(
				transaction.getTransactionFee(),
				transaction.getMemo(),
				transaction.getTransactionID(),
				getTimestamp(consensusTime),
				transactionReceipt).build();
	}

	private ResponseCodeEnum validateContractDelete(ContractDeleteTransactionBody op) {
		AccountID id = asAccount(op.getContractID());
		if (ledger.getBalance(id) > 0) {
			AccountID beneficiary = getBeneficiary(op);
			if (beneficiary == null) {
				return OBTAINER_REQUIRED;
			} else if (beneficiary.equals(id)) {
				return OBTAINER_SAME_CONTRACT_ID;
			} else if (!ledger.exists(beneficiary) || ledger.isDeleted(beneficiary)) {
				return ResponseCodeEnum.OBTAINER_DOES_NOT_EXIST;
			}
		}
		return SUCCESS;
	}

	private AccountID getBeneficiary(ContractDeleteTransactionBody op) {
		if (op.hasTransferAccountID()) {
			return op.getTransferAccountID();
		} else if (op.hasTransferContractID()) {
			return asAccount(op.getTransferContractID());
		}
		return null;
	}

	private long getContractCallRbhInTinyBars(Timestamp at) {
		return rbhPriceTinyBarsGiven(ContractCall, at);
	}

	private long getContractCreateRbhInTinyBars(Timestamp at) {
		return rbhPriceTinyBarsGiven(ContractCreate, at);
	}

	private long rbhPriceTinyBarsGiven(HederaFunctionality function, Timestamp at) {
		FeeData prices = usagePrices.pricesGiven(function, at);
		long feeInTinyCents = prices.getServicedata().getRbh() / 1000;
		long feeInTinyBars = FeeBuilder.getTinybarsFromTinyCents(exchange.rate(at), feeInTinyCents);
		return Math.max(1L, feeInTinyBars);
	}

	private long getContractCreateGasPriceInTinyBars(Timestamp at) {
		return gasPriceTinyBarsGiven(ContractCreate, at);
	}

	private long getContractCallGasPriceInTinyBars(Timestamp at) {
		return gasPriceTinyBarsGiven(ContractCall, at);
	}

	private long gasPriceTinyBarsGiven(HederaFunctionality function, Timestamp at) {
		FeeData prices = usagePrices.pricesGiven(function, at);
		long feeInTinyCents = prices.getServicedata().getGas() / 1000;
		long feeInTinyBars = FeeBuilder.getTinybarsFromTinyCents(exchange.rate(at), feeInTinyCents);
		return Math.max(1L, feeInTinyBars);
	}

	private long getContractCallSbhInTinyBars(Timestamp at) {
		return sbhPriceTinyBarsGiven(ContractCall, at);
	}

	private long getContractCreateSbhInTinyBars(Timestamp at) {
		return sbhPriceTinyBarsGiven(ContractCreate, at);
	}

	private long sbhPriceTinyBarsGiven(HederaFunctionality function, Timestamp at) {
		FeeData prices = usagePrices.pricesGiven(function, at);
		long feeInTinyCents = prices.getServicedata().getSbh() / 1000;
		long feeInTinyBars = FeeBuilder.getTinybarsFromTinyCents(exchange.rate(at), feeInTinyCents);
		return Math.max(1L, feeInTinyBars);
	}

	@VisibleForTesting
	public static long getLogsBillableSize(TransactionRecord transactionRecord) {
		long sizeToReturn = 0L;
		ContractFunctionResult contractFunctionResult = ContractFunctionResult.getDefaultInstance();
		if (transactionRecord.hasContractCallResult()) {
			contractFunctionResult = transactionRecord.getContractCallResult();
		} else if (transactionRecord.hasContractCreateResult()) {
			contractFunctionResult = transactionRecord.getContractCreateResult();
		}
		if (contractFunctionResult.getLogInfoCount() > 0) {
			List<ContractLoginfo> logsList = contractFunctionResult.getLogInfoList();
			for (ContractLoginfo currLog : logsList) {
				int numOfTopics = currLog.getTopicCount();
				long dataSize = 0L;
				ByteString logData = currLog.getData();
				if (logData != null) {
					dataSize = logData.size();
				}
				long currLogBillableSize = Program.calculateLogSize(numOfTopics, dataSize);
				sizeToReturn += currLogBillableSize;
			}
		}
		return sizeToReturn;
	}
}
