import com.example.kotlin_app.model.Product
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path

public interface ApiService {

    @GET("products")
    fun getAllProducts(): Call<List<Product>>
    @GET("products/{id}")
    fun getProductById(@Path("id") productId: Int): Call<Product>

}

