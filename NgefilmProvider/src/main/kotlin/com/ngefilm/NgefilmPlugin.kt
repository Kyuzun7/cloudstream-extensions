package com.ngefilm

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class NgefilmPlugin: Plugin() {
    override fun load(context: Context) {
        // Mendaftarkan NgefilmProvider ke Cloudstream
        registerMainAPI(NgefilmProvider())
    }
}