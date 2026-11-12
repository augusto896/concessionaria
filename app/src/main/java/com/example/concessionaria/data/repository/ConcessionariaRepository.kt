package com.example.concessionaria.data.repository

import com.example.concessionaria.BuildConfig
import com.example.concessionaria.data.remote.ConcessionariaApi
import com.example.concessionaria.domain.Dashboard
import com.example.concessionaria.domain.Record
import com.example.concessionaria.domain.Session
import com.example.concessionaria.preferences.UserPreferences
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException

class ConcessionariaRepository(
    private val api: ConcessionariaApi,
    private val preferences: UserPreferences
) {
    suspend fun login(identity: String, password: String): Session =
        request { api.login(mapOf("usuario" to identity, "senha" to password)).data }

    suspend fun logout() {
        request { api.logout() }
    }

    suspend fun dashboard(): Dashboard = request { api.dashboard().data }

    suspend fun list(resource: String): List<Record> = request { api.list(resource).data }

    suspend fun save(resource: String, id: Long?, record: Record) {
        request {
            if (id == null) api.create(resource, record) else api.update(resource, id, record)
        }
    }

    suspend fun delete(resource: String, id: Long) {
        request { api.delete(resource, id) }
    }

    private suspend fun <T> request(block: suspend () -> T): T = try {
        block()
    } catch (error: HttpException) {
        val message = try {
            val body = error.response()?.errorBody()?.string()
            Gson().fromJson(body, JsonObject::class.java)
                ?.getAsJsonObject("error")
                ?.get("message")?.asString
        } catch (_: Exception) {
            null
        }
        throw IllegalStateException(message ?: "Falha na comunicação com a API (${error.code()}).")
    } catch (error: IOException) {
        throw IllegalStateException("Não foi possível conectar à API. Verifique o WampServer e o endereço configurado.")
    }

    companion object {
        fun create(preferences: UserPreferences): ConcessionariaRepository {
            val client = OkHttpClient.Builder()
                .addInterceptor(SessionInterceptor(preferences))
                .build()
            val retrofit = Retrofit.Builder()
                .baseUrl(BuildConfig.API_BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            return ConcessionariaRepository(retrofit.create(ConcessionariaApi::class.java), preferences)
        }
    }
}

private class SessionInterceptor(private val preferences: UserPreferences) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { preferences.settings.first().token }
        val request = chain.request().newBuilder().apply {
            if (token.isNotBlank()) header("Authorization", "Bearer $token")
        }.build()
        return chain.proceed(request)
    }
}
