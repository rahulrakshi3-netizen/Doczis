package com.doczis.app.ui.splash

import android.content.Intent
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.doczis.app.MainActivity
import com.doczis.app.R

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logoBox = findViewById<android.view.View>(R.id.logoBox)
        val titleText = findViewById<android.view.View>(R.id.titleText)
        val taglineText = findViewById<android.view.View>(R.id.taglineText)
        val container = findViewById<android.view.View>(R.id.splashContainer)

        logoBox.alpha = 0f
        logoBox.scaleX = 0.8f
        logoBox.scaleY = 0.8f
        titleText.alpha = 0f
        titleText.translationY = 16f
        taglineText.alpha = 0f
        taglineText.translationY = 12f

        logoBox.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(600)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                titleText.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(500)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .withEndAction {
                        taglineText.animate()
                            .alpha(1f)
                            .translationY(0f)
                            .setDuration(400)
                            .setInterpolator(AccelerateDecelerateInterpolator())
                            .start()
                    }
                    .start()
            }
            .start()

        container.postDelayed({
            if (!isFinishing) {
                container.animate()
                    .alpha(0f)
                    .setDuration(600)
                    .withEndAction {
                        startActivity(Intent(this, MainActivity::class.java))
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                        finish()
                    }
                    .start()
            }
        }, 2400)
    }
}
