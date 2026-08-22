# JShard-Binomial

A [ShardingSphere](https://shardingsphere.apache.org/)-based database sharding library for Java, using **[BinomialHash](https://arxiv.org/abs/2406.19836)** — a stateless, memory-free consistent hashing algorithm — in place of the classic virtual-node hash ring.

JShard-Binomial is a fork of [JShard](#relationship-to-jshard), replacing its ring-based `JShardHashAlgorithm` with a new `AlgorithmConfig` implementation while keeping the rest of the connection/routing layer (connection config, table config, datasource provider, router) intact.

---

## Why fork JShard?

The original JShard algorithm places `virtualNodesPerShard` (default 160) virtual nodes per shard on a sorted 64-bit ring, cached process-wide in Guava caches, and routes each key by finding the next ring position at or after the key's hash (`TreeMap.ceilingEntry`, i.e. `O(log(N·V))` per lookup where `N` = shard count, `V` = virtual nodes per shard).

BinomialHash removes the ring entirely. It computes a shard index directly from a key's hash and the current shard count, with no cached structure of any kind — routing is a constant number of hash evaluations (up to `OMEGA + 1 = 7` retries in the current implementation), independent of shard count.

### Benchmark

The numbers below come from running **both real implementations** — the original `JShardHashAlgorithm` (ring, with its actual Guava caches) and JShard-Binomial's `AlgorithmConfig` — with the real ShardingSphere 5.5.2 dependency tree, not a simulation. Each measurement below is the median (memory) or average (time) of 5 isolated JVM runs.

**Lookup throughput** (warm cache, 200k lookups per shard count):

| Shards | Ring (ops/s) | BinomialHash (ops/s) | BinomialHash advantage |
|---:|---:|---:|---:|
| 4 | 277,200 | 2,009,801 | 7.3× |
| 16 | 340,716 | 11,775,510 | 34.6× |
| 64 | 287,846 | 11,626,041 | 40.4× |
| 256 | 60,148 | 10,756,899 | 178.9× |
| 1,024 | 14,601 | 9,990,806 | 684.3× |

**Memory** (heap delta to build/route once, isolated JVM, `-Xmx2g`):

| Shards | Ring | BinomialHash |
|---:|---:|---:|
| 4 | ~0 (below measurement noise) | ~0 (below measurement noise) |
| 16 | ~93 KB | ~0 (below measurement noise) |
| 64 | ~652 KB | ~0 (below measurement noise) |
| 256 | ~2.89 MB | ~0 (below measurement noise) |
| 1,024 | ~11.84 MB | ~0 (below measurement noise) |

**Cold build time** (fresh instance, first routing call — includes JIT warm-up, 5-run average):

| Shards | Ring (ms) | BinomialHash (ms) | BinomialHash advantage |
|---:|---:|---:|---:|
| 4 | 98.6 | 2.86 | 34.5× |
| 16 | 118.1 | 1.49 | 79.3× |
| 64 | 147.0 | 2.86 | 51.4× |
| 256 | 237.1 | 3.36 | 70.6× |
| 1,024 | 560.4 | 5.45 | 102.8× |

**Reading these results:** the ring's cost in all three dimensions grows with shard count — roughly `O(N·V)` in memory and `O(N log(N·V))` in per-lookup and build time — because it maintains a real data structure sized to the shard set. BinomialHash's cost is close to flat, because it maintains no structure at all: adding or removing a shard is just a different integer passed into the next call, not a rebuild. The gap widens as the deployment grows, which is the case that matters most for a sharding layer.

**What this benchmark does *not* claim:** these numbers measure the two hashing algorithms directly (identical JVM, identical ShardingSphere version, identical machine); they do not include real network I/O, connection pooling, or query execution, which will dominate end-to-end latency in a live cluster. Reproduce with the harness in [`/benchmark`](#reproducing-the-benchmark) if you want numbers for your own hardware.

---

## Installation

Not yet published to a public Maven repository. Build from source:

```bash
git clone <this-repo-url>
cd JShard-Binomial
mvn install
```

Then depend on it locally:

```xml
<dependency>
    <groupId>org.jshard</groupId>
    <artifactId>jshard-binomial</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

**Requirements:** Java 25+, Maven 3.9+.

> **Note:** if this module ends up living inside the J2OS Platform fork (under `org.j2os.platform.jshard`), update the `groupId` above and the clone URL to match before publishing.

---

## Quick start

```java
JShardConnectionConfig shardA = JShardConnectionConfig.of(
        "org.postgresql.Driver", "jdbc:postgresql://localhost:5432/p1", "postgres", "password", 3);
JShardConnectionConfig shardB = JShardConnectionConfig.of(
        "org.postgresql.Driver", "jdbc:postgresql://localhost:5432/p2", "postgres", "password", 2);
JShardConnectionConfig shardC = JShardConnectionConfig.of(
        "org.postgresql.Driver", "jdbc:postgresql://localhost:5432/p3", "postgres", "password", 15);

try (JShardDataSource ds = JShardDataSourceProvider.builder()
        .shard("shard-a", shardA)
        .shard("shard-b", shardB)
        .shard("shard-c", shardC)
        .table("person", "person_id")
        .showSql(true)
        .build()) {

    try (Connection conn = ds.getConnection();
         PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO person (person_id, name, family) VALUES (?, ?, ?)")) {
        ps.setString(1, UUID.randomUUID().toString());
        ps.setString(2, "mohammad");
        ps.setString(3, "ghaderi");
        ps.execute();
    }
}
```

A full runnable version is in [`Example1.java`](src/main/java/org/examples/jshard/Example1.java) — see below for the rest.

---

## Examples

All examples live under [`org.examples.jshard`](src/main/java/org/examples/jshard). `Example1`–`Example4` connect to a real PostgreSQL instance; `Example0` is fully self-contained (in-memory H2, no external database needed) and lives under `src/test/java` for that reason.

| Example | Demonstrates |
|---|---|
| [`Example0`](src/test/java/org/examples/jshard/Example0.java) | Guided tour — calls every public method on every public class once, against an in-memory H2 database. Start here if you want to see the full API surface in one place. |
| [`Example1`](src/main/java/org/examples/jshard/Example1.java) | The basic case: three shards, one connection each, no replicas. Inserts rows and prints which shard each one routed to. |
| [`Example2`](src/main/java/org/examples/jshard/Example2.java) | Primary + read replicas per shard (`.shard(name, primary, replicas...)`). |
| [`Example3`](src/main/java/org/examples/jshard/Example3.java) | Bulk shard registration from a `Map` (`.shards(...)`), multiple sharded tables on one cluster, and an explicit `assertAllReachable` pre-flight check. |
| [`Example4`](src/main/java/org/examples/jshard/Example4.java) | Multi-tenant isolation: two independent `JShardDataSource` instances, each with its own connection pool. |

---

## How BinomialHash routing works

1. **Slot assignment** — each key is hashed (MurmurHash3, x86-32) into one of `2^14 = 16,384` fixed slots. If the key contains a `{...}` hash-tag segment (Redis Cluster convention), only that segment is hashed, so related keys can be co-located.
2. **Shard resolution** — the slot ID is mapped to a shard index in `[0, shardCount)` via a small number of additional hash evaluations, following the binomial-tree structure described in the [BinomialHash paper](https://arxiv.org/abs/2406.19836).
3. No ring, no virtual nodes, no cache: shard resolution is computed fresh from `(slotId, shardCount)` on every call.

This design gives two guarantees the original ring also aims for, verified independently in this repo's test suite and by an external re-implementation used to cross-check the algorithm during development:

- **Monotonicity** — growing the shard count never moves a key to an old shard; it either stays or moves to the new shard.
- **Minimal disruption on shrink** — removing a shard only relocates the keys that were on that shard.

The current implementation always uses `shardCount` directly (no persisted slot→shard assignment map), so it does not yet exploit the algorithm's designed-for capability of resharding with partial, targeted key movement — that requires storing and updating the slot assignment across shard-count changes, which is a natural next milestone (see [Roadmap](#roadmap)).

---

## API reference

| Class | Package | Purpose |
|---|---|---|
| `AlgorithmConfig` | `org.jshard.algorithm` | `StandardShardingAlgorithm` implementation — BinomialHash routing logic |
| `Algorithm` | `org.jshard.algorithm` | Core BinomialHash math (slot assignment, shard resolution) |
| `MurmurHash3` | `org.jshard.algorithm` | Hash function used by `Algorithm` |
| `JShardConnectionConfig` | `org.jshard.config` | Per-shard JDBC connection settings (driver, URL, credentials, pool size) |
| `JShardTableConfig` | `org.jshard.config` | Sharded table name + sharding column |
| `JShardDataSourceProvider` | `org.jshard.datasource` | Builder that wires shards + tables into a `JShardDataSource` |
| `JShardDataSource` | `org.jshard.datasource` | The resulting `javax.sql.DataSource`, backed by ShardingSphere |
| `JShardDataSourceRegistry` | `org.jshard.datasource` | HikariCP pool management per shard |
| `JShardRouter` | `org.jshard.router` | Standalone shard-key prediction without a live datasource |

---

## Relationship to JShard

This project forks the original JShard module (part of the J2OS Platform toolkit) — `<!-- add a link to the J2OS-Platform repository here -->`. What's carried over unchanged: `JShardConnectionConfig`, `JShardTableConfig`, `JShardDataSourceProvider`, `JShardDataSource`, `JShardDataSourceRegistry`, and `JShardRouter`. What's replaced: `JShardHashAlgorithm` (ring-based) → `AlgorithmConfig` + `Algorithm` (BinomialHash-based).

This fork was created with the permission of the original JShard maintainer(s).

---

## Reproducing the benchmark

The benchmark harness compiles and runs both the ring algorithm and `AlgorithmConfig` against the same ShardingSphere 5.5.2 dependency set, in isolated JVM processes to avoid cross-run interference:

```bash
# from a directory containing both this project and the original JShard source
javac -cp "<shardingsphere+guava+hikari+slf4j jars>" -d out BenchmarkHarness.java
java -Xmx2g -cp "<same classpath>:out" BenchmarkHarness
```

Numbers above were produced on the machine building this README; expect different absolute values on other hardware, but the same qualitative trend (ring cost scaling with shard count, BinomialHash flat).

---

## Roadmap

- [ ] Persisted slot→shard assignment map, to enable BinomialHash's designed-for partial-reshard capability (currently `shardCount` alone determines routing, which is correct but doesn't yet track slot ownership across topology changes)
- [ ] JMH-based benchmark suite (current numbers use `System.nanoTime` + isolated-process memory sampling, which is good enough to establish the trend but not micro-benchmark-grade)
- [ ] Publish to Maven Central

---

## License

The original J2OS Platform / JShard project does not carry a public license (all rights reserved by default). This fork exists with the explicit permission of the original maintainer(s) for the purpose of contributing an improved sharding algorithm (`AlgorithmConfig`).

No separate license is declared for this fork; its distribution and use remain subject to the original project owner's terms.