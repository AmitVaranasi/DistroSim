package com.distrosim.algorithm

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.distrosim.actor.*
import com.distrosim.config.SimConfig
import com.distrosim.graph.EnrichedGraph
import com.distrosim.algorithm.snapshot.ChandyLamportManager
import com.distrosim.algorithm.termination.DijkstraScholtenManager

import scala.concurrent.duration.*

// Central coordinator for distributed algorithm execution.
// Receives commands to run algorithms, delegates to the appropriate
// manager, and collects results.
class AlgorithmManager(
  nodeActors: Map[Int, ActorRef],
  enrichedGraph: EnrichedGraph,
  config: SimConfig
) extends Actor with ActorLogging:

  private var snapshotManager: Option[ChandyLamportManager] = None
  private var terminationManager: Option[DijkstraScholtenManager] = None
  // Per-algorithm callback refs (so they don't overwrite each other)
  private var snapshotCallback: Option[ActorRef] = None
  private var terminationCallback: Option[ActorRef] = None

  def receive: Receive =
    // Run a named algorithm
    case RunAlgorithm(algorithmName) =>
      algorithmName match
        case "chandy-lamport" =>
          snapshotCallback = Some(sender())
          val manager = new ChandyLamportManager(nodeActors, config.clInitiatorNode)
          snapshotManager = Some(manager)
          manager.start()
          log.info("Chandy-Lamport snapshot initiated")

        case "dijkstra-scholten" =>
          terminationCallback = Some(sender())
          val manager = new DijkstraScholtenManager(nodeActors, config.dsInitiatorNode)
          terminationManager = Some(manager)
          manager.start()
          // Set a timeout for termination detection
          import context.dispatcher
          context.system.scheduler.scheduleOnce(45.seconds, self, "ds-timeout")
          log.info("Dijkstra-Scholten termination detection initiated")

        case other =>
          log.warning(s"Unknown algorithm: $other")

    // Chandy-Lamport: collect recorded states from nodes
    case rs: RecordedState =>
      snapshotManager.foreach { manager =>
        manager.onStateReceived(rs)
        if manager.isComplete then
          manager.results.foreach { result =>
            log.info(result.summary)
            snapshotCallback.foreach(_ ! AlgorithmComplete("chandy-lamport", result))
            snapshotCallback = None
          }
      }

    // Dijkstra-Scholten: termination detected
    case td: TerminationDetected =>
      terminationManager.foreach { manager =>
        manager.onTerminationDetected(td)
        if manager.isComplete then
          manager.results.foreach { result =>
            log.info(result.summary)
            terminationCallback.foreach(_ ! AlgorithmComplete("dijkstra-scholten", result))
            terminationCallback = None
          }
      }

    // DS timeout
    case "ds-timeout" =>
      terminationManager.foreach { manager =>
        if !manager.isComplete then
          manager.onTimeout()
          manager.results.foreach { result =>
            log.info(result.summary)
            terminationCallback.foreach(_ ! AlgorithmComplete("dijkstra-scholten", result))
            terminationCallback = None
          }
      }

    case GetState =>
      val status = Map(
        "snapshot-complete" -> snapshotManager.exists(_.isComplete).toString,
        "termination-complete" -> terminationManager.exists(_.isComplete).toString
      )
      sender() ! status
