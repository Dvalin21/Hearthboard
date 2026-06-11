package com.openlight.cal.data.mealie

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Minimal Mealie API client.
 * Fetches recipes and meal plan data from a self-hosted Mealie instance.
 *
 * Mealie API docs: https://mealie.dev/docs/api/
 * No tracking, no analytics, no third-party SDKs.
 */
class MealieApi(
    private val serverUrl: String,
    private val apiToken: String
) {
    companion object {
        private const val TAG = "MealieApi"
    }

    data class MealieRecipe(
        val slug: String,
        val name: String,
        val description: String,
        val imageUrl: String?,
        val category: String
    )

    data class MealieRecipeDetail(
        val slug: String,
        val name: String,
        val description: String,
        val recipeYield: String,
        val totalTime: String,
        val ingredients: List<String>,
        val instructions: List<String>
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("Authorization", "Bearer $apiToken")
                .header("User-Agent", "HearthBoard/1.0")
                .build()
            chain.proceed(req)
        }
        .build()

    /** Fetch all recipes from Mealie. */
    fun getRecipes(): List<MealieRecipe> {
        return try {
            val url = "${serverUrl.trimEnd('/')}/api/recipes"
            val req = Request.Builder().url(url).get().build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) {
                Log.e(TAG, "getRecipes failed: ${resp.code}")
                return emptyList()
            }
            val body = resp.body?.string() ?: return emptyList()
            val json = JSONObject(body)
            val items = json.optJSONArray("items") ?: json.optJSONArray("results") ?: JSONArray()
            (0 until items.length()).mapNotNull { i ->
                val r = items.getJSONObject(i)
                MealieRecipe(
                    slug = r.optString("slug"),
                    name = r.optString("name"),
                    description = r.optString("description"),
                    imageUrl = r.optString("image"),
                    category = r.optString("category")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "getRecipes exception: ${e.message}")
            emptyList()
        }
    }

    /** Fetch recipe details including ingredients and instructions. */
    fun getRecipeDetail(slug: String): MealieRecipeDetail? {
        return try {
            val url = "${serverUrl.trimEnd('/')}/api/recipes/$slug"
            val req = Request.Builder().url(url).get().build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null
            val r = JSONObject(body)

            val ingredients = JSONArray(r.optString("recipeIngredient", "[]"))
                .let { arr -> (0 until arr.length()).map { arr.getString(it) } }

            val instructions = JSONArray(r.optString("recipeInstructions", "[]"))
                .let { arr -> (0 until arr.length()).mapNotNull { i ->
                    val instr = arr.getJSONObject(i)
                    instr.optString("text")
                } }

            MealieRecipeDetail(
                slug = slug,
                name = r.optString("name"),
                description = r.optString("description"),
                recipeYield = r.optString("recipeYield"),
                totalTime = r.optString("totalTime"),
                ingredients = ingredients,
                instructions = instructions
            )
        } catch (e: Exception) {
            Log.e(TAG, "getRecipeDetail exception: ${e.message}")
            null
        }
    }

    /**
     * Create a new recipe on the Mealie server.
     * Returns the new recipe slug, or null on failure.
     */
    fun createRecipe(
        name: String,
        description: String,
        ingredients: List<String>,
        instructions: List<String>,
        recipeYield: String,
        totalTime: String
    ): String? {
        return try {
            val body = JSONObject().apply {
                put("name", name)
                put("description", description)
                put("recipeYield", recipeYield)
                put("totalTime", totalTime)
                put("recipeIngredient", JSONArray(ingredients))
                put("recipeInstructions", JSONArray(instructions.map { i ->
                    JSONObject().put("text", i)
                }))
            }
            val url = "${serverUrl.trimEnd('/')}/api/recipes"
            val req = Request.Builder()
                .url(url)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) {
                Log.e(TAG, "createRecipe failed: ${resp.code}")
                return null
            }
            val json = JSONObject(resp.body?.string() ?: return null)
            json.optString("slug").ifEmpty { null }
        } catch (e: Exception) {
            Log.e(TAG, "createRecipe exception: ${e.message}")
            null
        }
    }

    /** Simple health check — verifies the server is reachable and token works. */
    fun checkConnection(): Boolean {
        return try {
            val url = "${serverUrl.trimEnd('/')}/api/recipes?perPage=1"
            val req = Request.Builder().url(url).get().build()
            val resp = client.newCall(req).execute()
            resp.isSuccessful
        } catch (_: Exception) {
            false
        }
    }
}