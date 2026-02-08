package com.distrosim.traffic

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.distrosim.actor.{Tick, StopSimulation}
import scala.concurrent.duration.*
import scala.util.Random

// A timer actor that sends periodic Tick messages to target node actors.
// Uses Akka scheduler with jitter for realistic timing.
// Follows the professor's ScheduleEventsRandomIntervals pattern.
class TimerNode(
  targets: List[ActorRef],
  interval: FiniteDuration,
  jitter: FiniteDuration,
  seed: Long
) extends Actor with ActorLogging:

  import context.dispatcher
  private val rng = new Random(seed)
  private var tickCount: Long = 0

  override def preStart(): Unit =
    log.info(s"TimerNode starting: interval=$interval, jitter=$jitter, targets=${targets.size}")
    scheduleTick()

  def receive: Receive =
    case "tick" =>
      tickCount += 1
      targets.foreach(_ ! Tick(tickCount))
      scheduleTick()

    case StopSimulation =>
      log.info(s"TimerNode stopping after $tickCount ticks")
      context.stop(self)

  private def scheduleTick(): Unit =
    val jitterMs = (rng.nextDouble() * jitter.toMillis).toLong
    val delay = interval + jitterMs.millis
    context.system.scheduler.scheduleOnce(delay, self, "tick")
