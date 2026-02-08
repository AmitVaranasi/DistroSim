package com.distrosim.config

import com.typesafe.config.{Config, ConfigFactory}
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

// Typed wrapper around application.conf.
// Provides safe access to all configuration values with defaults.
case class SimConfig(underlying: Config):

  // --- Graph generation ---
  val graphStatesTotal: Int = underlying.getInt("distrosim.graph.states-total")
  val graphEdgeProbability: Double = underlying.getDouble("distrosim.graph.edge-probability")
  val graphMaxBranchingFactor: Int = underlying.getInt("distrosim.graph.max-branching-factor")
  val graphMaxDepth: Int = underlying.getInt("distrosim.graph.max-depth")
  val graphMaxProperties: Int = underlying.getInt("distrosim.graph.max-properties")
  val graphUndirected: Boolean = underlying.getBoolean("distrosim.graph.undirected")
  val graphLoadFromFile: String = underlying.getString("distrosim.graph.load-from-file")

  // --- Enrichment ---
  val edgeTypeProbability: Double = underlying.getDouble("distrosim.enrichment.edge-type-probability")
  val minTransmissionDelayMs: Long = underlying.getLong("distrosim.enrichment.min-transmission-delay-ms")
  val maxTransmissionDelayMs: Long = underlying.getLong("distrosim.enrichment.max-transmission-delay-ms")
  val minProcessingDelayMs: Long = underlying.getLong("distrosim.enrichment.min-processing-delay-ms")
  val maxProcessingDelayMs: Long = underlying.getLong("distrosim.enrichment.max-processing-delay-ms")
  val edgeReliability: Double = underlying.getDouble("distrosim.enrichment.reliability")

  // --- Simulation ---
  val seed: Long = underlying.getLong("distrosim.simulation.seed")
  val simulationDuration: FiniteDuration = underlying.getDuration("distrosim.simulation.duration").toMillis.millis
  val warmupPeriod: FiniteDuration = underlying.getDuration("distrosim.simulation.warmup-period").toMillis.millis

  // --- Timer ---
  val timerEnabled: Boolean = underlying.getBoolean("distrosim.timer.enabled")
  val timerCount: Int = underlying.getInt("distrosim.timer.count")
  val timerInterval: FiniteDuration = underlying.getDuration("distrosim.timer.interval").toMillis.millis
  val timerJitter: FiniteDuration = underlying.getDuration("distrosim.timer.jitter").toMillis.millis

  // --- Input ---
  val inputEnabled: Boolean = underlying.getBoolean("distrosim.input.enabled")
  val inputSource: String = underlying.getString("distrosim.input.source")
  val inputFilePath: String = underlying.getString("distrosim.input.file-path")

  // --- Traffic ---
  val trafficEnabled: Boolean = underlying.getBoolean("distrosim.traffic.enabled")
  val trafficRate: FiniteDuration = underlying.getDuration("distrosim.traffic.rate").toMillis.millis

  // --- Algorithms ---
  val clInitiatorNode: Int = underlying.getInt("distrosim.algorithms.chandy-lamport.initiator-node")
  val clTriggerDelay: FiniteDuration = underlying.getDuration("distrosim.algorithms.chandy-lamport.trigger-delay").toMillis.millis
  val dsInitiatorNode: Int = underlying.getInt("distrosim.algorithms.dijkstra-scholten.initiator-node")
  val dsTriggerDelay: FiniteDuration = underlying.getDuration("distrosim.algorithms.dijkstra-scholten.trigger-delay").toMillis.millis

  // --- Experiments ---
  val activeExperiments: List[String] = underlying.getStringList("distrosim.experiments.active").asScala.toList

object SimConfig:
  def load(): SimConfig = SimConfig(ConfigFactory.load())

  def load(configName: String): SimConfig =
    val experimentConf = ConfigFactory.load(configName)
    val fallback = ConfigFactory.load()
    SimConfig(experimentConf.withFallback(fallback))
