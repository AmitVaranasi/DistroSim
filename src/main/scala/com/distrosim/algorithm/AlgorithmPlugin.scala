package com.distrosim.algorithm

import akka.actor.ActorRef
import com.distrosim.graph.EnrichedGraph
import com.distrosim.actor.{NodeActorState, DataMessage}
import java.util.UUID

// Trait that all distributed algorithm implementations must satisfy.
// Provides a uniform interface for the AlgorithmManager to
// initialize, start, monitor, and collect results from algorithms.
trait AlgorithmPlugin:
  def name: String
  def start(): Unit
  def isComplete: Boolean
  def results: Option[AlgorithmResult]

// Result types returned by algorithm implementations.
sealed trait AlgorithmResult:
  def summary: String

case class SnapshotResult(
  snapshotId: UUID,
  nodeStates: Map[Int, NodeActorState],
  channelStates: Map[(Int, Int), List[DataMessage]],
  timestampMs: Long
) extends AlgorithmResult:
  def summary: String =
    val totalChannelMsgs = channelStates.values.map(_.size).sum
    s"Chandy-Lamport Snapshot [id=${snapshotId.toString.take(8)}]: " +
    s"${nodeStates.size} node states captured, " +
    s"$totalChannelMsgs messages in ${channelStates.size} channels, " +
    s"total processed=${nodeStates.values.map(_.messagesProcessed).sum}, " +
    s"total sent=${nodeStates.values.map(_.messagesSent).sum}"

case class TerminationResult(
  detected: Boolean,
  detectionTimeMs: Long,
  initiatorId: Int,
  computationId: UUID
) extends AlgorithmResult:
  def summary: String =
    if detected then
      s"Dijkstra-Scholten: termination detected in ${detectionTimeMs}ms " +
      s"(initiator=$initiatorId, computation=${computationId.toString.take(8)})"
    else
      s"Dijkstra-Scholten: termination NOT detected (timeout)"
