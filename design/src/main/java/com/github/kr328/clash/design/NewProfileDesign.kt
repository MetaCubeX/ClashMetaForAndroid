package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import com.github.kr328.clash.design.adapter.ProfileProviderAdapter
import com.github.kr328.clash.design.databinding.DesignNewProfileBinding
import com.github.kr328.clash.design.model.ProfileProvider
import com.github.kr328.clash.design.util.*
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

class NewProfileDesign(context: Context) : Design<NewProfileDesign.Request>(context) {
    sealed class Request {
        data class Create(val provider: ProfileProvider) : Request()
        data class OpenDetail(val provider: ProfileProvider.External) : Request()
    }

    private val binding = DesignNewProfileBinding
        .inflate(context.layoutInflater, context.root, false)
    private val adapter = ProfileProviderAdapter(context, this::requestCreate, this::requestDetail)

    override val root: View
        get() = binding.root

    suspend fun patchProviders(providers: List<ProfileProvider>) {
        adapter.apply {
            patchDataSet(this::providers, providers)
        }
    }

    init {
        binding.self = this

        binding.activityBarLayout.applyFrom(context)

        binding.mainList.recyclerList.also {
            it.bindAppBarElevation(binding.activityBarLayout)
            it.applyLinearAdapter(context, adapter)
        }
    }

    private fun requestCreate(provider: ProfileProvider) {
        if (provider is ProfileProvider.QR){
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_QR_CODE,
                    Barcode.FORMAT_AZTEC)
                .enableAutoZoom() // available on 16.1.0 and higher
                .build()
            val scanner = GmsBarcodeScanning.getClient(context,options)
            scanner.startScan()
                .addOnSuccessListener { barcode ->
                    // Task completed successfully
                    provider.url =  barcode.rawValue
                    requests.trySend(Request.Create(provider))
                }
                .addOnCanceledListener {
                    // Task canceled
                }
                .addOnFailureListener { e ->
                    // Task failed with an exception
                }

        }else{
            requests.trySend(Request.Create(provider))
        }

    }

    private fun requestDetail(provider: ProfileProvider): Boolean {
        if (provider !is ProfileProvider.External) return false

        requests.trySend(Request.OpenDetail(provider))

        return true
    }
}