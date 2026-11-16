package com.github.kr328.clash.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.github.kr328.clash.R
import com.google.android.material.button.MaterialButton

class WidgetConfigActivity : AppCompatActivity() {
    private lateinit var store: WidgetStore
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private lateinit var appearanceGroup: RadioGroup
    private lateinit var transparencyGroup: RadioGroup
    private lateinit var previewRoot: View
    private lateinit var previewIcon: ImageView
    private lateinit var previewTitle: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        setContentView(R.layout.activity_widget_config)

        store = WidgetStore(this)

        appearanceGroup = findViewById(R.id.widget_appearance_group)
        transparencyGroup = findViewById(R.id.widget_transparency_group)
        previewRoot = findViewById(R.id.widget_preview)
        previewIcon = findViewById(R.id.widget_preview_icon)
        previewTitle = findViewById(R.id.widget_preview_title)

        appearanceGroup.check(
            when (store.appearance) {
                WidgetAppearance.FollowSystem -> R.id.widget_appearance_system
                WidgetAppearance.Light -> R.id.widget_appearance_light
                WidgetAppearance.Dark -> R.id.widget_appearance_dark
            }
        )

        transparencyGroup.check(
            when (store.transparency) {
                WidgetTransparency.Low -> R.id.widget_transparency_low
                WidgetTransparency.Medium -> R.id.widget_transparency_medium
                WidgetTransparency.High -> R.id.widget_transparency_high
            }
        )

        appearanceGroup.setOnCheckedChangeListener { _, _ -> updatePreview() }
        transparencyGroup.setOnCheckedChangeListener { _, _ -> updatePreview() }

        findViewById<MaterialButton>(R.id.widget_cancel).setOnClickListener {
            finish()
        }

        findViewById<MaterialButton>(R.id.widget_save).setOnClickListener {
            save()
        }

        updatePreview()
    }

    private fun selectedAppearance(): WidgetAppearance {
        return when (appearanceGroup.checkedRadioButtonId) {
            R.id.widget_appearance_light -> WidgetAppearance.Light
            R.id.widget_appearance_dark -> WidgetAppearance.Dark
            else -> WidgetAppearance.FollowSystem
        }
    }

    private fun selectedTransparency(): WidgetTransparency {
        return when (transparencyGroup.checkedRadioButtonId) {
            R.id.widget_transparency_low -> WidgetTransparency.Low
            R.id.widget_transparency_high -> WidgetTransparency.High
            else -> WidgetTransparency.Medium
        }
    }

    private fun updatePreview() {
        val night = WidgetAppearanceResolver.isNight(this, selectedAppearance())

        previewRoot.setBackgroundResource(
            WidgetAppearanceResolver.background(night, selectedTransparency())
        )

        val foreground = ContextCompat.getColor(
            this,
            WidgetAppearanceResolver.foreground(night, true)
        )

        previewIcon.setColorFilter(foreground)
        previewTitle.setTextColor(foreground)
    }

    private fun save() {
        store.appearance = selectedAppearance()
        store.transparency = selectedTransparency()

        Thread {
            WidgetUpdater.updateAll(applicationContext)
        }.start()

        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(
                RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            )
        }

        finish()
    }
}
