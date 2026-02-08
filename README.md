# DistroSim

A distributed algorithms simulator built with Scala 3 and Akka Classic actors. Generates random network graphs, enriches them with message-type labels and probability distributions, converts them into a live Akka actor system, and runs classical distributed algorithms on top.

Built for **CS553 — Distributed Algorithms** at the University of Illinois Chicago, Spring 2026.

## Implemented Algorithms

### Chandy-Lamport Global Snapshot
Captures a consistent global state of the distributed system by propagating Marker messages through all channels. Each node records its local state on first Marker receipt and records incoming channel messages until all channels have delivered Markers.

### Dijkstra-Scholten Termination Detection
Detects when a diffusing computation has terminated across the distributed system. Builds a spanning tree rooted at the initiator; each node sends Ack messages back toward the root once it becomes passive and all its children have acknowledged.

## Architecture

```
NetGameSim-compatible graph
  -> GraphLoader (generate random graph with configurable properties)
    -> GraphEnricher (add edge labels, node probability distributions)
      -> ActorGraphBuilder (1 Akka actor per node, edges = channels)
        -> TimerNode / TrafficGenerator / InputNode (traffic sources)
        -> AlgorithmManager
            |-- ChandyLamportManager (global snapshots)
            |-- DijkstraScholtenManager (termination detection)
  -> ExperimentRunner (3 configurations) -> ResultCollector -> JSON output
```

## Prerequisites

- **Java 11+** (tested with Java 25)
- **SBT 1.9+** (`brew install sbt` on macOS)

## Quick Start

```bash
# Clone and enter the project
cd DistroSim

# Compile
sbt compile

# Run all experiments
sbt run

# Results are written to results/ directory
```

## Project Structure

```
src/main/scala/com/distrosim/
  Main.scala                          # Entry point
  config/SimConfig.scala              # Typed configuration wrapper
  graph/
    GraphLoader.scala                 # Random graph generation
    GraphEnricher.scala               # Edge labels + node distributions
    EnrichedGraph.scala               # Data models (EnrichedNode, EnrichedEdge)
  actor/
    NodeActor.scala                   # Core node actor (state machine)
    NodeActorState.scala              # Per-node state snapshot
    Messages.scala                    # All message types
    ActorGraphBuilder.scala           # Graph -> actor system conversion
    ChannelRegistry.scala             # Edge metadata registry
  algorithm/
    AlgorithmPlugin.scala             # Plugin trait + result types
    AlgorithmManager.scala            # Algorithm lifecycle coordinator
    snapshot/
      ChandyLamportMixin.scala        # Per-node CL logic
      ChandyLamportManager.scala      # CL orchestrator
      SnapshotState.scala             # Global snapshot data model
    termination/
      DijkstraScholtenMixin.scala     # Per-node DS logic
      DijkstraScholtenManager.scala   # DS orchestrator
      DSNodeState.scala               # DS per-node state
  traffic/
    TimerNode.scala                   # Periodic tick generation
    TrafficGenerator.scala            # Background random traffic
    InputNode.scala                   # File-driven message injection
    MessageFactory.scala              # Message creation utilities
  experiment/
    ExperimentRunner.scala            # End-to-end experiment execution
    ExperimentConfig.scala            # Per-experiment parameters
    ResultCollector.scala             # Output formatting + JSON export
  util/
    RandomUtil.scala                  # Seeded random utilities
```

## Configuration

All settings are in `src/main/resources/application.conf`. Per-experiment overrides live in `src/main/resources/experiments/`.

### Key Settings

| Setting | Default | Description |
|---------|---------|-------------|
| `graph.states-total` | 50 | Number of nodes |
| `graph.edge-probability` | 0.3 | Probability of edge between any two nodes |
| `simulation.seed` | 42 | Random seed for reproducibility |
| `simulation.warmup-period` | 5s | Traffic warm-up before algorithms run |
| `traffic.rate` | 100ms | Average interval between background messages |
| `timer.count` | 3 | Number of timer nodes |
| `enrichment.reliability` | 0.95 | Message delivery probability per edge |

### Experiment Configurations

| Experiment | Nodes | Edge Prob | Traffic Rate | Description |
|------------|-------|-----------|-------------|-------------|
| `small-sparse` | 15 | 0.2 | 500ms | Basic correctness test |
| `medium-dense` | 50 | 0.6 | 50ms | Scalability under load |
| `large-tree` | 50 | 0.15 | 200ms | Deep spanning trees |

## Design Decisions

1. **Mixin pattern for algorithms**: ChandyLamportMixin and DijkstraScholtenMixin are instantiated inside each NodeActor rather than as separate actors. This keeps all node behavior co-located and avoids coordination overhead.

2. **Classic Akka actors**: Uses `Actor` trait with `receive` and `context.become()` for state transitions, following the patterns from the professor's PLANE repository examples.

3. **Seeded randomness**: Every random operation derives its seed from `(experimentSeed + offset)`, ensuring fully reproducible results across runs.

4. **Transmission delays via scheduler**: Edge delays use `context.system.scheduler.scheduleOnce()` for non-blocking, realistic message propagation.

5. **Bidirectional neighbor registration**: Even for directed graphs, both endpoints of an edge know about each other. This is essential for Chandy-Lamport (nodes need to know all incoming channels to record channel state).

## Technology Stack

- **Scala 3.3.4** (LTS)
- **Akka 2.8.8** (latest stable with Scala 3 support)
- **SBT 1.9.8**
- **Circe 0.14.6** (JSON serialization)
- **Google Guava 32.1.3** (graph structures)
- **Logback 1.4.14** (logging)
- **ScalaTest 3.2.17** (testing)

## How It Works

### Graph Generation
The `GraphLoader` generates random directed graphs with configurable node count and edge probability. It ensures full connectivity by adding edges from reachable nodes to any isolated components. Each node gets random properties (children count, stored values) compatible with NetGameSim's `NodeObject` model.

### Graph Enrichment
The `GraphEnricher` assigns:
- **Edge labels**: Each edge gets a set of allowed message types (DataTransfer, StateQuery, StateUpdate, Heartbeat) — messages that don't match an edge's labels are blocked.
- **Node distributions**: Each node gets a probability distribution over message types, controlling what kind of messages it produces.
- **Delays**: Transmission delays per edge, processing delays per node.
- **Reliability**: Probability that a message is actually delivered on each edge.

### Actor System
Each graph node becomes a `NodeActor` with three states (initializing -> active -> stopped). Edges become communication channels enforced through the neighbor registration system. The `ActorGraphBuilder` creates all actors, registers neighbors, and wires up traffic sources and the algorithm manager.

### Chandy-Lamport Snapshot
1. Initiator records local state, sends Marker to all neighbors
2. On first Marker: record local state, start recording on all other channels, forward Marker
3. On subsequent Markers: stop recording on that channel
4. When all channels have received Markers: report RecordedState to AlgorithmManager
5. Manager collects all states to form a consistent global snapshot

### Dijkstra-Scholten Termination Detection
1. Initiator sends Signal to all neighbors, becomes passive
2. Each node sets its parent (first Signal sender), propagates Signals, becomes passive
3. Leaf nodes (no outgoing signals) send Ack to parent
4. Internal nodes send Ack to parent when all children have Acked
5. Initiator detects termination when all outgoing Acks are received

## References

- [NetGameSim](https://github.com/0x1DOCD00D/NetGameSim) — Upstream graph generation platform
- [PLANE Akka Examples](https://github.com/0x1DOCD00D/PLANE/tree/master/src/main/scala/Akka) — Professor's Akka pattern examples
- [CS553 Course Project Spec](https://github.com/0x1DOCD00D/CS553_2026/blob/main/CourseProject.MD)
- Chandy, K.M. and Lamport, L. "Distributed Snapshots: Determining Global States of Distributed Systems" (1985)
- Dijkstra, E.W. and Scholten, C.S. "Termination Detection for Diffusing Computations" (1980)
