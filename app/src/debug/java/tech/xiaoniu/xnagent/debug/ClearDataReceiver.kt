package tech.xiaoniu.xnagent.debug

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import tech.xiaoniu.xnagent.BuildConfig

/**
 * DEBUG 版本下用于清空应用数据的广播接收器。
 */
class ClearDataReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!BuildConfig.DEBUG || intent.action != ACTION_CLEAR_DATA) return

        val activityManager = context.getSystemService(ActivityManager::class.java)
        val accepted = activityManager?.clearApplicationUserData() == true
        if (!accepted) {
            Log.w(TAG, "清空应用数据请求未被系统接受")
        }
    }

    companion object {
        private const val TAG = "ClearDataReceiver"
        private const val ACTION_CLEAR_DATA = "tech.xiaoniu.xnagent.action.CLEAR_DATA"
    }
}