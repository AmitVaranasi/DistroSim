package com.distrosim.algorithm.termination

import java.util.UUID

// Per-node state for Dijkstra-Scholten termination detection.
// Tracks the node's role, parent, and deficit counters.
case class DSNodeState(
  nodeId: Int,
  isInitiator: Boolean = false,
  parent: Option[Int] = None,
  computationId: Option[UUID] = None,
  incomingDeficit: Map[Int, Int] = Map.empty,
  outgoingDeficit: Map[Int, Int] = Map.empty,
  isActive: Boolean = false
):
  def totalIncomingDeficit: Int = incomingDeficit.values.sum
  def totalOutgoingDeficit: Int = outgoingDeficit.values.sum

  def prettyPrint: String =
    s"DSNode($nodeId, initiator=$isInitiator, parent=$parent, " +
    s"active=$isActive, inDeficit=$totalIncomingDeficit, outDeficit=$totalOutgoingDeficit)"
