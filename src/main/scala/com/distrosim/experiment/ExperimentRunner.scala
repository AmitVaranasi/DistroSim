package com.distrosim.experiment

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.util.Timeout
import com.distrosim.actor.{ActorGraphBuilder, AlgorithmComplete, RunAlgorithm, StartSimulation, StopSimulation}
import com.distrosim.config.SimConfig
import com.distrosim.graph.{GraphEnricher, GraphLoader}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{Await, Promise}
import scala.concurrent.duration.*
import scala.util.{Failure, Success, Try}
import com.distrosim.algorithm.AlgorithmResult

// Orchestrates one complete experiment:
// 1. Generate/load graph
// 2. Enrich graph
// 3. Build actor system
// 4. Start simulation + traffic
// 5. Run algorithms after warmup
// 6. Collect results
// 7. Shut down
object ExperimentRunner extends LazyLogging:

  def run(expConfig: ExperimentConfig): ExperimentResult =
    val config = expConfig.simConfig
    val startTime = System.currentTimeMillis()

    logger.info(s"=== Starting experiment: ${expConfig.name} ===")

    // Step 1: Generate graph
    logger.info("Step 1: Generating graph...")
    val loaded = if config.graphLoadFromFile.nonEmpty then
      GraphLoader.loadFromJson(config.graphLoadFromFile)
    else
      GraphLoader.generate(config)

    // Step 2: Enrich graph
    logger.info("Step 2: Enriching graph...")
    val enriched = GraphEnricher.enrich(loaded, config)
    val graphStats = GraphStats.from(enriched)

    // Step 3: Build actor system
    logger.info("Step 3: Building actor system...")
    val actorGraph = ActorGraphBuilder.build(enriched, config)

    // Step 4: Start simulation
    logger.info("Step 4: Starting simulation...")
    actorGraph.nodeActors.values.foreach(_ ! StartSimulation)

    // Step 5: Warmup period (let traffic flow)
    logger.info(s"Step 5: Warmup period (${config.warmupPeriod})...")
    Thread.sleep(config.warmupPeriod.toMillis)

    // Step 6: Run algorithms
    logger.info("Step 6: Running algorithms...")
    implicit val timeout: Timeout = Timeout(60.seconds)
    val algorithmResults = expConfig.algorithmsToRun.flatMap { algName =>
      // For Dijkstra-Scholten, stop traffic first so termination can be detected
      if algName == "dijkstra-scholten" then
        logger.info("  Stopping traffic for termination detection...")
        actorGraph.timerNodes.foreach(_ ! StopSimulation)
        actorGraph.trafficGenerator.foreach(_ ! StopSimulation)
        // Fixed drain time — traffic is lightweight, just need scheduled messages to complete
        val drainMs = 5000L
        logger.info(s"  Draining in-flight messages (${drainMs}ms)...")
        Thread.sleep(drainMs)

      logger.info(s"  Running $algName...")
      Try {
        val future = actorGraph.algorithmManager ? RunAlgorithm(algName)
        Await.result(future, 60.seconds) match
          case AlgorithmComplete(name, result: AlgorithmResult) =>
            logger.info(s"  $algName completed: ${result.summary}")
            Some(result)
          case other =>
            logger.warn(s"  Unexpected result from $algName: $other")
            None
      } match
        case Success(result) => result
        case Failure(ex) =>
          logger.error(s"  $algName failed: ${ex.getMessage}")
          None
    }

    // Step 7: Stop simulation
    logger.info("Step 7: Stopping simulation...")
    actorGraph.nodeActors.values.foreach(_ ! StopSimulation)
    actorGraph.timerNodes.foreach(_ ! StopSimulation)
    actorGraph.trafficGenerator.foreach(_ ! StopSimulation)
    actorGraph.inputNodes.foreach(_ ! StopSimulation)

    // Give actors time to process stop messages
    Thread.sleep(1000)

    // Terminate actor system
    actorGraph.system.terminate()
    Try(Await.result(actorGraph.system.whenTerminated, 10.seconds))

    val totalDuration = System.currentTimeMillis() - startTime
    logger.info(s"=== Experiment ${expConfig.name} complete in ${totalDuration}ms ===")

    ExperimentResult(
      name = expConfig.name,
      graphStats = graphStats,
      algorithmResults = algorithmResults,
      durationMs = totalDuration
    )
