package com.distrosim.actor

import akka.actor.ActorRef
import java.util.UUID
import com.distrosim.graph.MessageType

// ============================================================
// All messages used throughout the DistroSim system.
// Organized by category: application, snapshot, termination,
// traffic, and system control.
// ============================================================

// --- Application-level data messages ---

case class DataMessage(
  msgType: MessageType,
  payload: String,
  fromNodeId: Int,
  toNodeId: Int,
  timestamp: Long,
  msgId: UUID
)

// --- Chandy-Lamport Snapshot messages ---

sealed trait SnapshotMessage
case class InitiateSnapshot(snapshotId: UUID) extends SnapshotMessage
case class Marker(snapshotId: UUID, fromNodeId: Int) extends SnapshotMessage
case class RecordedState(
  snapshotId: UUID,
  nodeId: Int,
  localState: NodeActorState,
  channelStates: Map[Int, List[DataMessage]]
) extends SnapshotMessage

// --- Dijkstra-Scholten Termination messages ---

sealed trait TerminationMessage
case class DSInitiate(initiatorId: Int) extends TerminationMessage
case class Signal(fromId: Int, computationId: UUID) extends TerminationMessage
case class Ack(fromId: Int, computationId: UUID) extends TerminationMessage
case class TerminationDetected(initiatorId: Int, computationId: UUID) extends TerminationMessage

// --- Traffic / Timer messages ---

case class Tick(seqNum: Long)
case class ExternalInput(content: String, targetNodeId: Int)

// --- System control messages ---

case object StartSimulation
case object StopSimulation
case class RegisterNeighbor(nodeId: Int, ref: ActorRef)
case class SetAlgorithmManager(ref: ActorRef)
case object GetState
case class StateReport(nodeId: Int, state: NodeActorState)

// --- Algorithm manager messages ---

case class RunAlgorithm(algorithmName: String)
case class AlgorithmComplete(name: String, result: Any)
case class CollectSnapshot(snapshotId: UUID)
