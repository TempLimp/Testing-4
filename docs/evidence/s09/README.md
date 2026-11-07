# S09 evidence: FlowNeuro against upstream

Both comparisons ran on 2026-11-08 on the Windows dev machine (JDK 21.0.13, Kotlin 2.4.21), upstream at 77ae9d9.

## Benchmarks: upstream's code and the port's, same fixtures

Upstream's NeuroBenchmarkTest and MusicBenchmarkTest build their fixtures in code (`NeuroBenchmark.multiInterestUniverse`,
`MusicBenchmark.run`, seeded `java.util.Random`); nothing they read is gitignored. Upstream's numbers were measured,
not assumed: `reference-harness/` compiles upstream's unmodified sources on a plain JVM (the 42 files of
`data/recommendation` the two benchmarks reach, upstream's Snowball Java, `data/stats/LedgerTime.kt` and
`MonthlyLedgerStore.kt`, and upstream's `NeuroBenchmark.kt`, `NeuroEval.kt`, `MusicBenchmark.kt`) against the port's
jvm jar with the port's own recommendation, Snowball and ledger classes removed. The stubs under `android/` and
`org-json/` and the bridges only let the Android-bound stores compile; no benchmark reaches them. The same `Runner.kt`
then ran against the port's jar and the port's commonTest copies of the benchmark files.

Each side ran 200 times, then 1,000 times more. Upstream's discovery shuffles without a seed (its own reproducibility
test allows 0.15 of drift), so the serving numbers vary run to run on both sides and are compared as distributions:
`benchmark-comparison-1000.txt` (the 1,000-run raw files, about 1 MB each, are not committed) and
`benchmark-comparison-200.txt` with its raw runs (`benchmark-upstream-200-runs.txt`, `benchmark-port-200-runs.txt`).

- **Music benchmark: identical on every run on both sides.** Quick picks hit rate 1.000, discovery 0.100, longest
  artist run 2; discover hit rate 1.000, discovery 0.800, longest run 2; the cold brain passes through unchanged;
  0 blocked leaks.
- **Neuro benchmark, deterministic numbers: identical.** Group coverage 1.000, cumulative coverage 1.000, weak-group
  service 1.000 (default seed, seed 7, and the seen-gate-off counterfactual); discovery 6 sessions, every one
  non-empty, cumulative coverage 1.000, session overlap 0.000.
- **Neuro benchmark, sampled numbers: the same distributions.** nDCG, ILD, seen and served repeat rates, unique ratio
  and concentration, 18 metrics: over 1,000 runs a side every two-sample Kolmogorov-Smirnov distance is under the
  5 % critical value of 0.061 (largest 0.051) and every permutation test of the means is above 0.12. Means agree to
  the third decimal (serving nDCG 0.9794 upstream, 0.9791 port; seen repeat 0.0672 and 0.0675, both reaching 0.100
  at most).

The floors upstream asserts (nDCG >= 0.55, ILD >= 0.20, coverage >= 0.85 / 0.95, weak service >= 0.80, seen repeat
<= 0.10, served repeat <= 0.60) held on all 1,200 runs of each side.

Not compared: the benchmarks on the iOS simulator against upstream (only the floors run there, in CI), and
upstream on Android itself (no Android SDK here; the JVM is what upstream's own unit tests run on).

## Snowball: upstream's Java against the port's Kotlin

`reference-harness/SnowballCompare.java` loads upstream's compiled Java stemmers and the port's Kotlin ones in two
class loaders and stems every word of upstream's `resources/neuro/freq` lists (37,529 distinct words) with each of the
seven stemmers, and each stem again: `snowball-comparison.txt`, 262,703 stems, 0 differ.

## To rerun

1. `gradlew :shared:jvmJar`, and list `:shared`'s `jvmRuntimeClasspath`.
2. Copy the port's jar with `io/github/aedev/flow/data/recommendation/**`, `org/tartarus/**`,
   `data/profile/ProfileFiles*` and `data/stats/{LedgerTime,LedgerMoment,MonthlyLedgerStore,StoredLedger,LoadedLedger,LedgerFileSerializer}*`
   left out.
3. A Kotlin/JVM 2.4.21 project (with the serialization plugin) on that jar and classpath, holding: upstream's 42 files
   (the closure of `NeuroBenchmark.kt`, `NeuroEval.kt` and `MusicBenchmark.kt` over `data/recommendation`), its
   `org/tartarus` Java, `resources/neuro`, `data/stats/LedgerTime.kt` and `MonthlyLedgerStore.kt`, those three
   benchmark files, an `R` object with the `R.string` ids they name, and this folder's `android/`, `org-json/`,
   `Bridges*.kt` and `Runner.kt`. `run --args="1000 summary.txt"`.
4. For the port's side, the same project on the whole jar, with `-Xfriend-paths` on it, holding the port's commonTest
   `NeuroBenchmark.kt`, `NeuroEval.kt`, `MusicBenchmark.kt`, `testing/JavaRandom.kt`, `testing/Format.kt` and `Runner.kt`.
5. `java SnowballCompare.java <upstream's compiled stemmer classes> <port jar;kotlin-stdlib> <upstream resources/neuro/freq>`.
