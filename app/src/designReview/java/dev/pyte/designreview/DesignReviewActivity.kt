package dev.pyte.designreview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle

/** Only packaged in designReview. Uses a plain Application, never PYTE's services or database. */
class DesignReviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent {
            DesignReviewApp(initialPage = intent.getStringExtra("page") ?: "library",
                initialState = intent.getStringExtra("state") ?: "normal",
                initialLargeText = intent.getBooleanExtra("largeText", false),
                initialMotion = !intent.getBooleanExtra("reducedMotion", false))
        }
    }
}
