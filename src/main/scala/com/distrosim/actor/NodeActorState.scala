package com.distrosim.actor

// Immutable snapshot of a node actor's state at a point in time.
// Used by Chandy-Lamport to capture local state.
case class NodeActorState(
  nodeId: Int,
  messagesProcessed: Long = 0,
  messagesSent: Long = 0,
  localClock: Long = 0,
  storedValues: Map[String, String] = Map.empty,
  isActive: Boolean = true
)
