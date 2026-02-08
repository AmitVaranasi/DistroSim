package com.distrosim.traffic

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.distrosim.actor.{ExternalInput, StopSimulation}
import com.distrosim.graph.MessageType

import scala.concurrent.duration.*
import scala.util.Random

// Reads messages from a file and injects them into the actor system
// at specified delays. File format per line:
//   targetNodeId|messageType|payload|delayMs
// Lines starting with # are comments.
class InputNode(
  targets: Map[Int, ActorRef],
  source: String,
  filePath: String,
  seed: Long
) extends Actor with ActorLogging:

  import context.dispatcher

  override def preStart(): Unit =
    source match
      case "file" => loadAndScheduleFromFile(filePath)
      case _ => log.warning(s"InputNode: unknown source type '$source'")

  def receive: Receive =
    case StopSimulation =>
      log.info("InputNode stopping")
      context.stop(self)

  private def loadAndScheduleFromFile(path: String): Unit =
    try
      val lines = scala.io.Source.fromFile(path).getLines()
        .map(_.trim)
        .filterNot(l => l.isEmpty || l.startsWith("#"))
        .toList

      log.info(s"InputNode: loaded ${lines.size} messages from $path")

      lines.foreach { line =>
        val parts = line.split("\\|")
        if parts.length >= 4 then
          val targetId = parts(0).toInt
          val payload = parts(2)
          val delayMs = parts(3).toLong

          context.system.scheduler.scheduleOnce(delayMs.millis) {
            targets.get(targetId).foreach { ref =>
              ref ! ExternalInput(payload, targetId)
            }
          }
      }
    catch
      case e: Exception =>
        log.error(s"InputNode: failed to load file $path: ${e.getMessage}")
