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
package org.apache.flink.streaming.sampling.airlines;
import org.apache.commons.net.ntp.TimeStamp;
import org.apache.flink.api.common.functions.*;
import org.apache.flink.api.java.operators.translation.PlanFilterOperator;
import org.apache.flink.api.java.tuple.*;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;
import org.apache.flink.streaming.runtime.tasks.StreamingRuntimeContext;
import org.apache.flink.streaming.sampling.helpers.MetaAppender;
import org.apache.flink.streaming.sampling.helpers.SamplingUtils;
import org.apache.flink.streaming.sampling.helpers.SimpleUnwrapper;
import org.apache.flink.streaming.sampling.helpers.StreamTimestamp;
import org.apache.flink.streaming.sampling.samplers.BiasedReservoirSampler;
import org.apache.flink.streaming.sampling.samplers.ReservoirSampler;
import org.apache.flink.streaming.sampling.samplers.Sample;
import org.apache.flink.util.Collector;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Properties;


/**
 * Created by marthavk on 2015-05-21.
 *
 * Tuple2 has the fields: f0->Integer[] and f1->String[]
 *
 * Integer[] is an array of 11 values containing in the following order:
 * [year, month, day of month, day of week, CRS depart time, CRS arrival time
 * flight number, actual elapsed time, distance, diverted, delay]
 *
 * String[] is an array of 3 values containing in the following order:
 * [unique carrier, origin, destination]
 *
 */

public class AirlinesExample {

	public static void main(String[] args) throws Exception {

		String path = SamplingUtils.path;
		Properties initProps = SamplingUtils.readProperties(SamplingUtils.path + "distributionconfig.properties");
		int sample_size = Integer.parseInt(initProps.getProperty("sampleSize"));
		/*set execution environment*/
		final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
		env.setParallelism(1);
		DataStreamSource<String> source = env.readTextFile(path + "january_dataset.data")	;
		//DataStreamSource<String> source = env.readTextFile(path + "xs_dataset.data")	;
		/*
		 * Tuple8 fields:
		 * f0 day of january (Integer)
		 * f1 day of week (Integer)
		 * f2 crs depart time (Integer)
		 * f3 unique carrier (String)
		 * f4 origin (String)
		 * f5 destination (String)
		 * f6 delay (Integer)
		 * f7 1
		 */
		SingleOutputStreamOperator<Tuple8<Integer,Integer,Integer,String,String,String,Integer,Integer>,?> dataStream =
				source.map(new MapFunction<String, Tuple8<Integer, Integer, Integer, String, String, String, Integer, Integer>>() {
					@Override
					public Tuple8<Integer, Integer, Integer, String, String, String, Integer, Integer> map(String record) throws Exception {
						Tuple8 out = new Tuple8();
						String[] values = record.split(",");
						int[] integerFields = new int[]{1,2,3};
						int[] stringFields = new int[]{4, 5, 6};
						int[] integerFields2 = new int[]{7,8};

						int counter = 0;
						for(int i:integerFields) {
							int curr = Integer.parseInt(values[i]);
							out.setField(curr, counter);
							counter ++;
						}

						for (int i:stringFields) {
							out.setField(values[i],counter);
							counter++;
						}

						for (int i:integerFields2) {
							int curr = Integer.parseInt(values[i]);
							out.setField(curr,counter);
							counter++;
						}


						return out;

					}
				});

		//HEAVY HITTERS
		/*dataStream
				.filter(new FilterFunction<Tuple8<Integer, Integer, Integer, String, String, String, Integer, Integer>>() {
					@Override
					public boolean filter(Tuple8<Integer, Integer, Integer, String, String, String, Integer, Integer> value) throws Exception {
						return !value.f5.equals("ATL") && !value.f5.equals("ORD") && !value.f5.equals("DFW")
								&& !value.f5.equals("DEN") && !value.f5.equals("LAX") && !value.f5.equals("PHX")
								&& !value.f5.equals("LAS") && !value.f5.equals("IAH") && !value.f5.equals("DTW");
					}
				})
				.groupBy(5).sum(7).filter(new FilterFunction<Tuple8<Integer, Integer, Integer, String, String, String, Integer, Integer>>() {
			@Override
			public boolean filter(Tuple8<Integer, Integer, Integer, String, String, String, Integer, Integer> value) throws Exception {
				return value.f7>12090;
			}
		})
				.print();*/

		//RANGE QUERIES
		/*dataStream.filter(new FilterFunction<Tuple8<Integer, Integer, Integer, String, String, String, Integer, Integer>>() {
			@Override
			public boolean filter(Tuple8<Integer, Integer, Integer, String, String, String, Integer, Integer> value) throws Exception {
				return value.f0<8;
			}
		}).filter(new FilterFunction<Tuple8<Integer, Integer, Integer, String, String, String, Integer, Integer>>() {
			@Override
			public boolean filter(Tuple8<Integer, Integer, Integer, String, String, String, Integer, Integer> value) throws Exception {
				return value.f1==6||value.f1==7;
			}
		}).count().print();*/


		//SAMPLE
		dataStream.map(new ReservoirSampler<Tuple8<Integer, Integer, Integer, String, String, String, Integer, Integer>>(sample_size))
				.flatMap(new FlatMapFunction<Sample<Tuple8<Integer,Integer,Integer,String,String,String,Integer,Integer>>, String>() {
					StreamTimestamp s = new StreamTimestamp();
					@Override
					public void flatMap(Sample<Tuple8<Integer, Integer, Integer, String, String, String, Integer, Integer>> value, Collector<String> out) throws Exception {
						StreamTimestamp now = new StreamTimestamp();
						long time = now.getTimestamp()-s.getTimestamp();
						if (time > 1000) {
							//make string:
							String str = new String();
							ArrayList<Tuple8> allSamples = new ArrayList<Tuple8>();
							for (Tuple8 sample: allSamples) {
								String cTuple = sample.toString();
								/*cTuple = cTuple.substring(0)*/

							}
						}
					}
				});

		/*get js for execution plan*/
		System.err.println(env.getExecutionPlan());

		/*execute program*/
		env.execute();

	}


}