package com.distrosim.algorithm.termination

import akka.actor.ActorRef
import com.distrosim.actor.{DSInitiate, TerminationDetected}
import com.distrosim.algorithm.{AlgorithmPlugin, AlgorithmResult, TerminationResult}
import com.typesafe.scalalogging.LazyLogging

import java.util.UUID

// Manages Dijkstra-Scholten termination detection.
// Initiates a diffusing computation from a chosen node and waits
// for the initiator to report that termination has been detected.
class DijkstraScholtenManager(
  nodeActors: Map[Int, ActorRef],
  initiatorId: Int
) extends AlgorithmPlugin with LazyLogging:

  private val computationId: UUID = UUID.randomUUID()
  private var completed = false
  private var resultHolder: Option[TerminationResult] = None
  private var startTime: Long = 0

  def name: String = "dijkstra-scholten"

  def start(): Unit =
    startTime = System.currentTimeMillis()
    logger.info(s"Starting Dijkstra-Scholten termination detection from node $initiatorId")
    nodeActors.get(initiatorId).foreach { ref =>
      ref ! DSInitiate(initiatorId)
    }

  def onTerminationDetected(td: TerminationDetected): Unit =
    if !completed then
      completed = true
      val elapsed = System.currentTimeMillis() - startTime
      resultHolder = Some(TerminationResult(
        detected = true,
        detectionTimeMs = elapsed,
        initiatorId = td.initiatorId,
        computationId = td.computationId
      ))
      logger.info(s"Dijkstra-Scholten: termination detected in ${elapsed}ms")

  // Called on timeout if termination wasn't detected
  def onTimeout(): Unit =
    if !completed then
      completed = true
      val elapsed = System.currentTimeMillis() - startTime
      resultHolder = Some(TerminationResult(
        detected = false,
        detectionTimeMs = elapsed,
        initiatorId = initiatorId,
        computationId = computationId
      ))
      logger.warn(s"Dijkstra-Scholten: timeout after ${elapsed}ms")

  def isComplete: Boolean = completed

  def results: Option[AlgorithmResult] = resultHolder
