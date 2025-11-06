package com.example.kotlin_app.cache

import android.content.Context
import android.content.SharedPreferences
import com.example.kotlin_app.model.Product
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.concurrent.TimeUnit

/**
 * Redis-style cache manager for Android
 * Uses SharedPreferences as the underlying storage mechanism
 */
class RedisCacheManager private constructor(context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("redis_cache", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        @Volatile
        private var INSTANCE: RedisCacheManager? = null

        fun getInstance(context: Context): RedisCacheManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RedisCacheManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        private const val EXPIRY_SUFFIX = "_expiry"
    }

    /**
     * Set a key-value pair with optional TTL (Time To Live) in milliseconds
     */
    fun set(key: String, value: String, ttlMillis: Long? = null) {
        val editor = sharedPreferences.edit()
        editor.putString(key, value)

        if (ttlMillis != null) {
            val expiryTime = System.currentTimeMillis() + ttlMillis
            editor.putLong(key + EXPIRY_SUFFIX, expiryTime)
        } else {
            editor.remove(key + EXPIRY_SUFFIX)
        }

        editor.apply()
    }

    /**
     * Get a value by key, returns null if expired or not found
     */
    fun get(key: String): String? {
        if (isExpired(key)) {
            delete(key)
            return null
        }
        return sharedPreferences.getString(key, null)
    }

    /**
     * Cache a single product
     */
    fun cacheProduct(product: Product, ttlMillis: Long = TimeUnit.HOURS.toMillis(1)) {
        val key = "product:${product.id}"
        val json = gson.toJson(product)
        set(key, json, ttlMillis)
    }

    /**
     * Get a cached product by ID
     */
    fun getCachedProduct(productId: Int): Product? {
        val key = "product:$productId"
        val json = get(key) ?: return null
        return try {
            gson.fromJson(json, Product::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Cache a list of products by category
     */
    fun cacheProductsByCategory(category: String, products: List<Product>, ttlMillis: Long = TimeUnit.HOURS.toMillis(1)) {
        val key = "category:$category"
        val json = gson.toJson(products)
        set(key, json, ttlMillis)
    }

    /**
     * Get cached products by category
     */
    fun getCachedProductsByCategory(category: String): List<Product>? {
        val key = "category:$category"
        val json = get(key) ?: return null
        return try {
            val type = object : TypeToken<List<Product>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Cache all products
     */
    fun cacheAllProducts(products: List<Product>, ttlMillis: Long = TimeUnit.HOURS.toMillis(1)) {
        val key = "products:all"
        val json = gson.toJson(products)
        set(key, json, ttlMillis)
    }

    /**
     * Get all cached products
     */
    fun getAllCachedProducts(): List<Product>? {
        val key = "products:all"
        val json = get(key) ?: return null
        return try {
            val type = object : TypeToken<List<Product>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Check if a key exists and is not expired
     */
    fun exists(key: String): Boolean {
        return get(key) != null
    }

    /**
     * Delete a key
     */
    fun delete(key: String) {
        val editor = sharedPreferences.edit()
        editor.remove(key)
        editor.remove(key + EXPIRY_SUFFIX)
        editor.apply()
    }

    /**
     * Delete multiple keys
     */
    fun delete(vararg keys: String) {
        val editor = sharedPreferences.edit()
        keys.forEach { key ->
            editor.remove(key)
            editor.remove(key + EXPIRY_SUFFIX)
        }
        editor.apply()
    }

    /**
     * Clear all cached data
     */
    fun flushAll() {
        sharedPreferences.edit().clear().apply()
    }

    /**
     * Get all keys matching a pattern (simple prefix matching)
     */
    fun keys(pattern: String): List<String> {
        val allKeys = sharedPreferences.all.keys
        val prefix = pattern.replace("*", "")
        return allKeys.filter { it.startsWith(prefix) && !it.endsWith(EXPIRY_SUFFIX) }
    }

    /**
     * Set expiry time for an existing key
     */
    fun expire(key: String, ttlMillis: Long): Boolean {
        if (!sharedPreferences.contains(key)) {
            return false
        }
        val expiryTime = System.currentTimeMillis() + ttlMillis
        sharedPreferences.edit().putLong(key + EXPIRY_SUFFIX, expiryTime).apply()
        return true
    }

    /**
     * Get remaining TTL for a key in milliseconds
     */
    fun ttl(key: String): Long {
        val expiryTime = sharedPreferences.getLong(key + EXPIRY_SUFFIX, -1)
        if (expiryTime == -1L) {
            return -1 // No expiry set
        }
        val remaining = expiryTime - System.currentTimeMillis()
        return if (remaining > 0) remaining else -2 // -2 means expired
    }

    /**
     * Increment a numeric value
     */
    fun increment(key: String, delta: Long = 1): Long {
        val currentValue = get(key)?.toLongOrNull() ?: 0
        val newValue = currentValue + delta
        set(key, newValue.toString())
        return newValue
    }

    /**
     * Decrement a numeric value
     */
    fun decrement(key: String, delta: Long = 1): Long {
        return increment(key, -delta)
    }

    /**
     * Check if a key has expired
     */
    private fun isExpired(key: String): Boolean {
        val expiryTime = sharedPreferences.getLong(key + EXPIRY_SUFFIX, -1)
        if (expiryTime == -1L) {
            return false // No expiry set
        }
        return System.currentTimeMillis() > expiryTime
    }

    /**
     * Clean up all expired keys
     */
    fun cleanupExpiredKeys() {
        val allKeys = sharedPreferences.all.keys.filter { !it.endsWith(EXPIRY_SUFFIX) }
        val expiredKeys = allKeys.filter { isExpired(it) }
        delete(*expiredKeys.toTypedArray())
    }
}