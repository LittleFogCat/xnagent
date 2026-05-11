package tech.xiaoniu.xnagent.common.util

import android.annotation.SuppressLint
import java.util.Date

/**
 * @author littlefogcat
 * @email littlefogcat@foxmail.com
 */

@SuppressLint("SimpleDateFormat")
private val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

fun currentTimeF(format: String = "yyyy-MM-dd HH:mm:ss"): String {
    return formatter.format(Date())
}