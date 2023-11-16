package com.justanotherdeveloper.classic2playergamesonline

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View

class ChangeStyleActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_style)
    }

    @Suppress("UNUSED_PARAMETER")
    fun homeButtonFromChangeStyleClicked(view: View) {
        finish()
    }

    @Suppress("UNUSED_PARAMETER")
    fun settingsButtonFromChangeStyleClicked(view: View?) {
        val openPage = Intent(this, SettingsActivity::class.java)
        startActivity(openPage)
    }
}
