package com.example

import android.content.Intent
import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LiveDeploymentAndFirestoreDiagnosticTest {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    @Test
    fun verifyIntentQueryRouting_webServerRefreshSafety_andLiveFirestoreUser() {
        val exactUserUrl = "https://ais-pre-clxmtq4b6kwszyihq4tu6n-190436024907.asia-southeast1.run.app/?u=WB7x9Km2Pq4Rt8Vz3N"
        val bareQueryUrl = "https://ais-pre-clxmtq4b6kwszyihq4tu6n-190436024907.asia-southeast1.run.app?u=WB7x9Km2Pq4Rt8Vz3N"
        val pathRouteUrl = "https://ais-pre-clxmtq4b6kwszyihq4tu6n-190436024907.asia-southeast1.run.app/u/WB7x9Km2Pq4Rt8Vz3N"

        // 1. Verify Android MainActivity extracts user ID from query parameter "u" and /u/ routes
        val activity = MainActivity()
        assertEquals(
            "WB7x9Km2Pq4Rt8Vz3N",
            activity.extractPublicIdFromIntent(Intent(Intent.ACTION_VIEW, Uri.parse(exactUserUrl)))
        )
        assertEquals(
            "WB7x9Km2Pq4Rt8Vz3N",
            activity.extractPublicIdFromIntent(Intent(Intent.ACTION_VIEW, Uri.parse(bareQueryUrl)))
        )
        assertEquals(
            "WB7x9Km2Pq4Rt8Vz3N",
            activity.extractPublicIdFromIntent(Intent(Intent.ACTION_VIEW, Uri.parse(pathRouteUrl)))
        )

        // 2. Verify LIVE production Firebase Firestore returns WB7x9Km2Pq4Rt8Vz3N without admin login
        val projectId = "gen-lang-client-0530252854"
        val dbId = "ai-studio-android-winnerba-3979b9d3-2b97-4683-a1fe-d975aa7e02e4"
        val firestoreUserUrl =
            "https://firestore.googleapis.com/v1/projects/$projectId/databases/$dbId/documents/public_users/WB7x9Km2Pq4Rt8Vz3N"

        client.newCall(Request.Builder().url(firestoreUserUrl).get().build()).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            println("VERIFY_FIRESTORE_USER_STATUS=${resp.code}")
            assertEquals(200, resp.code)
            assertTrue(body.contains("WB7x9Km2Pq4Rt8Vz3N"))
            assertTrue(body.contains("Tanvir Rahman"))
            assertTrue(body.contains("125000"))
        }

        // 3. Verify local web portal server (port 3000) AND container reverse proxy (port 8080)
        // serve ?u=WB7x9Km2Pq4Rt8Vz3N, /u/WB7x9Km2Pq4Rt8Vz3N, and repeated refreshes with HTTP 200 OK
        val localRoutes = listOf(
            "http://127.0.0.1:3000/?u=WB7x9Km2Pq4Rt8Vz3N",
            "http://127.0.0.1:3000/u/WB7x9Km2Pq4Rt8Vz3N",
            "http://127.0.0.1:3000/.well-known/assetlinks.json",
            "http://127.0.0.1:3000/assets/aistudio/68e8db727540618044fd7e59_shutterstock_2300393367_cf09c248.jpg",
            "http://127.0.0.1:8080/?u=WB7x9Km2Pq4Rt8Vz3N",
            "http://127.0.0.1:8080/u/WB7x9Km2Pq4Rt8Vz3N",
            "http://127.0.0.1:8080/assets/aistudio/68e8db727540618044fd7e59_shutterstock_2300393367_cf09c248.jpg",
        )
        for (route in localRoutes) {
            client.newCall(Request.Builder().url(route).get().build()).execute().use { resp ->
                val bytes = resp.body?.bytes() ?: ByteArray(0)
                println("VERIFY_LOCAL_ROUTE url=$route status=${resp.code} len=${bytes.size}")
                assertEquals("Expected 200 OK for $route", 200, resp.code)
                when {
                    route.endsWith("assetlinks.json") -> {
                        assertTrue(String(bytes).contains("com.aistudio.winnerbalance.wbkpxq"))
                    }
                    route.endsWith(".jpg") -> {
                        assertEquals("image/jpeg", resp.header("Content-Type"))
                        assertTrue("Expected non-empty logo bytes", bytes.size > 10_000)
                    }
                    else -> {
                        val html = String(bytes)
                        assertTrue("Expected WINNER BALANCE portal HTML on $route", html.contains("WINNER BALANCE"))
                        assertTrue(
                            "Expected uploaded logo asset in HTML on $route",
                            html.contains("/assets/aistudio/68e8db727540618044fd7e59_shutterstock_2300393367_cf09c248.jpg")
                        )
                    }
                }
            }
        }

        // 4. Probe external Cloud Run URLs and log exact HTTP status for transparent reporting
        for (extUrl in listOf(
            exactUserUrl,
            "https://ais-dev-clxmtq4b6kwszyihq4tu6n-190436024907.asia-southeast1.run.app/?u=WB7x9Km2Pq4Rt8Vz3N"
        )) {
            runCatching {
                client.newCall(Request.Builder().url(extUrl).get().build()).execute().use { resp ->
                    val snippet = resp.body?.string().orEmpty().replace("\n", " ").take(200)
                    println("VERIFY_EXTERNAL_URL url=$extUrl status=${resp.code} loc=${resp.header("Location")} body=$snippet")
                }
            }.onFailure { err ->
                println("VERIFY_EXTERNAL_URL url=$extUrl ERROR=${err.message}")
            }
        }
    }
}
