package com.github.kr328.clash.design.model

import java.util.UUID

class ProfilePageState {
    var allUpdating = false
    /** Profile currently running a bulk ping (UI spinner on speedometer). */
    var pingingUuid: UUID? = null
}