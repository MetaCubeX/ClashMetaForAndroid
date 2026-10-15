package com.github.kr328.clash.service.remote

import com.github.kr328.kaidl.BinderInterface

@BinderInterface
fun interface IWebdavObserver {
    fun onProgress(phase: String, current: Int, total: Int, label: String)
}
