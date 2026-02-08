package com.distrosim.algorithm.snapshot

import akka.actor.ActorRef
import com.distrosim.actor.*
import java.util.UUID

// Per-snapshot tracking state within a node.
private[snapshot] case class SnapshotInProgress(
  snapshotId: UUID,
  localState: Option[NodeActorState],
  recordingChannels: Set[Int],
  channelRecords: Map[Int, List[DataMessage]],
  markerReceivedFrom: Set[Int]
)

// Mixin that handles Chandy-Lamport snapshot logic inside a NodeActor.
// Each node can participate in multiple concurrent snapshots.
//
// Algorithm:
// 1. Initiator records local state, sends Marker on all outgoing channels
// 2. On first Marker from channel C: record local state, start recording
//    on all other incoming channels, send Marker on all outgoing channels
// 3. On subsequent Marker from channel C: stop recording on C
// 4. When all channels have received Markers: report snapshot to manager
class ChandyLamportMixin(nodeId: Int):

  private var activeSnapshots: Map[UUID, SnapshotInProgress] = Map.empty

  // Called when this node is chosen as the snapshot initiator.
  // Returns Some(RecordedState) if snapshot completes immediately (no incoming channels).
  def handleInitiate(
    msg: InitiateSnapshot,
    currentState: NodeActorState,
    neighbors: Map[Int, ActorRef],
    self: ActorRef
  ): Option[RecordedState] =
    val incomingIds = neighbors.keySet // all neighbors could send us messages
    val snap = SnapshotInProgress(
      snapshotId = msg.snapshotId,
      localState = Some(currentState),
      recordingChannels = incomingIds,
      channelRecords = Map.empty,
      markerReceivedFrom = Set.empty
    )
    activeSnapshots += (msg.snapshotId -> snap)
    // Send Marker on ALL outgoing channels
    neighbors.foreach { case (nid, ref) =>
      ref ! Marker(msg.snapshotId, nodeId)
    }
    // Check if already complete (e.g. node has no incoming channels)
    checkComplete(msg.snapshotId)

  // Called when a Marker arrives from another node.
  // Returns Some(RecordedState) if the snapshot is complete for this node.
  def handleMarker(
    msg: Marker,
    currentState: NodeActorState,
    neighbors: Map[Int, ActorRef],
    self: ActorRef
  ): Option[RecordedState] =
    activeSnapshots.get(msg.snapshotId) match
      case None =>
        // First Marker for this snapshot: record local state
        val incomingIds = neighbors.keySet - msg.fromNodeId
        val snap = SnapshotInProgress(
          snapshotId = msg.snapshotId,
          localState = Some(currentState),
          recordingChannels = incomingIds,
          channelRecords = Map(msg.fromNodeId -> List.empty),
          markerReceivedFrom = Set(msg.fromNodeId)
        )
        activeSnapshots += (msg.snapshotId -> snap)
        // Send Marker on all outgoing channels
        neighbors.foreach { case (nid, ref) =>
          ref ! Marker(msg.snapshotId, nodeId)
        }
        // Check if already complete (no other incoming channels)
        checkComplete(msg.snapshotId)

      case Some(snap) =>
        // Already recording: stop recording on this channel
        val updatedSnap = snap.copy(
          recordingChannels = snap.recordingChannels - msg.fromNodeId,
          markerReceivedFrom = snap.markerReceivedFrom + msg.fromNodeId,
          channelRecords = snap.channelRecords +
            (msg.fromNodeId -> snap.channelRecords.getOrElse(msg.fromNodeId, List.empty))
        )
        activeSnapshots += (msg.snapshotId -> updatedSnap)
        checkComplete(msg.snapshotId)

  // Record a data message that arrived while we're recording channel state.
  def recordChannelMessage(msg: DataMessage): Unit =
    activeSnapshots = activeSnapshots.map { case (sid, snap) =>
      if snap.recordingChannels.contains(msg.fromNodeId) then
        val updated = snap.channelRecords.updatedWith(msg.fromNodeId) {
          case Some(msgs) => Some(msgs :+ msg)
          case None       => Some(List(msg))
        }
        sid -> snap.copy(channelRecords = updated)
      else
        sid -> snap
    }

  // Check if all Markers have been received for a snapshot.
  private def checkComplete(snapshotId: UUID): Option[RecordedState] =
    activeSnapshots.get(snapshotId).flatMap { snap =>
      if snap.recordingChannels.isEmpty then
        activeSnapshots -= snapshotId
        Some(RecordedState(
          snapshotId = snap.snapshotId,
          nodeId = nodeId,
          localState = snap.localState.get,
          channelStates = snap.channelRecords
        ))
      else
        None
    }
