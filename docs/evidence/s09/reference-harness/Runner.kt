import io.github.aedev.flow.data.recommendation.eval.NeuroBenchmark
import io.github.aedev.flow.data.recommendation.music.eval.MusicBenchmark
import java.io.File

// Runs the two benchmarks the way upstream's NeuroBenchmarkTest and MusicBenchmarkTest do, many times, and
// writes every summary number, so two builds (upstream's sources, the port's) can be compared number for number.
fun main(args: Array<String>) {
    val runs = args.getOrNull(0)?.toInt() ?: 20
    val out = File(args.getOrNull(1) ?: "summary.txt")
    val lines = ArrayList<String>()

    fun summary(
        label: String,
        s: NeuroBenchmark.ServingSummary,
    ) = "$label ndcg=${s.meanNdcg} ild=${s.meanIld} groupCov=${s.meanGroupCoverage} cumCov=${s.cumulativeGroupCoverage} " +
        "weak=${s.weakGroupServiceRate} seenRep=${s.meanSeenRepeatRate} servedRep=${s.meanServedRepeatRate} " +
        "unique=${s.uniqueServedRatio} conc=${s.meanConcentration}"

    repeat(runs) { run ->
        lines += summary("run$run serving", NeuroBenchmark.simulateServing().summary)
        lines += summary("run$run ungated", NeuroBenchmark.simulateServing(seenGateEnabled = false).summary)
        lines += summary("run$run seed7", NeuroBenchmark.simulateServing(seed = 7L).summary)
        val discovery = NeuroBenchmark.discoveryCoverage()
        lines += "run$run discovery sessions=${discovery.sessions.size} nonEmpty=${discovery.sessions.all { it.isNotEmpty() }} " +
            "cumCov=${discovery.cumulativeGroupCoverage} overlap=${discovery.meanSessionOverlap} " +
            "perSession=${discovery.perSessionGroupCoverage}"
        val music = MusicBenchmark.run(1_700_000_000_000L)
        lines += "run$run music ${music.quickPicks} ${music.discover} cold=${music.coldPassthroughHolds} leaks=${music.blockedLeaks}"
    }
    out.writeText(lines.joinToString("\n", postfix = "\n"))
    println(NeuroBenchmark.renderReport("ref", NeuroBenchmark.simulateServing(), NeuroBenchmark.discoveryCoverage()))
    println(MusicBenchmark.renderReport(MusicBenchmark.run(1_700_000_000_000L)))
}
