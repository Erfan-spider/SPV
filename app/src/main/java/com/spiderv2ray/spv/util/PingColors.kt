package com.spiderv2ray.spv.util

import com.spiderv2ray.spv.R

object PingColors {
    fun colorResFor(ms: Long): Int =
        if (ms >= 0) R.color.ping_good else R.color.ping_bad
}
