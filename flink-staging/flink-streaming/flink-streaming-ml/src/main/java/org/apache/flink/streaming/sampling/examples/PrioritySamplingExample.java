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

import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.sampling.evaluators.DistributionComparator;

import org.apache.flink.streaming.sampling.generators.DoubleDataGenerator;
import org.apache.flink.streaming.sampling.generators.GaussianDistribution;
import org.apache.flink.streaming.sampling.helpers.MetaAppender;
import org.apache.flink.streaming.sampling.helpers.SampleExtractor;
import org.apache.flink.streaming.sampling.helpers.SamplingUtils;
import org.apache.flink.streaming.sampling.samplers.PrioritySampler;
import org.apache.flink.streaming.sampling.samplers.Sample;
import org.apache.flink.streaming.sampling.sources.NormalStreamSource;

import java.util.Properties;

/**
 * Created by marthavk on 2015-05-06.
 */
public class PrioritySamplingExample {
	public static long MAX_COUNT;  // max count of generated numbers
	public static long TIME_WINDOW_SIZE; //In milliseconds
	public static int SAMPLE_SIZE;
	public static Properties initProps = new Properties();

	// *************************************************************************
	// PROGRAM
	// *************************************************************************
	public static void main(String[] args) throws Exception {

		/*read properties file and set static variables*/
		initProps = SamplingUtils.readProperties(SamplingUtils.path + "distributionconfig.properties");
		MAX_COUNT = Long.parseLong(initProps.getProperty("maxCount"));
		TIME_WINDOW_SIZE = Long.parseLong(initProps.getProperty("timeWindowSize"));
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
	 *
	 * @param env
	 * @param initProps
	 */
	public static void evaluateSampling(StreamExecutionEnvironment env, final Properties initProps) {

		int sampleSize = SAMPLE_SIZE;

		/*create stream of distributions as source (also number generators) and shuffle*/
		DataStreamSource<GaussianDistribution> source = createSource(env);
		SingleOutputStreamOperator<GaussianDistribution, ?> shuffledSrc = source.shuffle();

		/*generate random number from distribution*/
		SingleOutputStreamOperator<Double, ?> generator = shuffledSrc.map(new DoubleDataGenerator<GaussianDistribution>());

		SingleOutputStreamOperator<Sample<Double>, ?> sample = generator.map(new MetaAppender<Double>())
				/*sample the stream*/
				.map(new PrioritySampler<Double>(sampleSize, TIME_WINDOW_SIZE))

				/*extract Double sampled values (unwrap from Tuple3)*/
				.map(new SampleExtractor<Double>());

		/*connect sampled stream to source*/
		sample.connect(shuffledSrc)

				/*evaluate sample: compare current distribution parameters with sampled distribution parameters*/
				//.flatMap(new DistanceEvaluator())
				.flatMap(new DistributionComparator())

				/*sink*/
						//.print();
				.writeAsText(SamplingUtils.path + "priority");
	}


	/**
	 * Creates a DataStreamSource of GaussianDistribution items out of the params at input.
	 *
	 * @param env the StreamExecutionEnvironment.
	 * @return the DataStreamSource
	 */
	public static DataStreamSource<GaussianDistribution> createSource(StreamExecutionEnvironment env) {
		return env.addSource(new NormalStreamSource());
	}
}