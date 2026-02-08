package com.distrosim.graph

import scala.concurrent.duration.FiniteDuration

// Message types that can flow over edges in the simulation.
// Each edge is labeled with the set of message types it allows.
enum MessageType:
  case DataTransfer, StateQuery, StateUpdate, Heartbeat

object MessageType:
  val all: Set[MessageType] = Set(DataTransfer, StateQuery, StateUpdate, Heartbeat)

  def fromString(s: String): MessageType = s match
    case "DataTransfer" => DataTransfer
    case "StateQuery"   => StateQuery
    case "StateUpdate"  => StateUpdate
    case "Heartbeat"    => Heartbeat
    case other          => DataTransfer // default fallback

// A graph node enriched with message production probabilities
// and a simulated processing delay.
case class EnrichedNode(
  id: Int,
  children: Int,
  properties: Int,
  messageProductionDist: Map[MessageType, Double],
  processingDelayMs: Long
)

// A graph edge enriched with allowed message types,
// transmission delay, and reliability.
case class EnrichedEdge(
  fromId: Int,
  toId: Int,
  actionType: Int,
  cost: Double,
  allowedMessageTypes: Set[MessageType],
  transmissionDelayMs: Long,
  reliability: Double // 0.0 to 1.0 — probability message is delivered
)

// The complete enriched graph, ready to be converted into actors.
case class EnrichedGraph(
  nodes: Map[Int, EnrichedNode],
  edges: Map[(Int, Int), EnrichedEdge],
  adjacency: Map[Int, Set[Int]],
  initNodeId: Int
):
  def nodeIds: Set[Int] = nodes.keySet
  def neighborsOf(nodeId: Int): Set[Int] = adjacency.getOrElse(nodeId, Set.empty)
  def edgeBetween(from: Int, to: Int): Option[EnrichedEdge] = edges.get((from, to))
  def incomingEdges(nodeId: Int): Map[Int, EnrichedEdge] =
    edges.collect { case ((f, t), e) if t == nodeId => f -> e }
  def outgoingEdges(nodeId: Int): Map[Int, EnrichedEdge] =
    edges.collect { case ((f, t), e) if f == nodeId => t -> e }
