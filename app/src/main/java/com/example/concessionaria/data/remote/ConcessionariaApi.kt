package com.example.concessionaria.data.remote

import com.example.concessionaria.domain.Dashboard
import com.example.concessionaria.domain.Record
import com.example.concessionaria.domain.Session
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

data class ApiEnvelope<T>(val data: T)

interface ConcessionariaApi {
    @POST("auth/login")
    suspend fun login(@Body credentials: Map<String, String>): ApiEnvelope<Session>

    @POST("auth/logout")
    suspend fun logout()

    @GET("dashboard")
    suspend fun dashboard(): ApiEnvelope<Dashboard>

    @GET("{resource}")
    suspend fun list(@Path("resource") resource: String): ApiEnvelope<List<Record>>

    @GET("{resource}/{id}")
    suspend fun get(@Path("resource") resource: String, @Path("id") id: Long): ApiEnvelope<Record>

    @POST("{resource}")
    suspend fun create(@Path("resource") resource: String, @Body record: Record): ApiEnvelope<RecordId>

    @PUT("{resource}/{id}")
    suspend fun update(
        @Path("resource") resource: String,
        @Path("id") id: Long,
        @Body record: Record
    ): ApiEnvelope<UpdateResult>

    @DELETE("{resource}/{id}")
    suspend fun delete(@Path("resource") resource: String, @Path("id") id: Long): ApiEnvelope<DeleteResult>
}

data class RecordId(val id: Long)
data class UpdateResult(val atualizado: Boolean)
data class DeleteResult(val excluido: Boolean)
