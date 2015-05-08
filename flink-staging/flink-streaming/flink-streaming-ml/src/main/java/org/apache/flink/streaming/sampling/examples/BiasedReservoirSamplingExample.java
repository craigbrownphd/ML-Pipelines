/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.flink.streaming.sampling.examples;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.sampling.evaluators.DistributionComparator;
import org.apache.flink.streaming.sampling.generators.DataGenerator;
import org.apache.flink.streaming.sampling.generators.GaussianStreamGenerator;
import org.apache.flink.streaming.sampling.helpers.SamplingUtils;
import org.apache.flink.streaming.sampling.helpers.SimpleUnwrapper;
import org.apache.flink.streaming.sampling.helpers.StreamTimestamp;
import org.apache.flink.streaming.sampling.samplers.BiasedReservoirSampler;

import java.util.Properties;

/**
 * Created by marthavk on 2015-05-06.
 */
public class BiasedReservoirSamplingExample {
	public static long MAX_COUNT;  // max count of generated numbers
	public static int SAMPLE_SIZE;
	public static Properties initProps = new Properties();

	// *************************************************************************
	// PROGRAM
	// *************************************************************************
	public static void main(String args[]) throws Exception {

		/*read properties file and set static variables*/
		initProps = SamplingUtils.readProperties(SamplingUtils.path + "distributionconfig.properties");
		MAX_COUNT = Long.parseLong(initProps.getProperty("maxCount"));
		SAMPLE_SIZE = Integer.parseInt(initProps.getProperty("sampleSize"));

		/*set execution environment*/
		final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

		/*evaluate sampling method, run main algorithm*/
		evaluateSampling(env, initProps);

		/*get js for execution plan*/
		System.err.println(env.getExecutionPlan());

		/*execute program*/
		env.execute();

	}

	/**
	 * Evaluates the sampling method. Compares final sample distribution parameters
	 * with source.
	 * @param env
	 * @param initProps
	 */
	public static void evaluateSampling(StreamExecutionEnvironment env, final Properties initProps) {

		int sampleSize = SAMPLE_SIZE;

		/*create source*/
		DataStreamSource<NormalDistribution> source = createSource(env, initProps);

		/*generate random numbers according to Distribution parameters*/
		SingleOutputStreamOperator<NormalDistribution,?> operator = source.shuffle()

				/*generate double value from GaussianDistribution and wrap around
				Tuple3<Double, Timestamp, Long> */
				.map(new MapFunction<NormalDistribution, NormalDistribution>() {
					@Override
					public NormalDistribution map(NormalDistribution value) throws Exception {
						return value;
					}
				});

		operator.map(new DataGenerator())

				/*sample the stream*/
				.map(new BiasedReservoirSampler<Tuple3<Double, StreamTimestamp, Long>>(sampleSize))

				/*extract Double sampled values (unwrap from Tuple3)*/
				.map(new SimpleUnwrapper<Double>()) //use that for Reservoir, Biased Reservoir, FIFO Samplers

				/*connect sampled stream to source*/
				.connect(operator)

				/*evaluate sample: compare current distribution parameters with sampled distribution parameters*/
				//.flatMap(new DistanceEvaluator())
				.flatMap(new DistributionComparator())

				/*sink*/
				.writeAsText(SamplingUtils.path + "evaluation");
	}


	/**
	 * Creates a DataStreamSource of GaussianDistribution items out of the params at input.
	 *
	 * @param env the StreamExecutionEnvironment.
	 * @return the DataStreamSource
	 */
	public static DataStreamSource<NormalDistribution> createSource(StreamExecutionEnvironment env, final Properties props) {
		return env.addSource(new GaussianStreamGenerator(props));
	}

}