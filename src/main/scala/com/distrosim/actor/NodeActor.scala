package com.distrosim.actor

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.distrosim.graph.{EnrichedEdge, EnrichedNode, MessageType}
import com.distrosim.algorithm.snapshot.ChandyLamportMixin
import com.distrosim.algorithm.termination.DijkstraScholtenMixin
import com.distrosim.util.RandomUtil

import java.util.UUID
import scala.concurrent.duration.*
import scala.util.Random

// Each graph node becomes one NodeActor.
// Handles application messages, delegates to algorithm mixins
// for snapshot and termination detection protocols.
// Follows the professor's pattern: extends Actor + ActorLogging,
// uses context.become() for state transitions.
class NodeActor(
  val nodeId: Int,
  val enrichedNode: EnrichedNode,
  val outgoingEdges: Map[Int, EnrichedEdge],
  val seed: Long
) extends Actor with ActorLogging:

  import context.dispatcher

  private var state = NodeActorState(nodeId)
  private var neighbors: Map[Int, ActorRef] = Map.empty
  private val rng = new Random(seed + nodeId)

  // Algorithm mixins — initialized when algorithm manager is set
  private var algorithmManager: Option[ActorRef] = None
  private val snapshotMixin = new ChandyLamportMixin(nodeId)
  private val terminationMixin = new DijkstraScholtenMixin(nodeId)

  // === State machine: initializing -> active -> stopped ===

  def receive: Receive = initializing

  def initializing: Receive =
    case RegisterNeighbor(nid, ref) =>
      neighbors += (nid -> ref)
      log.debug(s"Node $nodeId: registered neighbor $nid")

    case SetAlgorithmManager(ref) =>
      algorithmManager = Some(ref)

    case StartSimulation =>
      log.info(s"Node $nodeId: starting (${neighbors.size} neighbors)")
      context.become(active)

    case GetState =>
      sender() ! StateReport(nodeId, state)

  def active: Receive =
    // Application data messages
    case msg: DataMessage =>
      handleDataMessage(msg)

    // Chandy-Lamport snapshot messages
    case msg: InitiateSnapshot =>
      val completed = snapshotMixin.handleInitiate(msg, state, neighbors, self)
      completed.foreach { recorded =>
        algorithmManager.foreach(_ ! recorded)
      }

    case msg: Marker =>
      val completed = snapshotMixin.handleMarker(msg, state, neighbors, self)
      completed.foreach { recorded =>
        algorithmManager.foreach(_ ! recorded)
      }

    // Dijkstra-Scholten termination messages
    case msg: DSInitiate =>
      terminationMixin.initiate(msg.initiatorId, neighbors, self)

    case msg: Signal =>
      terminationMixin.handleSignal(msg, neighbors, self)

    case msg: Ack =>
      val terminated = terminationMixin.handleAck(msg, neighbors, self)
      if terminated then
        algorithmManager.foreach(_ ! TerminationDetected(nodeId, msg.computationId))

    // Timer ticks — produce messages
    case Tick(seq) =>
      produceMessages(seq)

    // External input
    case ExternalInput(content, _) =>
      state = state.copy(
        messagesProcessed = state.messagesProcessed + 1,
        localClock = state.localClock + 1,
        storedValues = state.storedValues + (s"input-${state.localClock}" -> content)
      )

    case SetAlgorithmManager(ref) =>
      algorithmManager = Some(ref)

    case RegisterNeighbor(nid, ref) =>
      neighbors += (nid -> ref)

    case GetState =>
      sender() ! StateReport(nodeId, state)

    case StopSimulation =>
      log.info(s"Node $nodeId: stopping (processed=${state.messagesProcessed}, sent=${state.messagesSent})")
      context.become(stopped)

  def stopped: Receive =
    case GetState => sender() ! StateReport(nodeId, state)
    case _ => // ignore all other messages

  // === Core message handling ===

  private def handleDataMessage(msg: DataMessage): Unit =
    // Update Lamport clock
    state = state.copy(
      messagesProcessed = state.messagesProcessed + 1,
      localClock = math.max(state.localClock, msg.timestamp) + 1
    )
    log.debug(s"Node $nodeId: received ${msg.msgType} from ${msg.fromNodeId}")

    // Record in channel state if snapshot is recording
    snapshotMixin.recordChannelMessage(msg)

    // Notify DS mixin of received computation message
    terminationMixin.onMessageReceived(msg.fromNodeId)

    // Possibly forward to a random neighbor
    maybeForwardMessage(msg)

  private def produceMessages(seq: Long): Unit =
    // Sample a message type from this node's distribution
    val msgType = sampleMessageType()

    neighbors.foreach { case (neighborId, ref) =>
      outgoingEdges.get(neighborId).foreach { edge =>
        if edge.allowedMessageTypes.contains(msgType) then
          if rng.nextDouble() < edge.reliability then
            val msg = DataMessage(
              msgType = msgType,
              payload = s"tick-$seq-from-$nodeId",
              fromNodeId = nodeId,
              toNodeId = neighborId,
              timestamp = state.localClock,
              msgId = UUID.randomUUID()
            )
            // Apply transmission delay
            context.system.scheduler.scheduleOnce(edge.transmissionDelayMs.millis) {
              ref ! msg
            }
            state = state.copy(
              messagesSent = state.messagesSent + 1,
              localClock = state.localClock + 1
            )
            // Notify DS mixin
            terminationMixin.onMessageSent(neighborId)
      }
    }

  private def maybeForwardMessage(msg: DataMessage): Unit =
    // Forward with 30% probability to a random allowed neighbor
    if rng.nextDouble() < 0.3 && neighbors.nonEmpty then
      val candidates = neighbors.keys.toList.filter { nid =>
        outgoingEdges.get(nid).exists(_.allowedMessageTypes.contains(msg.msgType))
      }
      if candidates.nonEmpty then
        val targetId = candidates(rng.nextInt(candidates.size))
        val edge = outgoingEdges(targetId)
        val forwarded = msg.copy(
          fromNodeId = nodeId,
          toNodeId = targetId,
          timestamp = state.localClock,
          msgId = UUID.randomUUID()
        )
        context.system.scheduler.scheduleOnce(edge.transmissionDelayMs.millis) {
          neighbors(targetId) ! forwarded
        }
        state = state.copy(
          messagesSent = state.messagesSent + 1,
          localClock = state.localClock + 1
        )

  private def sampleMessageType(): MessageType =
    RandomUtil.weightedSample(enrichedNode.messageProductionDist, rng)

object NodeActor:
  def props(
    nodeId: Int,
    enrichedNode: EnrichedNode,
    outgoingEdges: Map[Int, EnrichedEdge],
    seed: Long
  ): Props = Props(new NodeActor(nodeId, enrichedNode, outgoingEdges, seed))
