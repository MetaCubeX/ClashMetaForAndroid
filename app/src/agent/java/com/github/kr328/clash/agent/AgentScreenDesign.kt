package com.github.kr328.clash.agent

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import com.github.kr328.clash.R
import com.github.kr328.clash.design.Design

class AgentScreenDesign(context: Context) : Design<Unit>(context) {
    override val root: View = LayoutInflater.from(context).inflate(R.layout.activity_agent, null, false)
}
