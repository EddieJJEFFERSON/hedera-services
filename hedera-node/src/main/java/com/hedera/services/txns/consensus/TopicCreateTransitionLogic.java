package com.hedera.services.txns.consensus;

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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.domain.haccount.HederaAccount;
import com.hedera.services.state.merkle.Topic;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hedera.services.state.merkle.EntityId;
import com.hedera.services.legacy.core.jproto.HEntityId;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JTimestamp;
import com.swirlds.fcmap.FCMap;
import org.apache.commons.codec.DecoderException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Function;
import java.util.function.Predicate;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

/**
 * The syntax check pre-consensus validates the adminKey's structure as signature validation occurs before
 * doStateTransition().
 */
public class TopicCreateTransitionLogic implements TransitionLogic {
	private static final Logger log = LogManager.getLogger(TopicCreateTransitionLogic.class);

	private final Function<TransactionBody, ResponseCodeEnum> PRE_SIGNATURE_VALIDATION_SYNTAX_CHECK =
			this::validatePreSignatureValidation;

	private final FCMap<EntityId, HederaAccount> accounts;
	private final FCMap<EntityId, Topic> topics;
	private final EntityIdSource entityIdSource;
	private final OptionValidator validator;
	private final TransactionContext transactionContext;

	public TopicCreateTransitionLogic(FCMap<EntityId, HederaAccount> accounts, FCMap<EntityId, Topic> topics,
									  EntityIdSource entityIdSource, OptionValidator validator,
									  TransactionContext transactionContext) {
		this.accounts = accounts;
		this.topics = topics;
		this.entityIdSource = entityIdSource;
		this.validator = validator;
		this.transactionContext = transactionContext;
	}

	@Override
	public void doStateTransition() {
		var postConsensusValidationResult = validatePreStateTransition();
		if (OK != postConsensusValidationResult) {
			transactionContext.setStatus(postConsensusValidationResult);
			return;
		}

		var transactionBody = transactionContext.accessor().getTxn();
		var payerAccountId = transactionBody.getTransactionID().getAccountID();
		var op = transactionBody.getConsensusCreateTopic();

		try {
			// expirationTime (currently un-enforced) is consensus timestamp of create plus the specified required
			// autoRenewPeriod->seconds.
			var expirationTime = transactionContext.consensusTime().plusSeconds(op.getAutoRenewPeriod().getSeconds());

			var topic = new Topic(op.getMemo(),
					op.hasAdminKey() ? JKey.mapKey(op.getAdminKey()) : null,
					op.hasSubmitKey() ? JKey.mapKey(op.getSubmitKey()) : null,
					op.getAutoRenewPeriod().getSeconds(),
					op.hasAutoRenewAccount() ? HEntityId.convert(op.getAutoRenewAccount()) : null,
					new JTimestamp(expirationTime.getEpochSecond(), expirationTime.getNano()));

			var newEntityId = entityIdSource.newAccountId(payerAccountId);
			var newTopicId = TopicID.newBuilder()
					.setShardNum(newEntityId.getShardNum())
					.setRealmNum(newEntityId.getRealmNum())
					.setTopicNum(newEntityId.getAccountNum())
					.build();

			topics.put(EntityId.fromPojoTopicId(newTopicId), topic);
			transactionContext.setCreated(newTopicId);
			transactionContext.setStatus(SUCCESS);
		} catch (DecoderException e) {
			log.error("DecoderException should have been hit in validatePostConsensus().", e);
			// Should not hit this - validatePostConsensus() should fail first on hasGoodEncoding(key).
			transactionContext.setStatus(BAD_ENCODING);
		}
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasConsensusCreateTopic;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> syntaxCheck() {
		return PRE_SIGNATURE_VALIDATION_SYNTAX_CHECK;
	}

	/**
	 * Pre-consensus (and post-consensus-pre-doStateTransition) validation validates the encoding of the optional
	 * adminKey; this check occurs before signature validation which occurs before doStateTransition.
	 * @param transactionBody
	 * @return
	 */
	private ResponseCodeEnum validatePreSignatureValidation(TransactionBody transactionBody) {
		var op = transactionBody.getConsensusCreateTopic();

		if (op.hasAdminKey() && !validator.hasGoodEncoding(op.getAdminKey())) {
			return BAD_ENCODING;
		}

		return OK;
	}

	/**
	 * Validation of the post-consensus transaction just prior to state transition.
	 * @return
	 */
	private ResponseCodeEnum validatePreStateTransition() {
		var op = transactionContext.accessor().getTxn().getConsensusCreateTopic();
		ResponseCodeEnum validationResult;

		if (!validator.isValidEntityMemo(op.getMemo())) {
			validationResult = MEMO_TOO_LONG;
		} else if (op.hasSubmitKey() && !validator.hasGoodEncoding(op.getSubmitKey())) {
			validationResult = BAD_ENCODING;
		} else if (!op.hasAutoRenewPeriod()) {
			validationResult = INVALID_RENEWAL_PERIOD;
		} else if (!validator.isValidAutoRenewPeriod(op.getAutoRenewPeriod())) {
			validationResult = AUTORENEW_DURATION_NOT_IN_RANGE;
		} else if (op.hasAutoRenewAccount() &&
				(OK != validator.queryableAccountStatus(op.getAutoRenewAccount(), accounts))) {
			validationResult = INVALID_AUTORENEW_ACCOUNT;
		} else if (op.hasAutoRenewAccount() && !op.hasAdminKey()) {
			// If present, the autoRenewAccount's key must have signed transaction (see HederaSigningOrder).
			validationResult = AUTORENEW_ACCOUNT_NOT_ALLOWED;
		} else {
			validationResult = OK;
		}

		return validationResult;
	}
}
