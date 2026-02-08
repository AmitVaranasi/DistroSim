package com.distrosim.experiment

import com.distrosim.algorithm.{AlgorithmResult, SnapshotResult, TerminationResult}
import com.distrosim.graph.EnrichedGraph
import com.typesafe.scalalogging.LazyLogging

import java.io.{File, PrintWriter}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Collects experiment results and outputs them as formatted text and JSON.
case class ExperimentResult(
  name: String,
  graphStats: GraphStats,
  algorithmResults: List[AlgorithmResult],
  durationMs: Long
)

case class GraphStats(
  nodeCount: Int,
  edgeCount: Int,
  avgDegree: Double,
  initNodeId: Int
)

object GraphStats:
  def from(graph: EnrichedGraph): GraphStats =
    GraphStats(
      nodeCount = graph.nodes.size,
      edgeCount = graph.edges.size,
      avgDegree = if graph.nodes.nonEmpty then graph.edges.size.toDouble / graph.nodes.size else 0.0,
      initNodeId = graph.initNodeId
    )

object ResultCollector extends LazyLogging:

  def printSummary(result: ExperimentResult): Unit =
    println(s"\n${"=" * 60}")
    println(s"  Experiment: ${result.name}")
    println(s"${"=" * 60}")
    println(s"  Graph: ${result.graphStats.nodeCount} nodes, ${result.graphStats.edgeCount} edges")
    println(s"  Avg degree: ${"%.2f".format(result.graphStats.avgDegree)}")
    println(s"  Duration: ${result.durationMs}ms")
    println()
    result.algorithmResults.foreach { r =>
      println(s"  ${r.summary}")
    }
    println(s"${"=" * 60}\n")

  def printComparison(results: List[ExperimentResult]): Unit =
    println(s"\n${"=" * 60}")
    println("  EXPERIMENT COMPARISON")
    println(s"${"=" * 60}")
    println(f"  ${"Name"}%-20s ${"Nodes"}%6s ${"Edges"}%6s ${"AvgDeg"}%8s ${"Duration"}%10s")
    println(s"  ${"-" * 56}")
    results.foreach { r =>
      println(f"  ${r.name}%-20s ${r.graphStats.nodeCount}%6d ${r.graphStats.edgeCount}%6d ${r.graphStats.avgDegree}%8.2f ${r.durationMs}%8dms")
    }
    println(s"${"=" * 60}\n")

  def writeToFile(result: ExperimentResult, path: String): Unit =
    val file = new File(path)
    file.getParentFile.mkdirs()
    val writer = new PrintWriter(file)
    try
      val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
      writer.println(s"""{"experiment": "${result.name}",""")
      writer.println(s""" "timestamp": "$timestamp",""")
      writer.println(s""" "graph": {"nodes": ${result.graphStats.nodeCount}, "edges": ${result.graphStats.edgeCount}, "avgDegree": ${result.graphStats.avgDegree}},""")
      writer.println(s""" "durationMs": ${result.durationMs},""")
      writer.println(s""" "algorithms": [""")
      result.algorithmResults.zipWithIndex.foreach { case (r, i) =>
        val comma = if i < result.algorithmResults.size - 1 then "," else ""
        r match
          case sr: SnapshotResult =>
            writer.println(s"""   {"name": "chandy-lamport", "nodesCaptures": ${sr.nodeStates.size}, "channelMessages": ${sr.channelStates.values.map(_.size).sum}, "timeMs": ${sr.timestampMs}}$comma""")
          case tr: TerminationResult =>
            writer.println(s"""   {"name": "dijkstra-scholten", "detected": ${tr.detected}, "timeMs": ${tr.detectionTimeMs}}$comma""")
      }
      writer.println(s""" ]""")
      writer.println("}")
      logger.info(s"Results written to $path")
    finally
      writer.close()
