package com.distrosim.util

import scala.util.Random

// Seeded random utilities for reproducible experiments.
// Every random operation derives its seed from (experimentSeed + offset)
// so results are deterministic across runs.
object RandomUtil:

  def seededRandom(seed: Long, offset: Int = 0): Random =
    new Random(seed + offset)

  // Sample from a weighted distribution. Returns the key with
  // probability proportional to its weight.
  def weightedSample[A](distribution: Map[A, Double], rng: Random): A =
    val total = distribution.values.sum
    var cumulative = 0.0
    val roll = rng.nextDouble() * total
    val iter = distribution.iterator
    var result: A = distribution.head._1
    while iter.hasNext do
      val (key, weight) = iter.next()
      cumulative += weight
      if cumulative >= roll then
        result = key
        return result
    result

  // Normalize a map of weights so they sum to 1.0
  def normalize(weights: Map[String, Double]): Map[String, Double] =
    val total = weights.values.sum
    if total == 0.0 then weights.map((k, _) => k -> (1.0 / weights.size))
    else weights.map((k, v) => k -> v / total)

  // Generate a Zipf distribution over n items with given exponent
  def zipfDistribution(n: Int, exponent: Double = 1.0): Map[Int, Double] =
    val raw = (1 to n).map(k => k -> (1.0 / math.pow(k, exponent))).toMap
    val total = raw.values.sum
    raw.map((k, v) => k -> v / total)
