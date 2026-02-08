package com.distrosim.graph

import com.distrosim.config.SimConfig
import com.distrosim.util.RandomUtil
import com.typesafe.scalalogging.LazyLogging

// Transforms a raw LoadedGraph into an EnrichedGraph by:
// 1. Assigning message type labels to each edge
// 2. Assigning message production probability distributions to each node
// 3. Assigning transmission delays and reliability to edges
// 4. Assigning processing delays to nodes
object GraphEnricher extends LazyLogging:

  def enrich(loaded: LoadedGraph, config: SimConfig): EnrichedGraph =
    val rng = RandomUtil.seededRandom(config.seed, offset = 1000)
    logger.info(s"Enriching graph: ${loaded.nodes.size} nodes, ${loaded.edges.size} edges")

    // Enrich nodes with message production distributions
    val enrichedNodes = loaded.nodes.map { node =>
      val dist = MessageType.all.map { mt =>
        mt -> rng.nextDouble()
      }.toMap
      // Normalize so probabilities sum to 1.0
      val total = dist.values.sum
      val normalized = dist.map((k, v) => k -> v / total)

      val processingDelay = config.minProcessingDelayMs +
        rng.nextLong(config.maxProcessingDelayMs - config.minProcessingDelayMs + 1)

      node.id -> EnrichedNode(
        id = node.id,
        children = node.children,
        properties = node.properties,
        messageProductionDist = normalized,
        processingDelayMs = processingDelay
      )
    }.toMap

    // Enrich edges with allowed message types and delays
    val enrichedEdges = loaded.edges.map { edge =>
      val allowed = MessageType.all.filter(_ => rng.nextDouble() < config.edgeTypeProbability)
      // Ensure at least one message type is allowed per edge
      val finalAllowed = if allowed.isEmpty then Set(MessageType.DataTransfer) else allowed

      val transmissionDelay = config.minTransmissionDelayMs +
        rng.nextLong(config.maxTransmissionDelayMs - config.minTransmissionDelayMs + 1)

      (edge.fromId, edge.toId) -> EnrichedEdge(
        fromId = edge.fromId,
        toId = edge.toId,
        actionType = edge.actionType,
        cost = edge.cost,
        allowedMessageTypes = finalAllowed,
        transmissionDelayMs = transmissionDelay,
        reliability = config.edgeReliability
      )
    }.toMap

    // Build adjacency map
    val adjacency = enrichedEdges.keys.groupBy(_._1).map { (fromId, pairs) =>
      fromId -> pairs.map(_._2).toSet
    }

    val graph = EnrichedGraph(
      nodes = enrichedNodes,
      edges = enrichedEdges,
      adjacency = adjacency,
      initNodeId = loaded.initNodeId
    )

    logger.info(s"Enrichment complete. ${enrichedNodes.size} nodes, ${enrichedEdges.size} edges")
    logger.info(s"Average edges per node: ${enrichedEdges.size.toDouble / enrichedNodes.size}")
    graph
