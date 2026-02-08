package com.distrosim.actor

import akka.actor.ActorRef
import com.distrosim.graph.EnrichedEdge

// A channel represents a directed communication link between two actors.
case class Channel(
  fromId: Int,
  toId: Int,
  fromRef: ActorRef,
  toRef: ActorRef,
  edge: EnrichedEdge
)

// Registry of all channels in the actor graph.
// Used by algorithm managers to understand topology.
class ChannelRegistry(channels: Map[(Int, Int), Channel]):

  def incomingChannels(nodeId: Int): List[Channel] =
    channels.values.filter(_.toId == nodeId).toList

  def outgoingChannels(nodeId: Int): List[Channel] =
    channels.values.filter(_.fromId == nodeId).toList

  def allChannels: List[Channel] = channels.values.toList

  def channelBetween(fromId: Int, toId: Int): Option[Channel] =
    channels.get((fromId, toId))

  def size: Int = channels.size

object ChannelRegistry:
  def build(
    nodeActors: Map[Int, ActorRef],
    edges: Map[(Int, Int), EnrichedEdge]
  ): ChannelRegistry =
    val channels = edges.flatMap { case ((fromId, toId), edge) =>
      for
        fromRef <- nodeActors.get(fromId)
        toRef   <- nodeActors.get(toId)
      yield (fromId, toId) -> Channel(fromId, toId, fromRef, toRef, edge)
    }
    new ChannelRegistry(channels)
