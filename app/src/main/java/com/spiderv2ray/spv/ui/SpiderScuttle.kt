package com.spiderv2ray.spv.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Walking mode that needs no extra frames: the spider scuttles sideways with a
 * bouncy little gait (bob, tilt, squash), stops to look around, then scuttles back.
 */
object SpiderScuttle {
    const val TOTAL = 3400L

    private fun ease(u: Float): Float = u * u * (3f - 2f * u)

    fun run(activity: Activity, spider: View) {
        val src = spider as? ImageView ?: return
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        val d = spider.resources.displayMetrics.density
        val a = IntArray(2)
        val r = IntArray(2)
        spider.getLocationInWindow(a)
        root.getLocationInWindow(r)
        val x0 = (a[0] - r[0]).toFloat()
        val y0 = (a[1] - r[1]).toFloat()
        val w = spider.width
        val h = spider.height

        val goRight = x0 + w / 2f < root.width / 2f
        val room = if (goRight) root.width - (x0 + w) - 12f * d else x0 - 12f * d
        val dist = minOf(room, 190f * d).coerceAtLeast(0f)
        val dir = if (goRight) 1f else -1f
        val dx = dist * dir

        val layer = FrameLayout(activity)
        layer.layoutDirection = View.LAYOUT_DIRECTION_LTR
        val img = ImageView(activity)
        img.setImageDrawable(src.drawable)
        img.scaleType = ImageView.ScaleType.FIT_CENTER
        layer.addView(img, FrameLayout.LayoutParams(w, h, Gravity.LEFT or Gravity.TOP))
        root.addView(
            layer,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        img.translationX = x0
        img.translationY = y0
        spider.alpha = 0f

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = TOTAL
            interpolator = LinearInterpolator()
            addUpdateListener {
                val ms = (it.animatedValue as Float) * TOTAL
                var pos = 0f
                var moving = 0f
                var facing = dir
                var look = 0f
                if (ms < 1200f) {
                    pos = ease(ms / 1200f)
                    moving = 1f
                } else if (ms < 1900f) {
                    pos = 1f
                    look = sin((ms - 1200f) / 700f * 2f * PI.toFloat() * 1.5f) * 9f
                } else if (ms < 3100f) {
                    pos = 1f - ease((ms - 1900f) / 1200f)
                    moving = 1f
                    facing = -dir
                }
                val step = sin(ms * 0.018f)
                val bob = abs(step) * 5f * d * moving
                img.translationX = x0 + dx * pos
                img.translationY = y0 - bob
                img.rotation = 5f * facing * moving + step * 4f * moving + look
                img.scaleY = 1f - 0.04f * abs(step) * moving
                img.scaleX = 1f + 0.03f * abs(step) * moving
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    root.removeView(layer)
                    spider.alpha = 1f
                }
            })
            start()
        }
    }
}
