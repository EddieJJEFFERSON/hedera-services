package com.hedera.services.txns.diligence;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.legacy.crypto.SignatureStatus;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class DiligenceScreen {
	private final OptionValidator validator;
	private final Supplier<Boolean> txnScopedPayerSigCheck;
	private final Supplier<SignatureStatus> txnScopedPayerSigStatus;
	private final Supplier<DuplicateClassification> txnScopedDuplicity;

	public DiligenceScreen(
			OptionValidator validator,
			Supplier<Boolean> txnScopedPayerSigCheck,
			Supplier<SignatureStatus> txnScopedPayerSigStatus,
			Supplier<DuplicateClassification> txnScopedDuplicity
	) {
		this.validator = validator;
		this.txnScopedDuplicity = txnScopedDuplicity;
		this.txnScopedPayerSigCheck = txnScopedPayerSigCheck;
		this.txnScopedPayerSigStatus = txnScopedPayerSigStatus;
	}

	public void applyIn(TransactionContext txnCtx) {
		throw new AssertionError("Not implemented!");
	}
}
