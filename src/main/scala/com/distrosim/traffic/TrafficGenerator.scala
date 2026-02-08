package com.distrosim.traffic

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.distrosim.actor.{DataMessage, StopSimulation}
import com.distrosim.graph.{EnrichedGraph, MessageType}
import com.distrosim.util.RandomUtil

import java.util.UUID
import scala.concurrent.duration.*
import scala.util.Random

// Generates background traffic at a configurable rate.
// Picks random connected node pairs and sends messages
// that respect edge label constraints.
class TrafficGenerator(
  nodeActors: Map[Int, ActorRef],
  enrichedGraph: EnrichedGraph,
  rate: FiniteDuration,
  seed: Long
) extends Actor with ActorLogging:

  import context.dispatcher
  private val rng = new Random(seed)
  private val edgeList = enrichedGraph.edges.toVector
  private var messageCount: Long = 0

  override def preStart(): Unit =
    log.info(s"TrafficGenerator starting: rate=$rate, edges=${edgeList.size}")
    scheduleNext()

  def receive: Receive =
    case "generate" =>
      if edgeList.nonEmpty then
        val ((fromId, toId), edge) = edgeList(rng.nextInt(edgeList.size))
        val allowedTypes = edge.allowedMessageTypes.toVector
        if allowedTypes.nonEmpty then
          val msgType = allowedTypes(rng.nextInt(allowedTypes.size))
          val msg = DataMessage(
            msgType = msgType,
            payload = s"bg-$messageCount",
            fromNodeId = fromId,
            toNodeId = toId,
            timestamp = System.currentTimeMillis(),
            msgId = UUID.randomUUID()
          )
          // Send via the source node (so it goes through proper routing)
          nodeActors.get(fromId).foreach(_ ! msg)
          messageCount += 1
      scheduleNext()

    case StopSimulation =>
      log.info(s"TrafficGenerator stopping after $messageCount messages")
      context.stop(self)

  private def scheduleNext(): Unit =
    val delayMs = (rate.toMillis * (0.5 + rng.nextDouble())).toLong
    context.system.scheduler.scheduleOnce(delayMs.millis, self, "generate")
