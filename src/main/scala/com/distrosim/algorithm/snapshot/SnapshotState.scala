package com.distrosim.algorithm.snapshot

import com.distrosim.actor.{NodeActorState, DataMessage}
import java.util.UUID

// A complete global snapshot captured by the Chandy-Lamport algorithm.
// Contains the local state of every node and the state of every channel
// (messages in transit) at the time of the snapshot.
case class GlobalSnapshot(
  snapshotId: UUID,
  nodeStates: Map[Int, NodeActorState],
  channelStates: Map[(Int, Int), List[DataMessage]],
  capturedAtMs: Long
):
  def totalMessagesProcessed: Long = nodeStates.values.map(_.messagesProcessed).sum
  def totalMessagesSent: Long = nodeStates.values.map(_.messagesSent).sum
  def totalMessagesInTransit: Int = channelStates.values.map(_.size).sum

  def prettyPrint: String =
    val sb = new StringBuilder
    sb.append(s"=== Global Snapshot [${snapshotId.toString.take(8)}] ===\n")
    sb.append(s"Captured at: ${capturedAtMs}ms\n")
    sb.append(s"Nodes: ${nodeStates.size}\n")
    sb.append(s"Total processed: $totalMessagesProcessed\n")
    sb.append(s"Total sent: $totalMessagesSent\n")
    sb.append(s"Messages in transit: $totalMessagesInTransit\n")
    sb.append("\nPer-node state:\n")
    nodeStates.toList.sortBy(_._1).foreach { case (id, state) =>
      sb.append(s"  Node $id: processed=${state.messagesProcessed}, " +
        s"sent=${state.messagesSent}, clock=${state.localClock}\n")
    }
    if channelStates.values.exists(_.nonEmpty) then
      sb.append("\nNon-empty channels:\n")
      channelStates.filter(_._2.nonEmpty).foreach { case ((from, to), msgs) =>
        sb.append(s"  ($from -> $to): ${msgs.size} messages\n")
      }
    sb.toString()
