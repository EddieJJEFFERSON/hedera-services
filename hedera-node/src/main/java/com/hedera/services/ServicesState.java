package com.hedera.services;

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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.context.ServicesContext;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.context.properties.StandardizedPropertySources;
import com.hedera.services.legacy.config.PropertiesLoader;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleBlobMeta;
import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.state.submerkle.SequenceNumber;
import com.hedera.services.utils.JvmSystemExits;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.services.utils.SystemExits;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.common.Address;
import com.swirlds.common.AddressBook;
import com.swirlds.common.FastCopyable;
import com.swirlds.common.NodeId;
import com.swirlds.common.Platform;
import com.swirlds.common.SwirldState;
import com.swirlds.common.Transaction;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.utility.AbstractMerkleInternal;
import com.swirlds.fcmap.FCMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import static com.hedera.services.state.merkle.MerkleNetworkContext.UNKNOWN_CONSENSUS_TIME;
import static com.hedera.services.context.SingletonContextsManager.CONTEXTS;
import static com.hedera.services.legacy.logic.ApplicationConstants.HEDERA_START_SEQUENCE;
import static com.hedera.services.sigs.HederaToPlatformSigOps.expandIn;
import static com.hedera.services.sigs.sourcing.DefaultSigBytesProvider.DEFAULT_SIG_BYTES;
import static com.hedera.services.utils.EntityIdUtils.accountParsedFromString;

public class ServicesState extends AbstractMerkleInternal implements SwirldState.SwirldState2 {
	private static final Logger log = LogManager.getLogger(ServicesState.class);

	static final int MERKLE_VERSION = 1;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0x8e300b0dfdafbb1aL;
	static final NodeId ID_WITH_INACTIVE_CONTEXT = null;

	static Supplier<AddressBook> legacyTmpBookSupplier = AddressBook::new;

	NodeId nodeId = ID_WITH_INACTIVE_CONTEXT;

	/* Order of v1 Merkle node children */
	static final int ADDRESS_BOOK_CHILD_INDEX = 0;
	static final int NETWORK_CTX_CHILD_INDEX = 1;
	static final int TOPICS_CHILD_INDEX = 2;
	static final int STORAGE_CHILD_INDEX = 3;
	static final int ACCOUNTS_CHILD_INDEX = 4;
	static final int NUM_V1_CHILDREN = 5;

	ServicesContext ctx;
	SystemExits systemExits = new JvmSystemExits();

	public ServicesState() { }

	public ServicesState(List<MerkleNode> children) {
		super(NUM_V1_CHILDREN);
		addDeserializedChildren(children, MERKLE_VERSION);
	}

	/* --- MerkleInternal --- */
	@Override
	public long getClassId() {
		return RUNTIME_CONSTRUCTABLE_ID;
	}

	@Override
	public int getVersion() {
		return MERKLE_VERSION;
	}

	@Override
	public int getMinimumChildCount(int version) {
		return NUM_V1_CHILDREN;
	}

	/* --- SwirldState --- */
	@Override
	public void init(Platform platform, AddressBook addressBook) {
		nodeId = platform.getSelfId();
		if (CONTEXTS.isInitialized(nodeId.getId())) {
			log.error("Services node {} re-initialized, indicating failure to load saved state. Exiting!", nodeId);
			systemExits.fail(1);
		}

		/* Note this overrides the address book from the saved state if it is present. */
		setChild(ADDRESS_BOOK_CHILD_INDEX, addressBook);

		if (getNumberOfChildren() < NUM_V1_CHILDREN) {
			var networkCtx = new MerkleNetworkContext(
					UNKNOWN_CONSENSUS_TIME,
					new SequenceNumber(HEDERA_START_SEQUENCE),
					new ExchangeRates());
			setChild(NETWORK_CTX_CHILD_INDEX, networkCtx);
			setChild(TOPICS_CHILD_INDEX, new FCMap<>(new MerkleEntityId.Provider(), new MerkleTopic.Provider()));
			setChild(STORAGE_CHILD_INDEX, new FCMap<>(new MerkleBlobMeta.Provider(), new MerkleOptionalBlob.Provider()));
			setChild(ACCOUNTS_CHILD_INDEX, new FCMap<>(new MerkleEntityId.Provider(), MerkleAccount.LEGACY_PROVIDER));
		}

		log.info("Initializing context of Services node {} with platform and address book...", nodeId);
		ctx = new ServicesContext(
				nodeId,
				platform,
				this,
				new StandardizedPropertySources(PropertiesLoader::getFileExistenceCheck));
		CONTEXTS.store(ctx);
		log.info("...done, context is set for Services node {}!", nodeId);
	}

	@Override
	public AddressBook getAddressBookCopy() {
		return addressBook().copy();
	}

	@Override
	public synchronized void handleTransaction(
			long submittingMember,
			boolean isConsensus,
			Instant creationTime,
			Instant consensusTime,
			com.swirlds.common.Transaction transaction,
			@Nullable Address toBeCreated
	) {
		if (isConsensus) {
			ctx.logic().incorporateConsensusTxn(transaction, consensusTime, submittingMember);
		}
	}

	@Override
	public void expandSignatures(Transaction platformTxn) {
		try {
			var accessor = new PlatformTxnAccessor(platformTxn);
			expandIn(accessor, ctx.lookupRetryingKeyOrder(), DEFAULT_SIG_BYTES);
		} catch (InvalidProtocolBufferException e) {
			log.warn("expandSignatures called with non-gRPC txn!", e);
		}
	}

	@Override
	public void noMoreTransactions() { }

	/* --- FastCopyable --- */
	@Override
	public synchronized FastCopyable copy() {
		return new ServicesState(List.of(
				addressBook().copy(),
				networkCtx().copy(),
				topics().copy(),
				storage().copy(),
				accounts().copy()));
	}

	@Override
	public synchronized void delete() {
		storage().delete();
	}

	@Override
	public boolean isImmutable() {
		return (nodeId == ID_WITH_INACTIVE_CONTEXT);
	}

	@Override
	@Deprecated
	public void copyFrom(SerializableDataInputStream in) throws IOException {
		log.info("Restoring context of Services node {} from legacy (Swirlds Platform v0.6.x) state...", nodeId);
		in.readLong();
		networkCtx().seqNo().deserialize(in);
		legacyTmpBookSupplier.get().copyFrom(in);
		accounts().copyFrom(in);
		storage().copyFrom(in);
		in.readBoolean();
		in.readLong();
		in.readLong();
		networkCtx().midnightRates().deserialize(in);
		if (in.readBoolean()) {
			networkCtx().setConsensusTimeOfLastHandledTxn(in.readInstant());
		}
		topics().copyFrom(in);
		log.info("...done, context is restored for Services node {}!", nodeId);
	}

	@Override
	@Deprecated
	public void copyFromExtra(SerializableDataInputStream in) throws IOException {
		in.readLong();
		legacyTmpBookSupplier.get().copyFromExtra(in);
		accounts().copyFromExtra(in);
		storage().copyFromExtra(in);
		topics().copyFromExtra(in);
	}

	@Override
	@Deprecated
	public void copyTo(SerializableDataOutputStream outputStream) {
		throw new UnsupportedOperationException();
	}

	@Override
	@Deprecated
	public void copyToExtra(SerializableDataOutputStream outputStream) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void copyFrom(SwirldState _state) {
		throw new UnsupportedOperationException();
	}

	/* --------------- */

	public AccountID getNodeAccountId() {
		var address = addressBook().getAddress(nodeId.getId());
		var memo = address.getMemo();
		return accountParsedFromString(memo);
	}

	public FCMap<MerkleEntityId, MerkleAccount> accounts() {
		return getChild(ACCOUNTS_CHILD_INDEX);
	}

	public FCMap<MerkleBlobMeta, MerkleOptionalBlob> storage() {
		return getChild(STORAGE_CHILD_INDEX);
	}

	public FCMap<MerkleEntityId, MerkleTopic> topics() {
		return getChild(TOPICS_CHILD_INDEX);
	}

	public MerkleNetworkContext networkCtx() {
		return getChild(NETWORK_CTX_CHILD_INDEX);
	}

	public AddressBook addressBook() {
		return getChild(ADDRESS_BOOK_CHILD_INDEX);
	}
}
