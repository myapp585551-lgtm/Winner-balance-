package com.example.data.remote

import com.squareup.moshi.JsonClass
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

@JsonClass(generateAdapter = true)
data class RemoteAdminAuthRequest(
    val email: String,
    val password: String,
)

@JsonClass(generateAdapter = true)
data class RemoteAdminAuthResponse(
    val email: String,
    val token: String,
    val authenticatedAtEpochMs: Long,
)

@JsonClass(generateAdapter = true)
data class RemotePublicUserDto(
    val publicId: String,
    val displayName: String,
    val balancePaisa: Long,
    val reservedPaisa: Long,
    val isActive: Boolean,
    val updatedAtEpochMs: Long,
    val avatarUri: String? = null,
)

@JsonClass(generateAdapter = true)
data class RemoteDepositCreateDto(
    val amountPaisa: Long,
    val senderBkashNumber: String,
    val transactionId: String,
)

@JsonClass(generateAdapter = true)
data class RemoteWithdrawalCreateDto(
    val amountPaisa: Long,
    val bankName: String,
    val accountHolderName: String,
    val bankAccountNumber: String,
)

@JsonClass(generateAdapter = true)
data class RemoteStatusUpdateDto(
    val status: String,
)

@JsonClass(generateAdapter = true)
data class RemoteMediaUploadResponse(
    val slot: String,
    val url: String,
)

/**
 * Production REST API client contract matching the companion backend server (/backend/server.js)
 * when BuildConfig.REMOTE_API_BASE_URL is configured to a deployed HTTPS domain.
 */
interface WinnerApiService {
    @POST("api/admin/auth/login")
    suspend fun adminLogin(@Body request: RemoteAdminAuthRequest): RemoteAdminAuthResponse

    @GET("api/public/u/{publicId}")
    suspend fun getPublicUserPage(@Path("publicId") publicId: String): RemotePublicUserDto

    @POST("api/public/u/{publicId}/deposits")
    suspend fun submitDeposit(
        @Path("publicId") publicId: String,
        @Body request: RemoteDepositCreateDto,
    )

    @POST("api/public/u/{publicId}/withdrawals")
    suspend fun submitWithdrawal(
        @Path("publicId") publicId: String,
        @Body request: RemoteWithdrawalCreateDto,
    )

    @PATCH("api/admin/deposits/{requestId}/status")
    suspend fun updateDepositStatus(
        @Header("Authorization") bearerToken: String,
        @Path("requestId") requestId: String,
        @Body body: RemoteStatusUpdateDto,
    )

    @PATCH("api/admin/withdrawals/{requestId}/status")
    suspend fun updateWithdrawalStatus(
        @Header("Authorization") bearerToken: String,
        @Path("requestId") requestId: String,
        @Body body: RemoteStatusUpdateDto,
    )

    @Multipart
    @POST("api/admin/media/{slot}")
    suspend fun uploadMediaBanner(
        @Header("Authorization") bearerToken: String,
        @Path("slot") slot: String,
        @Part image: MultipartBody.Part,
    ): RemoteMediaUploadResponse

    @Multipart
    @POST("api/admin/users/{userId}/avatar")
    suspend fun uploadUserAvatar(
        @Header("Authorization") bearerToken: String,
        @Path("userId") userId: String,
        @Part image: MultipartBody.Part,
    ): RemoteMediaUploadResponse

    @DELETE("api/admin/users/{userId}")
    suspend fun deleteUser(
        @Header("Authorization") bearerToken: String,
        @Path("userId") userId: String,
    )
}
