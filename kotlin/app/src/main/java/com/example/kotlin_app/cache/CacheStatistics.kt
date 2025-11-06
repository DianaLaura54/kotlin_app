package com.example.kotlin_app.cache

import android.content.Context
import android.util.Log
import java.util.concurrent.TimeUnit

/**
 * Utility class for monitoring and analyzing cache performance
 */
class CacheStatistics(private val context: Context) {

    private val cacheManager = RedisCacheManager.getInstance(context)
    private var cacheHits = 0
    private var cacheMisses = 0
    private var totalRequests = 0

    data class CacheStats(
        val totalKeys: Int,
        val productKeys: Int,
        val categoryKeys: Int,
        val expiredKeys: Int,
        val cacheHitRate: Double,
        val totalSize: Long
    )

    /**
     * Record a cache hit
     */
    fun recordHit() {
        cacheHits++
        totalRequests++
    }

    /**
     * Record a cache miss
     */
    fun recordMiss() {
        cacheMisses++
        totalRequests++
    }

    /**
     * Get current cache statistics
     */
    fun getStats(): CacheStats {
        val allKeys = cacheManager.keys("*")
        val productKeys = cacheManager.keys("product:")
        val categoryKeys = cacheManager.keys("category:")

        // Count expired keys
        var expiredCount = 0
        allKeys.forEach { key ->
            val ttl = cacheManager.ttl(key)
            if (ttl == -2L) { // -2 means expired
                expiredCount++
            }
        }

        val hitRate = if (totalRequests > 0) {
            (cacheHits.toDouble() / totalRequests.toDouble()) * 100
        } else {
            0.0
        }

        return CacheStats(
            totalKeys = allKeys.size,
            productKeys = productKeys.size,
            categoryKeys = categoryKeys.size,
            expiredKeys = expiredCount,
            cacheHitRate = hitRate,
            totalSize = estimateCacheSize()
        )
    }

    /**
     * Print detailed cache statistics to logcat
     */
    fun printDetailedStats() {
        val stats = getStats()

        Log.d("CacheStats", "═══════════════════════════════════")
        Log.d("CacheStats", "       CACHE STATISTICS")
        Log.d("CacheStats", "═══════════════════════════════════")
        Log.d("CacheStats", "Total Keys: ${stats.totalKeys}")
        Log.d("CacheStats", "Product Keys: ${stats.productKeys}")
        Log.d("CacheStats", "Category Keys: ${stats.categoryKeys}")
        Log.d("CacheStats", "Expired Keys: ${stats.expiredKeys}")
        Log.d("CacheStats", "───────────────────────────────────")
        Log.d("CacheStats", "Total Requests: $totalRequests")
        Log.d("CacheStats", "Cache Hits: $cacheHits")
        Log.d("CacheStats", "Cache Misses: $cacheMisses")
        Log.d("CacheStats", "Hit Rate: ${"%.2f".format(stats.cacheHitRate)}%")
        Log.d("CacheStats", "───────────────────────────────────")
        Log.d("CacheStats", "Estimated Size: ${formatBytes(stats.totalSize)}")
        Log.d("CacheStats", "═══════════════════════════════════")

        printTopKeys(5)
    }

    /**
     * Print top N keys with their TTL
     */
    private fun printTopKeys(limit: Int) {
        val allKeys = cacheManager.keys("*")
        Log.d("CacheStats", "")
        Log.d("CacheStats", "Top $limit Cached Keys:")
        Log.d("CacheStats", "───────────────────────────────────")

        allKeys.take(limit).forEach { key ->
            val ttl = cacheManager.ttl(key)
            val ttlInfo = when {
                ttl == -1L -> "No expiry"
                ttl == -2L -> "EXPIRED"
                else -> "${TimeUnit.MILLISECONDS.toMinutes(ttl)} min remaining"
            }
            Log.d("CacheStats", "• $key → $ttlInfo")
        }
    }

    /**
     * Analyze cache efficiency and provide recommendations
     */
    fun analyzeAndRecommend(): List<String> {
        val recommendations = mutableListOf<String>()
        val stats = getStats()

        // Check hit rate
        if (stats.cacheHitRate < 30 && totalRequests > 10) {
            recommendations.add("⚠️ Low cache hit rate (${stats.cacheHitRate}%). Consider increasing TTL or pre-caching popular items.")
        } else if (stats.cacheHitRate > 70) {
            recommendations.add("✅ Excellent cache hit rate (${stats.cacheHitRate}%)!")
        }

        // Check expired keys
        if (stats.expiredKeys > stats.totalKeys * 0.3) {
            recommendations.add("🧹 ${stats.expiredKeys} expired keys found. Run cleanupExpiredKeys() to free memory.")
        }

        // Check cache size
        if (stats.totalSize > 500_000) { // 500KB
            recommendations.add("📦 Cache size is ${formatBytes(stats.totalSize)}. Consider cleanup or shorter TTL.")
        }

        // Check if cache is empty
        if (stats.totalKeys == 0 && totalRequests > 0) {
            recommendations.add("❌ Cache is empty despite $totalRequests requests. Verify caching logic.")
        }

        // Check product/category balance
        if (stats.productKeys > 50) {
            recommendations.add("📊 ${stats.productKeys} individual products cached. Consider category-based caching.")
        }

        return recommendations
    }

    /**
     * Get cache health score (0-100)
     */
    fun getHealthScore(): Int {
        val stats = getStats()
        var score = 100

        // Deduct points for low hit rate
        if (stats.cacheHitRate < 50 && totalRequests > 10) {
            score -= ((50 - stats.cacheHitRate).toInt())
        }

        // Deduct points for expired keys
        val expiredRatio = if (stats.totalKeys > 0) {
            (stats.expiredKeys.toDouble() / stats.totalKeys.toDouble()) * 100
        } else 0.0
        score -= (expiredRatio / 2).toInt()

        // Deduct points for large cache size
        if (stats.totalSize > 1_000_000) { // 1MB
            score -= 20
        } else if (stats.totalSize > 500_000) { // 500KB
            score -= 10
        }

        return maxOf(0, minOf(100, score))
    }

    /**
     * Reset statistics counters
     */
    fun resetCounters() {
        cacheHits = 0
        cacheMisses = 0
        totalRequests = 0
    }

    /**
     * Estimate cache size in bytes
     */
    private fun estimateCacheSize(): Long {
        val allKeys = cacheManager.keys("*")
        var totalSize = 0L

        allKeys.forEach { key ->
            val value = cacheManager.get(key)
            if (value != null) {
                // Rough estimate: key size + value size
                totalSize += key.length * 2 // UTF-16
                totalSize += value.length * 2 // UTF-16
            }
        }

        return totalSize
    }

    /**
     * Format bytes to human-readable string
     */
    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${"%.2f".format(bytes / 1024.0)} KB"
            else -> "${"%.2f".format(bytes / (1024.0 * 1024.0))} MB"
        }
    }

    /**
     * Export statistics as JSON string
     */
    fun exportAsJson(): String {
        val stats = getStats()
        return """
            {
                "totalKeys": ${stats.totalKeys},
                "productKeys": ${stats.productKeys},
                "categoryKeys": ${stats.categoryKeys},
                "expiredKeys": ${stats.expiredKeys},
                "cacheHitRate": ${stats.cacheHitRate},
                "totalSize": ${stats.totalSize},
                "cacheHits": $cacheHits,
                "cacheMisses": $cacheMisses,
                "totalRequests": $totalRequests,
                "healthScore": ${getHealthScore()}
            }
        """.trimIndent()
    }
}