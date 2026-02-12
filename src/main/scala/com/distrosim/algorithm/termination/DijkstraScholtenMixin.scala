package com.distrosim.algorithm.termination

import akka.actor.ActorRef
import com.distrosim.actor.{Signal as DSSignal, Ack as DSAck, TerminationDetected}
import java.util.UUID

// Mixin that handles Dijkstra-Scholten termination detection
// inside a NodeActor.
//
// Algorithm:
// 1. Initiator starts computation by sending Signal messages
// 2. Each node tracks a deficit: # signals received - # acks sent
// 3. A node's parent is whoever first activated it (sent first Signal)
// 4. A node can send Ack to its parent only when:
//    - It is passive (no pending work)
//    - All nodes it sent Signals to have Acked back (outgoing deficit = 0)
// 5. Initiator detects termination when its outgoing deficit reaches 0
class DijkstraScholtenMixin(nodeId: Int):

  private var isInitiator: Boolean = false
  private var parent: Option[Int] = None
  private var computationId: UUID = UUID.randomUUID()

  // How many Signals we received from each neighbor (unacked)
  private var incomingDeficit: Map[Int, Int] = Map.empty
  // How many Signals we sent to each neighbor (unacked)
  private var outgoingDeficit: Map[Int, Int] = Map.empty
  // Whether this node has pending computation
  private var isActive: Boolean = false

  // Called to make this node the DS initiator.
  def initiate(initiatorId: Int, neighbors: Map[Int, ActorRef], self: ActorRef): Unit =
    if initiatorId == nodeId then
      isInitiator = true
      computationId = UUID.randomUUID()
      isActive = true
      // Send Signal to all neighbors to start the computation
      neighbors.foreach { case (nid, ref) =>
        ref ! DSSignal(nodeId, computationId)
        outgoingDeficit = outgoingDeficit.updatedWith(nid) {
          case Some(d) => Some(d + 1)
          case None    => Some(1)
        }
      }
      // Initiator becomes passive after sending all signals
      // (it has no local computation to perform)
      isActive = false

  // Called when a Signal message is received.
  def handleSignal(msg: DSSignal, neighbors: Map[Int, ActorRef], self: ActorRef): Unit =
    if msg.computationId != computationId && !isInitiator then
      computationId = msg.computationId

    if isInitiator then
      // Initiator receives signals back from children — immediately ack them
      // so their outgoing deficit can clear and they can ack their own parents
      neighbors.get(msg.fromId).foreach { ref =>
        ref ! DSAck(nodeId, computationId)
      }
      return

    incomingDeficit = incomingDeficit.updatedWith(msg.fromId) {
      case Some(d) => Some(d + 1)
      case None    => Some(1)
    }

    if parent.isEmpty then
      // First activation: set parent
      parent = Some(msg.fromId)
      isActive = true

      // Propagate computation to neighbors (except parent)
      neighbors.foreach { case (nid, ref) =>
        if nid != msg.fromId then
          ref ! DSSignal(nodeId, computationId)
          outgoingDeficit = outgoingDeficit.updatedWith(nid) {
            case Some(d) => Some(d + 1)
            case None    => Some(1)
          }
      }

      // Node becomes passive after propagating
      isActive = false
      tryAckParent(neighbors)

    else
      // Already have a parent: immediately ack extra signals from non-parent
      if Some(msg.fromId) != parent then
        neighbors.get(msg.fromId).foreach { ref =>
          ref ! DSAck(nodeId, computationId)
          incomingDeficit = incomingDeficit.updatedWith(msg.fromId) {
            case Some(d) if d > 1 => Some(d - 1)
            case _                => None
          }
        }
      else
        // Extra signal from parent — will be acked when we become fully idle
        tryAckParent(neighbors)

  // Called when an Ack message is received.
  // Returns true if this node is the initiator and termination is detected.
  def handleAck(msg: DSAck, neighbors: Map[Int, ActorRef], self: ActorRef): Boolean =
    outgoingDeficit = outgoingDeficit.updatedWith(msg.fromId) {
      case Some(d) if d > 1 => Some(d - 1)
      case _                => None
    }

    // Try to propagate ack up to parent
    tryAckParent(neighbors)

    // Check if initiator and all acks received
    if isInitiator && outgoingDeficit.isEmpty && !isActive then
      true
    else
      false

  // Track messages sent (for integration with application traffic)
  def onMessageSent(toId: Int): Unit = ()

  // Track messages received (for integration with application traffic)
  def onMessageReceived(fromId: Int): Unit = ()

  private def tryAckParent(neighbors: Map[Int, ActorRef]): Unit =
    if !isActive && outgoingDeficit.isEmpty && !isInitiator then
      parent.foreach { parentId =>
        // Send acks for ALL remaining incoming deficit from parent
        val parentDeficit = incomingDeficit.getOrElse(parentId, 0)
        if parentDeficit > 0 then
          neighbors.get(parentId).foreach { ref =>
            (0 until parentDeficit).foreach { _ =>
              ref ! DSAck(nodeId, computationId)
            }
            incomingDeficit = incomingDeficit.removed(parentId)
          }
      }
