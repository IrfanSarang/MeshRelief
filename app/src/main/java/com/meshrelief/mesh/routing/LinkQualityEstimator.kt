package com.meshrelief.mesh.routing

import android.util.Log
import kotlinx.coroutines.*
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LinkQualityEstimator @Inject constructor() {

    private val TAG = "LinkQualityEstimator"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // deviceId -> quality score (0–100)
    private val qualityMap = mutableMapOf<String, Int>()

    fun getQuality(deviceId: String): Int = qualityMap[deviceId] ?: 0

    fun measureRtt(deviceId: String, ipAddress: String, onResult: (Int) -> Unit) {
        scope.launch {
            val score = runCatching {
                val samples = 4
                val rtts = (1..samples).mapNotNull {
                    val start = System.currentTimeMillis()
                    val reachable = InetAddress.getByName(ipAddress)
                        .isReachable(1000)
                    val rtt = System.currentTimeMillis() - start
                    if (reachable) rtt else null
                }
                if (rtts.isEmpty()) return@runCatching 0
                val avgRtt = rtts.average()
                val loss = (samples - rtts.size).toDouble() / samples
                rttToScore(avgRtt, loss)
            }.getOrElse {
                Log.w(TAG, "RTT measurement failed for $ipAddress: ${it.message}")
                0
            }
            qualityMap[deviceId] = score
            Log.d(TAG, "Link quality for $deviceId ($ipAddress): $score/100")
            withContext(Dispatchers.Main) { onResult(score) }
        }
    }

    /**
     * Converts average RTT (ms) and packet loss ratio to a 0–100 quality score.
     * RTT bands: <50ms=excellent, <150ms=good, <300ms=fair, >=300ms=poor
     * Each 25% packet loss deducts 25 points.
     */
    private fun rttToScore(avgRttMs: Double, lossRatio: Double): Int {
        val rttScore = when {
            avgRttMs < 50  -> 100
            avgRttMs < 150 -> 80
            avgRttMs < 300 -> 50
            else           -> 20
        }
        val penaltyFromLoss = (lossRatio * 100).toInt()
        return (rttScore - penaltyFromLoss).coerceIn(0, 100)
    }

    fun clear() {
        qualityMap.clear()
    }

    fun shutdown() {
        scope.cancel()
    }
}