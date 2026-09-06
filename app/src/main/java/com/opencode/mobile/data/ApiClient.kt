package com.opencode.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jakewharton.retrofit.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

private val Context.dataStore by preferencesDataStore(name = "opencode_prefs")

data class ConnectionSettings(
    val baseUrl: String = "http://192.168.1.10:4096",
    val username: String = "opencode",
    val password: String = ""
)

class PreferencesRepository(private val context: Context) {
    private val baseUrlKey = stringPreferencesKey("base_url")
    private val usernameKey = stringPreferencesKey("username")
    private val passwordKey = stringPreferencesKey("password")

    val settings: Flow<ConnectionSettings> = context.dataStore.data.map { p ->
        ConnectionSettings(
            baseUrl = p[baseUrlKey] ?: "http://192.168.1.10:4096",
            username = p[usernameKey] ?: "opencode",
            password = p[passwordKey] ?: ""
        )
    }

    suspend fun save(s: ConnectionSettings) {
        context.dataStore.edit { p ->
            p[baseUrlKey] = s.baseUrl.trim().trimEnd('/')
            p[usernameKey] = s.username
            p[passwordKey] = s.password
        }
    }
}

object ApiClient {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

    fun create(baseUrl: String, username: String, password: String): OpencodeApi {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                if (password.isNotBlank()) {
                    req.header("Authorization", Credentials.basic(username.ifBlank { "opencode" }, password))
                }
                req.header("X-Client", "opencode-mobile-android")
                chain.proceed(req.build())
            }
            .build()

        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpencodeApi::class.java)
    }

    fun sseClient(username: String, password: String): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                if (password.isNotBlank()) {
                    req.header("Authorization", Credentials.basic(username.ifBlank { "opencode" }, password))
                }
                req.header("Accept", "text/event-stream")
                chain.proceed(req.build())
            }
            .build()
    }
}
