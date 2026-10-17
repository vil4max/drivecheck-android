package io.github.vil4max.drivecheck.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.vil4max.drivecheck.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

// The launcher icon as the system masks it, for review next to the iOS icon.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "xxxhdpi")
class IconScreenshotTest {
    @Test
    fun launcher_icon() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val icon = context.getDrawable(R.mipmap.ic_launcher)
        assertTrue(icon is AdaptiveIconDrawable)
        val bitmap = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, 432, 432)
        icon.draw(Canvas(bitmap))
        bitmap.captureRoboImage("src/test/screenshots/launcher_icon.png")
    }
}
