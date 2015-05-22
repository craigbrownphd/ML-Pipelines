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
package org.apache.flink.streaming.incrementalML.classification.Metrics

/** Represents the local results for the best and the second best attributes that could be used
  * to split upon
  *
  */
case class EvaluationMetric(
//  bestValue: (Int, (Double, List[Double])),
//  secondBestValue: (Int, (Double, List[Double])),
  proposedValues: List[(Int, (Double, List[Double]))],
  leafId: Int,
  signalIdNumber: Int)
  extends Metrics
  with Serializable {

  override def toString: String = {
    s"Proposed attributes to split: $proposedValues\n"
  }
}
