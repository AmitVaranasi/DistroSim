package com.distrosim

import com.distrosim.config.SimConfig
import com.distrosim.experiment.{ExperimentConfig, ExperimentRunner, ResultCollector}
import com.typesafe.scalalogging.LazyLogging

// DistroSim — Distributed Algorithms Simulator
//
// Entry point that runs all configured experiments sequentially.
// Each experiment generates a graph, builds an actor system,
// runs Chandy-Lamport and Dijkstra-Scholten algorithms, and
// collects results.
//
// Usage: sbt run
object Main extends LazyLogging:

  def main(args: Array[String]): Unit =
    println()
    println("╔══════════════════════════════════════════════════╗")
    println("║          DistroSim v0.1.0                       ║")
    println("║   Distributed Algorithms Simulator              ║")
    println("║   CS553 — Spring 2026                           ║")
    println("╚══════════════════════════════════════════════════╝")
    println()

    // Load default configuration
    val defaultConfig = SimConfig.load()
    logger.info("Configuration loaded")

    // Load experiment configurations
    val experiments = ExperimentConfig.loadAll(defaultConfig)
    logger.info(s"${experiments.size} experiments configured: ${experiments.map(_.name).mkString(", ")}")

    // Run each experiment
    val allResults = experiments.map { expConfig =>
      println(s"\n>>> Running experiment: ${expConfig.name}")
      val result = ExperimentRunner.run(expConfig)
      ResultCollector.printSummary(result)
      ResultCollector.writeToFile(result, s"results/${expConfig.name}.json")
      result
    }

    // Print comparison table
    if allResults.size > 1 then
      ResultCollector.printComparison(allResults)

    println("All experiments complete. Results written to results/ directory.")
