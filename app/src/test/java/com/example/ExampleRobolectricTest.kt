package com.example

import android.content.Context
import android.view.View
import android.webkit.WebView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name is uw88`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("UW88 - Wallpaper PG Soft", context.getString(R.string.app_name))
    }

    @Test
    fun `gallery renders 2 columns with 4 PG Soft wallpapers`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val recyclerView = activity.findViewById<RecyclerView>(R.id.rvWallpapers)

        assertEquals(View.VISIBLE, recyclerView.visibility)
        val layoutManager = recyclerView.layoutManager as? GridLayoutManager
        assertNotNull(layoutManager)
        assertEquals(2, layoutManager?.spanCount)
        assertEquals(4, recyclerView.adapter?.itemCount)
    }

    @Test
    fun `masuk button toggles webview and back returns to gallery`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val btnMasuk = activity.findViewById<MaterialButton>(R.id.btnMasuk)
        val rvWallpapers = activity.findViewById<RecyclerView>(R.id.rvWallpapers)
        val webView = activity.findViewById<WebView>(R.id.webView)

        assertEquals("Masuk", btnMasuk.text.toString())
        assertEquals(View.VISIBLE, rvWallpapers.visibility)
        assertEquals(View.GONE, webView.visibility)

        btnMasuk.performClick()
        assertEquals("Galeri", btnMasuk.text.toString())
        assertEquals(View.GONE, rvWallpapers.visibility)
        assertEquals(View.VISIBLE, webView.visibility)
        assertTrue(webView.settings.javaScriptEnabled)
        assertTrue(webView.settings.domStorageEnabled)

        activity.onBackPressedDispatcher.onBackPressed()
        assertEquals("Masuk", btnMasuk.text.toString())
        assertEquals(View.VISIBLE, rvWallpapers.visibility)
        assertEquals(View.GONE, webView.visibility)
    }

    @Test
    fun `share app button launches Android share chooser`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val btnShareApp = activity.findViewById<MaterialButton>(R.id.btnShareApp)

        btnShareApp.performClick()
        val startedIntent = Shadows.shadowOf(activity).nextStartedActivity
        assertNotNull(startedIntent)
        assertEquals(android.content.Intent.ACTION_CHOOSER, startedIntent.action)
    }
}
