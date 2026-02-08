package com.distrosim.algorithm.snapshot

import akka.actor.ActorRef
import com.distrosim.actor.{InitiateSnapshot, RecordedState, NodeActorState, DataMessage}
import com.distrosim.algorithm.{AlgorithmPlugin, AlgorithmResult, SnapshotResult}
import com.typesafe.scalalogging.LazyLogging

import java.util.UUID

// Manages a Chandy-Lamport snapshot across the entire actor graph.
// Initiates the snapshot on a chosen node and collects RecordedState
// messages from all nodes until the snapshot is complete.
class ChandyLamportManager(
  nodeActors: Map[Int, ActorRef],
  initiatorId: Int
) extends AlgorithmPlugin with LazyLogging:

  val snapshotId: UUID = UUID.randomUUID()
  private var collectedStates: Map[Int, RecordedState] = Map.empty
  private var completed = false
  private var resultHolder: Option[SnapshotResult] = None
  private var startTime: Long = 0

  def name: String = "chandy-lamport"

  def start(): Unit =
    startTime = System.currentTimeMillis()
    logger.info(s"Initiating Chandy-Lamport snapshot [${snapshotId.toString.take(8)}] from node $initiatorId")
    nodeActors.get(initiatorId).foreach { ref =>
      ref ! InitiateSnapshot(snapshotId)
    }

  def onStateReceived(rs: RecordedState): Unit =
    if rs.snapshotId == snapshotId then
      collectedStates += (rs.nodeId -> rs)
      logger.debug(s"Snapshot: received state from node ${rs.nodeId} (${collectedStates.size}/${nodeActors.size})")
      if collectedStates.size == nodeActors.size then
        completed = true
        val elapsed = System.currentTimeMillis() - startTime
        resultHolder = Some(SnapshotResult(
          snapshotId = snapshotId,
          nodeStates = collectedStates.map { case (id, rs) => id -> rs.localState },
          channelStates = collectedStates.flatMap { case (_, rs) =>
            rs.channelStates.map { case (fromId, msgs) => (fromId, rs.nodeId) -> msgs }
          },
          timestampMs = elapsed
        ))
        logger.info(s"Chandy-Lamport snapshot complete in ${elapsed}ms")

  def isComplete: Boolean = completed

  def results: Option[AlgorithmResult] = resultHolder
