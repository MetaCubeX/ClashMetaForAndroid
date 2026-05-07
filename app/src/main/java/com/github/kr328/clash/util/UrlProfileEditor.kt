package com.github.kr328.clash.util

import com.github.kr328.clash.BaseActivity
import com.github.kr328.clash.PropertiesActivity
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.design.R
import com.github.kr328.clash.service.model.Profile

suspend fun BaseActivity<*>.createEmptyUrlProfileAndOpenEditor() {
    val name = getString(R.string.new_profile)
    val uuid = withProfile { create(Profile.Type.Url, name) }
    startActivity(PropertiesActivity::class.intent.setUUID(uuid))
}
