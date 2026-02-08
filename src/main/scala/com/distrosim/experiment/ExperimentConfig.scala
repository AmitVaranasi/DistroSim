package com.distrosim.experiment

import com.distrosim.config.SimConfig
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration.*

// Per-experiment configuration. Each experiment can override
// the default graph, traffic, and algorithm settings.
case class ExperimentConfig(
  name: String,
  simConfig: SimConfig,
  algorithmsToRun: List[String] = List("chandy-lamport", "dijkstra-scholten")
)

object ExperimentConfig:
  // Load experiment configs from the active experiments list
  def loadAll(defaultConfig: SimConfig): List[ExperimentConfig] =
    defaultConfig.activeExperiments.map { expName =>
      val expConf = try
        SimConfig.load(s"experiments/$expName")
      catch
        case _: Exception =>
          // Fall back to default config if experiment-specific file not found
          defaultConfig
      ExperimentConfig(
        name = expName,
        simConfig = expConf
      )
    }
