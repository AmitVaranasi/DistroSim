package com.distrosim.actor

import akka.actor.{ActorRef, ActorSystem, Props}
import com.distrosim.config.SimConfig
import com.distrosim.graph.EnrichedGraph
import com.distrosim.algorithm.AlgorithmManager
import com.distrosim.traffic.{TimerNode, TrafficGenerator, InputNode}
import com.typesafe.scalalogging.LazyLogging

// The running actor graph: all node actors, traffic sources,
// and the algorithm manager, ready for simulation.
case class ActorGraph(
  system: ActorSystem,
  nodeActors: Map[Int, ActorRef],
  timerNodes: List[ActorRef],
  inputNodes: List[ActorRef],
  trafficGenerator: Option[ActorRef],
  algorithmManager: ActorRef
)

// Converts an EnrichedGraph into a live Akka actor system.
// Phase 1: Create node actors
// Phase 2: Register neighbors (edges become channels)
// Phase 3: Create traffic sources (timers, input, background)
// Phase 4: Create algorithm manager
object ActorGraphBuilder extends LazyLogging:

  def build(enrichedGraph: EnrichedGraph, config: SimConfig): ActorGraph =
    val system = ActorSystem("DistroSim")
    logger.info("Building actor graph...")

    // Phase 1: Create one actor per node
    val nodeActors: Map[Int, ActorRef] = enrichedGraph.nodes.map { case (id, eNode) =>
      val edgesForNode = enrichedGraph.outgoingEdges(id)
      val ref = system.actorOf(
        NodeActor.props(id, eNode, edgesForNode, config.seed),
        name = s"node-$id"
      )
      id -> ref
    }
    logger.info(s"Created ${nodeActors.size} node actors")

    // Phase 2: Register neighbors (each edge = communication channel)
    // Both directions are always registered so that:
    // - The sender knows the receiver (for sending messages)
    // - The receiver knows the sender (for CL channel state recording)
    enrichedGraph.edges.foreach { case ((fromId, toId), _) =>
      for
        fromRef <- nodeActors.get(fromId)
        toRef   <- nodeActors.get(toId)
      do
        fromRef ! RegisterNeighbor(toId, toRef)
        toRef ! RegisterNeighbor(fromId, fromRef)
    }
    logger.info("Registered all neighbor relationships")

    // Phase 3: Create algorithm manager
    val algorithmManager = system.actorOf(
      Props(new AlgorithmManager(nodeActors, enrichedGraph, config)),
      name = "algorithm-manager"
    )

    // Tell all nodes about the algorithm manager
    nodeActors.values.foreach(_ ! SetAlgorithmManager(algorithmManager))

    // Phase 4: Create timer nodes
    val timerNodes = if config.timerEnabled then
      val timerTargets = nodeActors.values.toList
      (0 until config.timerCount).map { i =>
        system.actorOf(
          Props(new TimerNode(timerTargets, config.timerInterval, config.timerJitter, config.seed + 5000 + i)),
          name = s"timer-$i"
        )
      }.toList
    else List.empty

    // Phase 5: Create background traffic generator
    val trafficGen = if config.trafficEnabled then
      Some(system.actorOf(
        Props(new TrafficGenerator(nodeActors, enrichedGraph, config.trafficRate, config.seed + 9000)),
        name = "traffic-generator"
      ))
    else None

    // Phase 6: Create input nodes
    val inputNodes = if config.inputEnabled then
      List(system.actorOf(
        Props(new InputNode(nodeActors, config.inputSource, config.inputFilePath, config.seed + 7000)),
        name = "input-node"
      ))
    else List.empty

    logger.info(s"Actor graph built: ${nodeActors.size} nodes, ${timerNodes.size} timers, traffic=${trafficGen.isDefined}")

    ActorGraph(system, nodeActors, timerNodes, inputNodes, trafficGen, algorithmManager)
