package com.iptv.jktivi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlin.concurrent.thread

class HomeChannelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        thread {
            try { HomeChannel.publish(app) } finally { pending.finish() }
        }
    }
}
