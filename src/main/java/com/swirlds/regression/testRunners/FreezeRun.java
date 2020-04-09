/*
 * (c) 2016-2019 Swirlds, Inc.
 *
 * This software is the confidential and proprietary information of
 * Swirlds, Inc. ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Swirlds.
 *
 * SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. SWIRLDS SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 */

package com.swirlds.regression.testRunners;

import com.swirlds.regression.Experiment;
import com.swirlds.regression.jsonConfigs.TestConfig;

import java.time.Duration;

import static com.swirlds.regression.RegressionUtilities.MILLIS;

public class FreezeRun implements TestRun {
	@Override
	public void runTest(TestConfig testConfig, Experiment experiment) {
		final int iterations = testConfig.getFreezeConfig().getFreezeIterations();
		final int freezeTiming = testConfig.getFreezeConfig().getFreezeTiming();
		final int experimentStartDelay = testConfig.getExperimentConfig().getExperimentStartDelay();

		for (int i = 0; i < iterations; i++) {

			// if this iteration fails, stop the test
			if (!runSingleFreeze(experiment, freezeTiming, experimentStartDelay, i, true)) {
				return;
			}


			log.info(MARKER, "{} dynamic freeze test completed.", (i + 1));

			// wait a bit during freeze
			experiment.sleepThroughExperiment(testConfig.getExperimentConfig().getFreezeWaitMillis());
		}

		log.info(MARKER, "Last dynamic freeze test finished. Starting for final time.");

		// set the non-freeze config for the last run
		// to generate new config files, we should update app in ConfigBuilder
		experiment.setConfigApp(
				testConfig.getFreezeConfig().getPostFreezeApp()
		);
		// upload the configs
		experiment.sendConfigToNodes();


		// wait a bit for sending new config to nodes
		// if the new config is not sent successfully before swirlds.jar is started
		// total freeze frequency would be FreezeIteration + 1
		// use FREEZE_WAIT_MILLIS * 2 to make the waiting period be a little longer
		experiment.sleepThroughExperiment(testConfig.getExperimentConfig().getFreezeWaitMillis() * 2);

		// start all processes
		experiment.startAllSwirlds();

		// sleep through the rest of the test
		long testDuration = testConfig.getDuration() * MILLIS;
		log.info(MARKER, "kicking off test, duration: {}", testDuration);
		experiment.sleepThroughExperiment(testDuration);
	}

	/**
	 * run a single freeze run
	 *
	 * @param experiment
	 * @param waitTiming
	 * 		for FreezeTest, it is FreezeConfig.freezeTiming;
	 * 		for RestartTest, it is RestartConfig.restartTiming;
	 * @param iteration
	 * 		for FreezeTest, it is current iteration number;
	 * 		for RestartTest, it should always be 0;
	 * @param isFreezeTest
	 * 		if it is true, this is a FreezeTest (Dynamic Restart Test);
	 * 		if it is false, this is a RestartTest
	 * @return return true if succeed, else return false
	 */
	static boolean runSingleFreeze(final Experiment experiment,
			final int waitTiming, final int experimentStartDelay, final int iteration, final boolean isFreezeTest) {
		// start all processes
		experiment.startAllSwirlds();

		Duration sleep =
				Duration.ofMinutes(experimentStartDelay + waitTiming);
		experiment.sleepThroughExperiment(sleep.toMillis());

		// check if all nodes has entered Maintenance status at ith iteration
		// if not, log an error and stop the test
		if (!experiment.checkAllNodesFreeze(iteration, isFreezeTest)) {
			if (isFreezeTest) {
				log.error(EXCEPTION, "Dynamic freeze test failed at {}th iteration. " +
								"Not all nodes entered Maintenance status after waiting for {} mins",
						iteration, waitTiming);
			} else {
				log.error(EXCEPTION, "Restart test failed. " +
								"Not all nodes entered Maintenance status after waiting for {} mins",
						waitTiming);
			}

			return false;
		}

		// check if all nodes finish saving expectedMap if they already started
		if (!experiment.checkAllNodesSavedExpected(iteration)) {
			// wait for a while
			experiment.sleepThroughExperiment(
					Duration.ofMinutes(SAVE_EXPECTED_WAIT_MINS).toMillis());
			// if any node hasn't finished yet, log an error and stop the test
			if (!experiment.checkAllNodesSavedExpected(iteration)) {
				if (isFreezeTest) {
					log.error(EXCEPTION, "Dynamic freeze test failed at {}th iteration. " +
									"Not all nodes have finished saving ExpectedMap after {} mins",
							iteration, waitTiming +
									SAVE_EXPECTED_WAIT_MINS);
				} else {
					log.error(EXCEPTION, "Restart test failed. " +
							"Not all nodes have finished saving ExpectedMap after {} mins", waitTiming +
							SAVE_EXPECTED_WAIT_MINS);
				}
				return false;
			}
		}

		// kill the process during the freeze
		experiment.stopAllSwirlds();
		return true;
	}
}
