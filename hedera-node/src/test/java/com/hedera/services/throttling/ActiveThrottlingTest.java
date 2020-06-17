package com.hedera.services.throttling;

import com.hedera.services.context.properties.StandardizedPropertySources;
import com.hederahashgraph.api.proto.java.ConsensusCreateTopic;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ServicesConfigurationList;
import com.hederahashgraph.api.proto.java.Setting;
import com.swirlds.common.AddressBook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.hedera.services.throttling.bucket.BucketConfig.bucketsIn;
import static com.hedera.services.throttling.bucket.BucketConfig.namedIn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusCreateTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusDeleteTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusGetTopicInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusSubmitMessage;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusUpdateTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCallLocal;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractGetBytecode;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractGetRecords;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetAccountRecords;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileAppend;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileGetContents;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TransactionGetReceipt;
import static java.util.stream.Collectors.toMap;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

@RunWith(JUnitPlatform.class)
class ActiveThrottlingTest {
	int numNodes = 13;
	AddressBook book;
	FunctionalityThrottling subject;
	double nodeCreateTopicTps;
	double networkCreateTopicTps = 5.0;
	double nodeSlowTps;
	double networkSlowTps = 13.0;
	double nodeFastTps;
	double networkFastTps = 10000.0;

	int maxSubmittedPerSec = 15_000;
	long testLengthMs = 60_000l;
	Supplier<HederaFunctionality> supplier;
	EnumMap<HederaFunctionality, AtomicInteger> submitted;
	EnumMap<HederaFunctionality, AtomicInteger> throttled;

	EnumSet<HederaFunctionality> unthrottledFunctions = EnumSet.of(
			TransactionGetReceipt
	);
	EnumSet<HederaFunctionality> slowFunctions = EnumSet.of(
			ContractCreate, ContractCall, ContractUpdate, ContractDelete,
			ContractCallLocal, ContractGetInfo, ContractGetBytecode,
			FileCreate, FileUpdate, FileDelete, FileAppend,
			FileGetInfo, FileGetContents
	);
	EnumSet<HederaFunctionality> fastFunctions = EnumSet.of(
			CryptoCreate, CryptoTransfer, CryptoDelete, CryptoUpdate,
			CryptoGetInfo, CryptoGetAccountRecords,
			ContractGetRecords,
			ConsensusUpdateTopic, ConsensusDeleteTopic, ConsensusSubmitMessage,
			ConsensusGetTopicInfo
	);
	EnumSet<HederaFunctionality> createFunctions = EnumSet.of(
			ConsensusCreateTopic
	);

	@BeforeEach
	private void setup() {
		book = mock(AddressBook.class);
		given(book.getSize()).willReturn(numNodes);

		nodeCreateTopicTps = networkCreateTopicTps / numNodes;
		nodeSlowTps = networkSlowTps / numNodes;
		nodeFastTps = networkFastTps / numNodes;

		submitted = new EnumMap<>(HederaFunctionality.class);
		throttled = new EnumMap<>(HederaFunctionality.class);
		Stream.of(unthrottledFunctions, slowFunctions, fastFunctions, createFunctions)
				.flatMap(Set::stream)
				.forEach(functionality -> {
					submitted.put(functionality, new AtomicInteger(0));
					throttled.put(functionality, new AtomicInteger(0));
				});
	}

	@Test
	public void enforcesExpectedTpsTargetsWithSlowAndFast() throws IOException {
		// given:
		initFrom("src/test/resources/n13-throttle.properties");
		supplier = slowAndFast();

		// when:
		runSubmissions();

		// then:
		displayResults(List.of(slowFunctions, fastFunctions));
		System.out.println(String.format("--> Slow tps: %.3f", actualNetTps(slowFunctions)));
		System.out.println(String.format("--> Fast tps: %.3f", actualNetTps(fastFunctions)));
	}

	@Test
	public void enforcesExpectedTpsTargetsWithOnlyCreate() throws IOException {
		// given:
		initFrom("src/test/resources/n13-throttle.properties");
		supplier = createTopicOnly();

		// when:
		runSubmissions();

		// then:
		displayResults(List.of(createFunctions));
	}

	@Test
	public void enforcesExpectedTpsTargetsWithOnlySlow() throws IOException {
		// given:
		initFrom("src/test/resources/n13-throttle.properties");
		supplier = slowOnly();

		// when:
		runSubmissions();

		// then:
		displayResults(List.of(slowFunctions));
	}

	@Test
	public void enforcesExpectedTpsTargetsWithOnlyFast() throws IOException {
		// given:
		initFrom("src/test/resources/n13-throttle.properties");
		supplier = fastOnly();

		// when:
		runSubmissions();

		// then:
		displayResults(List.of(fastFunctions));
	}

	private void displayResults(List<EnumSet<HederaFunctionality>> sets) {
		HederaFunctionality[] functions = sets.stream().flatMap(Set::stream).toArray(HederaFunctionality[]::new);
		System.out.println("--> Detailed results:");
		for (HederaFunctionality function : functions) {
			System.out.println(String.format(
					"%s: Expected %.3f tps vs. Actual %.3f tps (%d submitted)",
					function,
					expectedTps(function) / functions.length,
					actualTps(function),
					submitted.get(function).get()));
		}
	}

	private double actualTps(HederaFunctionality function) {
		long secs = (testLengthMs / 1_000L);
		return 1.0 * (submitted.get(function).intValue() - throttled.get(function).intValue()) / secs;
	}

	private double actualNetTps(EnumSet<HederaFunctionality> functions) {
		int allSubmitted = functions.stream().mapToInt(function -> submitted.get(function).intValue()).sum();
		int allThrottled = functions.stream().mapToInt(function -> throttled.get(function).intValue()).sum();
		long secs = (testLengthMs / 1_000L);
		return 1.0 * (allSubmitted - allThrottled) / secs;
	}

	private double expectedTps(HederaFunctionality function) {
		if (slowFunctions.contains(function)) {
			return nodeSlowTps;
		} else if (createFunctions.contains(function)) {
			return nodeCreateTopicTps;
		} else if (fastFunctions.contains(function)) {
			return nodeFastTps;
		} else {
			return -1.0;
		}
	}

	private void runSubmissions() {
		long secBoundaryMs = System.currentTimeMillis();
		long endBoundaryMs = secBoundaryMs + testLengthMs;
		int submittedThisSec = 0;

		long now = System.currentTimeMillis();
		while (now < endBoundaryMs) {
			if (now > secBoundaryMs + 1_000L) {
				secBoundaryMs = now;
				submittedThisSec = 0;
			}
			if (submittedThisSec < maxSubmittedPerSec) {
				var function = supplier.get();
				submitted.get(function).getAndIncrement();
				var shouldThrottle = subject.shouldThrottle(function);
				if (shouldThrottle)	 {
					throttled.get(function).getAndIncrement();
				}
				submittedThisSec++;
			} else {
				try {
					Thread.sleep(1L);
				} catch (InterruptedException ignore) { }
			}
			now = System.currentTimeMillis();
		}
	}

	private Supplier<HederaFunctionality> slowOnly() {
		final HederaFunctionality[] choices = slowFunctions.toArray(new HederaFunctionality[0]);
		final SplittableRandom r = new SplittableRandom();
		return () -> choices[r.nextInt(choices.length)];
	}

	private Supplier<HederaFunctionality> fastOnly() {
		final HederaFunctionality[] choices = fastFunctions.toArray(new HederaFunctionality[0]);
		final SplittableRandom r = new SplittableRandom();
		return () -> choices[r.nextInt(choices.length)];
	}

	private Supplier<HederaFunctionality> createTopicOnly() {
		return () -> ConsensusCreateTopic;
	}

	private Supplier<HederaFunctionality> slowAndFast() {
		final HederaFunctionality[] choices = Stream.of(slowFunctions, fastFunctions)
				.flatMap(Set::stream)
				.toArray(HederaFunctionality[]::new);
		final SplittableRandom r = new SplittableRandom();
		return () -> choices[r.nextInt(choices.length)];
	}

	private void initFrom(String throttlePropsLoc) throws IOException {
		var jutilProps = new Properties();
		jutilProps.load(Files.newInputStream(Paths.get(throttlePropsLoc)));
		var configBuilder = ServicesConfigurationList.newBuilder();
		jutilProps.stringPropertyNames().forEach(name ->
				configBuilder.addNameValue(Setting.newBuilder().setName(name).setValue(jutilProps.getProperty(name))));

		var baseSource = new StandardizedPropertySources(ignore -> Boolean.TRUE);
		baseSource.updateThrottlePropsFrom(configBuilder.build());
		var props = baseSource.asResolvingSource();

		ThrottlingPropsBuilder.displayFn = System.out::println;
		var bucketThrottling = new BucketThrottling(
				book,
				props,
				p -> bucketsIn(p).stream().collect(toMap(Function.identity(), b -> namedIn(p, b))),
				ThrottlingPropsBuilder::withPrioritySource);
		bucketThrottling.rebuild();

		subject = bucketThrottling;
	}
}