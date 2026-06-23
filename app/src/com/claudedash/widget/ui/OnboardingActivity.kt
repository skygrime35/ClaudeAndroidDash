package com.claudedash.widget.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.claudedash.widget.R
import com.claudedash.widget.adapter.credentials.TokenStore

class OnboardingActivity : Activity() {

    private lateinit var tokenStore: TokenStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        tokenStore = TokenStore(this)

        val apiKeyField = findViewById<EditText>(R.id.et_api_key)

        // Pre-fill if already stored
        tokenStore.claudeAccessToken?.takeIf { it.isNotEmpty() }?.let {
            apiKeyField.setText(it)
        }

        // Open Anthropic Console to get the key
        findViewById<Button>(R.id.btn_get_key).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.anthropic.com/settings/keys")))
        }

        findViewById<Button>(R.id.btn_save_key).setOnClickListener {
            val key = apiKeyField.text.toString().trim()
            if (key.startsWith("sk-ant-api") && key.length > 20) {
                tokenStore.claudeAccessToken = key
                Toast.makeText(this, "Clé API sauvegardée ✓", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Clé invalide (doit commencer par sk-ant-api...)", Toast.LENGTH_LONG).show()
            }
        }

        findViewById<Button>(R.id.btn_done).setOnClickListener { finish() }
    }
}
