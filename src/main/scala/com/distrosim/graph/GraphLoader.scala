package com.distrosim.graph

import com.distrosim.config.SimConfig
import com.distrosim.util.RandomUtil
import scala.util.Random
import com.typesafe.scalalogging.LazyLogging

// Raw graph data before enrichment.
case class LoadedGraph(
  nodes: List[RawNode],
  edges: List[RawEdge],
  initNodeId: Int
)

case class RawNode(
  id: Int,
  children: Int,
  properties: Int,
  storedValue: Double
)

case class RawEdge(
  fromId: Int,
  toId: Int,
  actionType: Int,
  cost: Double
)

// Generates random graphs with configurable properties.
// Uses our own generator (compatible with NetGameSim's graph model)
// so students don't need to build/install NetGameSim separately.
object GraphLoader extends LazyLogging:

  def generate(config: SimConfig): LoadedGraph =
    val rng = RandomUtil.seededRandom(config.seed)
    val numNodes = config.graphStatesTotal
    val edgeProb = config.graphEdgeProbability

    logger.info(s"Generating graph with $numNodes nodes, edge probability $edgeProb")

    // Generate nodes
    val nodes = (0 until numNodes).map { id =>
      RawNode(
        id = id,
        children = rng.nextInt(config.graphMaxBranchingFactor) + 1,
        properties = rng.nextInt(config.graphMaxProperties) + 1,
        storedValue = rng.nextDouble() * 100.0
      )
    }.toList

    // Generate edges based on probability
    val edges = for
      from <- nodes
      to <- nodes
      if from.id != to.id
      if rng.nextDouble() < edgeProb
    yield RawEdge(
      fromId = from.id,
      toId = to.id,
      actionType = rng.nextInt(20),
      cost = rng.nextDouble() * 10.0
    )

    // Ensure connectivity: make sure every node is reachable from node 0
    val connectedEdges = ensureConnectivity(nodes, edges, rng)

    logger.info(s"Generated ${nodes.size} nodes and ${connectedEdges.size} edges")
    LoadedGraph(nodes, connectedEdges, initNodeId = 0)

  // Load a graph from a JSON file (NetGameSim format).
  // JSON format: line 1 = JSON array of nodes, line 2 = JSON array of edges
  def loadFromJson(path: String): LoadedGraph =
    val lines = scala.io.Source.fromFile(path).getLines().toList
    // Parse using simple string parsing for NetGameSim JSON
    // (In production, use Circe decoders matching NodeObject/Action)
    logger.info(s"Loading graph from JSON: $path")
    // Simplified: generate a placeholder until we integrate full Circe decoding
    throw new UnsupportedOperationException(
      s"JSON loading from $path not yet implemented. Use generate() instead."
    )

  // Ensure every node is reachable from node 0 by adding
  // edges to connect any disconnected components.
  private def ensureConnectivity(
    nodes: List[RawNode],
    edges: List[RawEdge],
    rng: Random
  ): List[RawEdge] =
    val nodeIds = nodes.map(_.id).toSet
    val adj = edges.groupBy(_.fromId).map((k, v) => k -> v.map(_.toId).toSet)

    // BFS from node 0 to find reachable nodes
    var reachable = Set(0)
    var frontier = List(0)
    while frontier.nonEmpty do
      val next = frontier.flatMap(n => adj.getOrElse(n, Set.empty)).filterNot(reachable.contains)
      reachable ++= next
      frontier = next

    // Add edges to connect unreachable nodes
    val unreachable = nodeIds -- reachable
    val extraEdges = unreachable.toList.map { nodeId =>
      val connectTo = reachable.toList(rng.nextInt(reachable.size))
      reachable += nodeId // now reachable
      RawEdge(connectTo, nodeId, rng.nextInt(20), rng.nextDouble() * 10.0)
    }

    edges ++ extraEdges
