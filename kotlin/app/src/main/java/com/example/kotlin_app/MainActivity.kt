package com.example.kotlin_app

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.kotlin_app.model.Product
import com.example.kotlin_app.cache.RedisCacheManager
import ApiService
import com.bumptech.glide.Glide
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private var isLoggedIn = false
    private lateinit var cacheManager: RedisCacheManager

    val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    val client = OkHttpClient.Builder()
        .addInterceptor(logging)
        .build()

    val retrofit = Retrofit.Builder()
        .baseUrl("https://fakestoreapi.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .client(client)
        .build()

    val apiService = retrofit.create(ApiService::class.java)

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialize Redis-style cache
        cacheManager = RedisCacheManager.getInstance(this)

        // Clean up expired cache entries on app start
        cacheManager.cleanupExpiredKeys()

        val usernameField: EditText = findViewById(R.id.usernameField)
        val passwordField: EditText = findViewById(R.id.passwordField)
        val loginButton: Button = findViewById(R.id.loginButton)
        val progressBar: ProgressBar = findViewById(R.id.loadingProgressBar)
        val productListView: ListView = findViewById(R.id.productListView)

        val filterMensClothing: Button = findViewById(R.id.filterMensClothing)
        val filterWomensClothing: Button = findViewById(R.id.filterWomensClothing)
        val filterElectronics: Button = findViewById(R.id.filterElectronics)
        val filterJewelry: Button = findViewById(R.id.filterJewelry)
        val categoryImagesLayout: LinearLayout = findViewById(R.id.categoryImagesLayout)
        val logoutButton: Button = findViewById(R.id.logoutButton)

        val productIdInput: EditText = findViewById(R.id.productIdInput)
        val productValidationMessage: TextView = findViewById(R.id.productValidationMessage)
        val productDetails: TextView = findViewById(R.id.productDetails)

        filterMensClothing.isEnabled = false
        filterWomensClothing.isEnabled = false
        filterElectronics.isEnabled = false
        filterJewelry.isEnabled = false
        categoryImagesLayout.visibility = LinearLayout.GONE
        productListView.visibility = ListView.GONE
        logoutButton.visibility = Button.GONE
        productIdInput.visibility = EditText.GONE

        val fieldValidator = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validateField(usernameField)
                validateField(passwordField)
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        usernameField.addTextChangedListener(fieldValidator)
        passwordField.addTextChangedListener(fieldValidator)

        loginButton.setOnClickListener {
            val username = usernameField.text.toString()
            val password = passwordField.text.toString()

            if (validateField(usernameField) && validateField(passwordField)) {
                progressBar.visibility = ProgressBar.VISIBLE

                Handler().postDelayed({
                    progressBar.visibility = ProgressBar.GONE
                    isLoggedIn = true

                    filterMensClothing.isEnabled = true
                    filterWomensClothing.isEnabled = true
                    filterElectronics.isEnabled = true
                    filterJewelry.isEnabled = true
                    categoryImagesLayout.visibility = LinearLayout.VISIBLE
                    logoutButton.visibility = Button.VISIBLE
                    productIdInput.visibility = EditText.VISIBLE

                    usernameField.visibility = EditText.GONE
                    passwordField.visibility = EditText.GONE
                    loginButton.visibility = Button.GONE

                    // Show cache statistics
                    showCacheStats()
                }, 2000)
            } else {
                Toast.makeText(this, "Please fix the errors", Toast.LENGTH_SHORT).show()
            }
        }

        logoutButton.setOnClickListener {
            isLoggedIn = false
            usernameField.visibility = EditText.VISIBLE
            passwordField.visibility = EditText.VISIBLE
            loginButton.visibility = Button.VISIBLE

            categoryImagesLayout.visibility = LinearLayout.GONE
            productListView.visibility = ListView.GONE
            logoutButton.visibility = Button.GONE
            productIdInput.visibility = EditText.GONE

            productIdInput.text.clear()
            productIdInput.setBackgroundColor(Color.WHITE)
            productValidationMessage.visibility = TextView.GONE
            productDetails.visibility = TextView.GONE

            val imageView: ImageView? = findViewById(R.id.productImage)
            imageView?.setImageDrawable(null)

            Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
        }

        filterMensClothing.setOnClickListener {
            fetchFilteredProducts("men's clothing", productListView, progressBar)
        }
        filterWomensClothing.setOnClickListener {
            fetchFilteredProducts("women's clothing", productListView, progressBar)
        }
        filterElectronics.setOnClickListener {
            fetchFilteredProducts("electronics", productListView, progressBar)
        }
        filterJewelry.setOnClickListener {
            fetchFilteredProducts("jewelery", productListView, progressBar)
        }

        productIdInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val productId = productIdInput.text.toString().toIntOrNull()

                if (productId != null && productId > 0 && productId <= 20) {
                    productIdInput.setBackgroundColor(Color.GREEN)
                    productValidationMessage.visibility = TextView.GONE
                    fetchProductById(productId, productDetails)
                } else {
                    productIdInput.setBackgroundColor(Color.RED)
                    productValidationMessage.text = "Invalid product ID. Enter a number between 1 and 20."
                    productValidationMessage.visibility = TextView.VISIBLE
                    productDetails.visibility = TextView.GONE
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun validateField(field: EditText): Boolean {
        return if (field.text.length >= 3) {
            field.setBackgroundColor(Color.GREEN)
            true
        } else {
            field.setBackgroundColor(Color.RED)
            false
        }
    }

    private fun updateProductList(listView: ListView, products: List<Product>) {
        Log.d("ProductList", "Products fetched: ${products.size}")
        if (products.isNotEmpty()) {
            products.take(5).forEach { product ->
                Log.d("ProductList", "Product: ${product.title}")
            }
            val adapter = ProductAdapter(this, products)
            listView.adapter = adapter
        } else {
            Log.d("ProductList", "No products available")
        }
    }

    fun <T> Call<T>.enqueueWithLogging(
        onSuccess: (T) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        this.enqueue(object : Callback<T> {
            override fun onResponse(call: Call<T>, response: Response<T>) {
                if (response.isSuccessful && response.body() != null) {
                    onSuccess(response.body()!!)
                } else {
                    onError(Exception("Error: ${response.code()} ${response.message()}"))
                }
            }

            override fun onFailure(call: Call<T>, t: Throwable) {
                onError(t)
            }
        })
    }

    private fun fetchFilteredProducts(category: String, listView: ListView, progressBar: ProgressBar) {
        val productDetails: TextView = findViewById(R.id.productDetails)
        val productIdInput: EditText = findViewById(R.id.productIdInput)

        // Try to get from cache first
        val cachedProducts = cacheManager.getCachedProductsByCategory(category)
        if (cachedProducts != null) {
            Log.d("Cache", "Loading products from cache for category: $category")
            Toast.makeText(this, "Loaded from cache ⚡", Toast.LENGTH_SHORT).show()

            productDetails.visibility = TextView.GONE
            productIdInput.setBackgroundColor(Color.WHITE)
            listView.visibility = ListView.VISIBLE
            updateProductList(listView, cachedProducts)
            return
        }

        // If not in cache, fetch from API
        Log.d("Cache", "Cache miss for category: $category, fetching from API")
        progressBar.visibility = ProgressBar.VISIBLE
        apiService.getAllProducts().enqueueWithLogging(
            onSuccess = { products ->
                progressBar.visibility = ProgressBar.GONE

                productDetails.visibility = TextView.GONE
                productIdInput.setBackgroundColor(Color.WHITE)

                val filteredProducts = products.filter { it.category == category }
                if (filteredProducts.isNotEmpty()) {
                    // Cache the filtered products for 1 hour
                    cacheManager.cacheProductsByCategory(category, filteredProducts, TimeUnit.HOURS.toMillis(1))
                    Log.d("Cache", "Cached ${filteredProducts.size} products for category: $category")

                    listView.visibility = ListView.VISIBLE
                    updateProductList(listView, filteredProducts)
                } else {
                    Toast.makeText(this, "No products found in this category", Toast.LENGTH_SHORT).show()
                }
            },
            onError = { error ->
                progressBar.visibility = ProgressBar.GONE
                Log.e("ApiService", "Error: ${error.message}")
                Toast.makeText(this, "Network error", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun fetchProductById(productId: Int, productDetails: TextView) {
        val productListView: ListView = findViewById(R.id.productListView)
        val imageView: ImageView? = findViewById(R.id.productImage)

        productListView.visibility = ListView.GONE

        // Try to get from cache first
        val cachedProduct = cacheManager.getCachedProduct(productId)
        if (cachedProduct != null) {
            Log.d("Cache", "Loading product from cache: ${cachedProduct.title}")
            Toast.makeText(this, "Loaded from cache ⚡", Toast.LENGTH_SHORT).show()
            displayProduct(cachedProduct, productDetails, imageView)
            return
        }

        // If not in cache, fetch from API
        Log.d("Cache", "Cache miss for product ID: $productId, fetching from API")
        apiService.getProductById(productId).enqueueWithLogging(
            onSuccess = { product ->
                Log.d("FetchProductById", "Product fetched: ${product.title}")

                // Cache the product for 1 hour
                cacheManager.cacheProduct(product, TimeUnit.HOURS.toMillis(1))
                Log.d("Cache", "Cached product: ${product.title}")

                displayProduct(product, productDetails, imageView)
            },
            onError = { error ->
                Log.e("ApiService", "Error fetching product: ${error.message}")
                Toast.makeText(this, "Error fetching product details", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun displayProduct(product: Product, productDetails: TextView, imageView: ImageView?) {
        Log.d("FetchProductById", "Image URL: ${product.image}")

        imageView?.let {
            it.visibility = ImageView.VISIBLE
            it.setImageDrawable(null)
            Glide.with(this)
                .load(product.image)
                .into(it)
        } ?: Log.e("FetchProductById", "ImageView is null")

        val productInfo = """
            Product: ${product.title}
            Price: $${product.price}
            Description: ${product.description}
            Category: ${product.category}
            Rating: ${product.rating.rate} (${product.rating.count} reviews)
        """.trimIndent()

        productDetails.text = productInfo
        productDetails.visibility = TextView.VISIBLE
    }

    private fun showCacheStats() {
        val allKeys = cacheManager.keys("*")
        val productKeys = cacheManager.keys("product:")
        val categoryKeys = cacheManager.keys("category:")

        Log.d("Cache", "=== Cache Statistics ===")
        Log.d("Cache", "Total keys: ${allKeys.size}")
        Log.d("Cache", "Cached products: ${productKeys.size}")
        Log.d("Cache", "Cached categories: ${categoryKeys.size}")

        // Show TTL for some keys
        productKeys.take(3).forEach { key ->
            val ttl = cacheManager.ttl(key)
            Log.d("Cache", "Key: $key, TTL: ${ttl}ms (${TimeUnit.MILLISECONDS.toMinutes(ttl)} minutes)")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Optionally clean up expired keys when app closes
        cacheManager.cleanupExpiredKeys()
    }
}