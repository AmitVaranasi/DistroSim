package com.distrosim.traffic

import com.distrosim.actor.DataMessage
import com.distrosim.graph.MessageType
import java.util.UUID

// Factory for creating typed messages with consistent formatting.
object MessageFactory:

  def createDataMessage(
    msgType: MessageType,
    fromNodeId: Int,
    toNodeId: Int,
    payload: String,
    timestamp: Long
  ): DataMessage =
    DataMessage(
      msgType = msgType,
      payload = payload,
      fromNodeId = fromNodeId,
      toNodeId = toNodeId,
      timestamp = timestamp,
      msgId = UUID.randomUUID()
    )

  def createTickMessage(
    fromNodeId: Int,
    toNodeId: Int,
    tickSeq: Long,
    timestamp: Long
  ): DataMessage =
    createDataMessage(
      MessageType.DataTransfer,
      fromNodeId,
      toNodeId,
      s"tick-$tickSeq",
      timestamp
    )

  def createHeartbeat(
    fromNodeId: Int,
    toNodeId: Int,
    timestamp: Long
  ): DataMessage =
    createDataMessage(
      MessageType.Heartbeat,
      fromNodeId,
      toNodeId,
      "heartbeat",
      timestamp
    )
