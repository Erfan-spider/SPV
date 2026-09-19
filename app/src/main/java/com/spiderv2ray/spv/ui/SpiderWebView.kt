package com.spiderv2ray.spv.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.AnimationDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

class SpiderWebView(context: Context) : View(context) {

    private val d = resources.displayMetrics.density
    private val spokes = 18
    private val rings = 13
    private val baseSpoke = 1.5f * d
    private val baseRing = 1.0f * d

    private val spokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F0FFFFFF")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = baseSpoke
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#A8FFFFFF")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = baseRing
    }
    private val dewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E6FFFFFF")
    }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E1E24")
    }
    private val wingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#99CFE8FF")
    }
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#C0392B")
    }
    private val legPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#101014")
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * d
    }
    private val silkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F2FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * d
        strokeCap = Paint.Cap.ROUND
    }

    private var nx = Array(0) { FloatArray(0) }
    private var ny = Array(0) { FloatArray(0) }
    private var rx = Array(0) { FloatArray(0) }
    private var ry = Array(0) { FloatArray(0) }
    private var wx = Array(0) { FloatArray(0) }
    private var wy = Array(0) { FloatArray(0) }
    private var sag = Array(0) { FloatArray(0) }
    private var sd = Array(0) { FloatArray(0) }
    private var ta = Array(0) { FloatArray(0) }
    private var td = Array(0) { FloatArray(0) }
    private var gap = Array(0) { BooleanArray(0) }
    private var dew = Array(0) { BooleanArray(0) }
    private var cx = 0f
    private var cy = 0f
    private var maxR = 1f
    private var tnx = 1f
    private var tny = 0f
    private var flyX = 0f
    private var flyY = 0f
    private var flySide = 1f
    private var flyTa = 0f
    private var flyTd = 0f
    private val fk = IntArray(5)
    private val fi = IntArray(5)
    private var time = 0
    private val pt = FloatArray(2)
    private val path = Path()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val rnd = Random()
        val wf = w.toFloat()
        val hf = h.toFloat()
        cx = wf * 0.5f + (rnd.nextFloat() - 0.5f) * 60f * d
        cy = hf * 0.38f
        maxR = hypot(wf, hf) * 0.75f
        val step = 2f * PI.toFloat() / spokes
        val ang = FloatArray(spokes) { step * it + (rnd.nextFloat() - 0.5f) * step * 0.45f }

        // خط پارگی: تقریبا عمودی و دندانه‌دار
        val phi = PI.toFloat() / 2f + (rnd.nextFloat() - 0.5f) * 0.9f
        val ttx = cos(phi)
        val tty = sin(phi)
        tnx = -tty
        tny = ttx
        val ph1 = rnd.nextFloat() * 6.28f
        val ph2 = rnd.nextFloat() * 6.28f
        fun tearDist(relx: Float, rely: Float): FloatArray {
            val al = relx * ttx + rely * tty
            val ds = relx * tnx + rely * tny +
                (14f * sin(al / (52f * d) + ph1) + 8f * sin(al / (21f * d) + ph2)) * d
            return floatArrayOf(abs(al), abs(ds), if (ds >= 0f) 1f else -1f)
        }

        nx = Array(rings) { FloatArray(spokes) }
        ny = Array(rings) { FloatArray(spokes) }
        rx = Array(rings) { FloatArray(spokes) }
        ry = Array(rings) { FloatArray(spokes) }
        wx = Array(rings) { FloatArray(spokes) }
        wy = Array(rings) { FloatArray(spokes) }
        sag = Array(rings) { FloatArray(spokes) }
        sd = Array(rings) { FloatArray(spokes) }
        ta = Array(rings) { FloatArray(spokes) }
        td = Array(rings) { FloatArray(spokes) }
        gap = Array(rings) { BooleanArray(spokes) }
        dew = Array(rings) { BooleanArray(spokes) }

        for (k in 0 until rings) {
            val base = maxR * ((k + 1f) / rings).pow(1.15f)
            for (i in 0 until spokes) {
                val r = base * (0.94f + rnd.nextFloat() * 0.12f)
                nx[k][i] = cx + r * cos(ang[i])
                ny[k][i] = cy + r * sin(ang[i])
                rx[k][i] = rnd.nextFloat() * 2f - 1f
                ry[k][i] = rnd.nextFloat() * 2f - 1f
                sag[k][i] = 0.04f + rnd.nextFloat() * 0.06f
                gap[k][i] = rnd.nextFloat() < 0.07f
                dew[k][i] = rnd.nextFloat() < 0.08f
                val t3 = tearDist(nx[k][i] - cx, ny[k][i] - cy)
                ta[k][i] = t3[0]
                td[k][i] = t3[1]
                sd[k][i] = t3[2]
            }
        }

        flyX = cx + wf * 0.20f
        flyY = cy - hf * 0.11f
        val tf = tearDist(flyX - cx, flyY - cy)
        flyTa = tf[0]
        flyTd = tf[1]
        flySide = tf[2]
        val idx = (0 until rings * spokes).sortedBy {
            hypot(nx[it / spokes][it % spokes] - flyX, ny[it / spokes][it % spokes] - flyY)
        }
        for (n in 0 until 5) {
            fk[n] = idx[n] / spokes
            fi[n] = idx[n] % spokes
        }
    }

    fun setTime(ms: Int) {
        time = ms
        val fin = (ms / FADE.toFloat()).coerceIn(0f, 1f)
        val q = ((ms - FADE - HOLD) / TEAR.toFloat()).coerceIn(0f, 1f)
        val fout = ((q - 0.45f) / 0.55f).coerceIn(0f, 1f)
        alpha = minOf(fin, 1f - fout * fout * (3f - 2f * fout))
        invalidate()
    }

    private fun place(
        x: Float, y: Float, side: Float, ta0: Float, td0: Float,
        reach: Float, grow: Float, wob: Float, a: Float, b: Float
    ) {
        var px = x
        var py = y
        if (wob > 0f) {
            val dist = hypot(x - flyX, y - flyY)
            val amp = 6f * d * wob * exp(-dist / (320f * d))
            px += sin(time * 0.035f + a * 9f) * amp
            py += cos(time * 0.031f + b * 9f) * amp
        }
        val s = ((reach - ta0) / (0.3f * maxR)).coerceIn(0f, 1f)
        if (s > 0f) {
            val m = s * s * (3f - 2f * s) * grow
            val k = exp(-td0 / (230f * d))
            px += side * tnx * 130f * d * m * k
            py += side * tny * 130f * d * m * k +
                150f * d * m * m * exp(-td0 / (320f * d))
        }
        pt[0] = px
        pt[1] = py
    }

    private fun segment(
        p: Path,
        ax: Float, ay: Float, aox: Float, aoy: Float,
        bx: Float, by: Float, box: Float, boy: Float,
        taMid: Float, cross: Boolean, reach: Float, droop: Float, rv: Float
    ) {
        val br = if (cross) ((reach - taMid) / (0.08f * maxR)).coerceIn(0f, 1f) else 0f
        if (br <= 0f) {
            p.moveTo(ax, ay)
            p.lineTo(bx, by)
        } else {
            val frac = 0.5f - (0.12f + 0.16f * rv) * br
            val dx = box - aox
            val dy = boy - aoy
            val dr = droop * br
            p.moveTo(ax, ay)
            p.lineTo(ax + dx * frac, ay + dy * frac + dr)
            p.moveTo(bx, by)
            p.lineTo(bx - dx * frac, by - dy * frac + dr)
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (nx.isEmpty()) return
        val q = ((time - FADE - HOLD) / TEAR.toFloat()).coerceIn(0f, 1f)
        val rp = (q / 0.4f).coerceIn(0f, 1f)
        val reach = maxR * 1.15f * rp * rp * (3f - 2f * rp)
        val grow = 0.4f + 0.6f * q
        val wob = (1f - q * 2.5f).coerceAtLeast(0f)
        val droop = 18f * d * grow

        for (k in 0 until rings) {
            for (i in 0 until spokes) {
                place(nx[k][i], ny[k][i], sd[k][i], ta[k][i], td[k][i],
                    reach, grow, wob, rx[k][i], ry[k][i])
                wx[k][i] = pt[0]
                wy[k][i] = pt[1]
            }
        }
        place(cx, cy, 1f, 0f, 0f, reach, grow, wob, 0f, 0f)
        val hx = pt[0]
        val hy = pt[1]

        path.reset()
        for (i in 0 until spokes) {
            segment(path, hx, hy, cx, cy, wx[0][i], wy[0][i], nx[0][i], ny[0][i],
                ta[0][i] * 0.5f, sd[0][i] < 0f, reach, droop, ry[0][i] * 0.5f + 0.5f)
            for (k in 0 until rings - 1) {
                segment(path, wx[k][i], wy[k][i], nx[k][i], ny[k][i],
                    wx[k + 1][i], wy[k + 1][i], nx[k + 1][i], ny[k + 1][i],
                    (ta[k][i] + ta[k + 1][i]) * 0.5f, sd[k][i] != sd[k + 1][i],
                    reach, droop, ry[k][i] * 0.5f + 0.5f)
            }
        }
        canvas.drawPath(path, spokePaint)

        path.reset()
        for (k in 0 until rings) {
            for (i in 0 until spokes) {
                if (gap[k][i]) continue
                val j = (i + 1) % spokes
                val taMid = (ta[k][i] + ta[k][j]) * 0.5f
                val broken = sd[k][i] != sd[k][j] && reach > taMid
                if (!broken) {
                    val x0 = wx[k][i]
                    val y0 = wy[k][i]
                    val x1 = wx[k][j]
                    val y1 = wy[k][j]
                    val sg = sag[k][i] + 0.05f * q
                    val mx = (x0 + x1) / 2f
                    val my = (y0 + y1) / 2f
                    path.moveTo(x0, y0)
                    path.quadTo(mx + (hx - mx) * sg, my + (hy - my) * sg, x1, y1)
                } else {
                    segment(path, wx[k][i], wy[k][i], nx[k][i], ny[k][i],
                        wx[k][j], wy[k][j], nx[k][j], ny[k][j],
                        taMid, true, reach, droop, rx[k][i] * 0.5f + 0.5f)
                }
            }
        }
        canvas.drawPath(path, ringPaint)

        for (k in 0 until rings) {
            for (i in 0 until spokes) {
                if (dew[k][i]) canvas.drawCircle(wx[k][i], wy[k][i], 2.2f * d, dewPaint)
            }
        }

        place(flyX, flyY, flySide, flyTa, flyTd, reach, grow, wob, 0.37f, -0.61f)
        val fx = pt[0] + sin(time * 0.06f) * 2.5f * d * wob
        val fy = pt[1] + cos(time * 0.071f) * 2.5f * d * wob
        path.reset()
        for (n in 0 until 5) {
            val a = fk[n]
            val b = fi[n]
            segment(path, fx, fy, flyX, flyY, wx[a][b], wy[a][b], nx[a][b], ny[a][b],
                (flyTa + ta[a][b]) * 0.5f, sd[a][b] != flySide, reach, droop, 0.5f)
        }
        canvas.drawPath(path, spokePaint)
        drawFly(canvas, fx, fy, 1f, 35f + sin(time * 0.05f) * 8f * wob + q * 20f * flySide)
    }

    private fun drawFly(c: Canvas, x: Float, y: Float, sc: Float, rot: Float) {
        val u = d * 1.6f
        c.save()
        c.translate(x, y)
        c.rotate(rot)
        c.scale(sc, sc)
        for (s in intArrayOf(-1, 1)) {
            val wcx = -6f * u
            val wcy = s * 7f * u
            c.save()
            c.rotate(-s * 35f, wcx, wcy)
            c.drawOval(wcx - 9f * u, wcy - 3.6f * u, wcx + 9f * u, wcy + 3.6f * u, wingPaint)
            c.restore()
        }
        for (s in intArrayOf(-1, 1)) {
            for (j in -1..1) {
                c.drawLine(j * 3.5f * u, s * 4f * u, j * 6f * u - 2f * u, s * 11f * u, legPaint)
            }
        }
        c.drawOval(-9f * u, -5f * u, 7f * u, 5f * u, bodyPaint)
        c.drawCircle(9f * u, 0f, 4f * u, bodyPaint)
        c.drawCircle(10f * u, -2.6f * u, 1.8f * u, eyePaint)
        c.drawCircle(10f * u, 2.6f * u, 1.8f * u, eyePaint)
        c.drawLine(-8f * u, -6f * u, 6f * u, 5f * u, silkPaint)
        c.drawLine(-8f * u, 5f * u, 5f * u, -6f * u, silkPaint)
        c.drawLine(-3f * u, -6f * u, -3f * u, 6f * u, silkPaint)
        c.drawLine(2f * u, -6f * u, 2f * u, 6f * u, silkPaint)
        c.restore()
    }

    companion object {
        const val FADE = 250
        const val HOLD = 1400
        const val TEAR = 1800
        const val TOTAL = FADE + HOLD + TEAR
        private const val DANGLE_TOTAL = 3300L
        private const val WALK_TOTAL = 3700L
        private const val WALK_FRAME_MS = 70
        // اگه فریم‌های راه رفتن رو به سمت چپ بود، این رو false کن
        private const val WALK_FACES_RIGHT = true
        private var last = -1
        private var busy = false

        fun blink(glow: View) {
            ObjectAnimator.ofFloat(glow, View.ALPHA, 0f, 1f, 0.1f, 1f, 0.1f, 1f, 0f)
                .setDuration(1600)
                .start()
        }

        private fun walkFrames(activity: Activity): List<Int> {
            val ids = ArrayList<Int>()
            for (i in 1..60) {
                val id = activity.resources.getIdentifier(
                    String.format("spider_walk_%02d", i), "drawable", activity.packageName
                )
                if (id == 0) break
                ids.add(id)
            }
            return ids
        }

        fun tap(activity: Activity, spider: View, glow: View) {
            if (busy) return
            val modes = ArrayList<Int>()
            modes.add(0)
            modes.add(1)
            modes.add(2)
            busy = true
            blink(glow)
            var m: Int
            do {
                m = modes[Random().nextInt(modes.size)]
            } while (m == last && modes.size > 1)
            last = m
            val total: Long = when (m) {
                0 -> { play(activity); TOTAL.toLong() }
                1 -> { dangle(activity, spider); DANGLE_TOTAL }
                else -> { walk(activity, spider); WALK_TOTAL }
            }
            Handler(Looper.getMainLooper()).postDelayed({ busy = false }, total + 150L)
        }

        fun play(activity: Activity) {
            val root = activity.findViewById<ViewGroup>(android.R.id.content)
            if (root.findViewWithTag<View>("spider_web") != null) return

            val web = SpiderWebView(activity)
            web.tag = "spider_web"
            web.alpha = 0f
            root.addView(
                web,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )

            ValueAnimator.ofInt(0, TOTAL).apply {
                duration = TOTAL.toLong()
                interpolator = LinearInterpolator()
                addUpdateListener { web.setTime(it.animatedValue as Int) }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        root.removeView(web)
                    }
                })
                start()
            }
        }

        fun dangle(activity: Activity, spider: View) {
            val src = spider as? ImageView ?: return
            val root = activity.findViewById<ViewGroup>(android.R.id.content)
            val d = spider.resources.displayMetrics.density
            val w = root.width.toFloat()
            val h = root.height.toFloat()
            val sw = (150f * d).toInt()
            val sh = (100f * d).toInt()
            val tw = (1.5f * d).toInt().coerceAtLeast(2)
            val hangX = w * 0.5f
            val hangL = h * 0.42f
            val overlap = 12f * d
            val threadFull = (hangL + 40f * d).toInt()
            val jumpAt = 2200f
            val jumpDur = 700f
            val arc = 90f * d

            val a = IntArray(2)
            val r = IntArray(2)
            spider.getLocationInWindow(a)
            root.getLocationInWindow(r)
            val tx = a[0] - r[0] + spider.width / 2f
            val ty = a[1] - r[1] + spider.height / 2f
            val endScale = spider.width / sw.toFloat()

            val layer = FrameLayout(activity)
            layer.layoutDirection = View.LAYOUT_DIRECTION_LTR
            val thread = View(activity)
            thread.setBackgroundColor(Color.parseColor("#E6FFFFFF"))
            layer.addView(
                thread,
                FrameLayout.LayoutParams(tw, threadFull, Gravity.LEFT or Gravity.TOP)
            )
            val img = ImageView(activity)
            img.setImageDrawable(src.drawable)
            img.scaleType = ImageView.ScaleType.FIT_CENTER
            layer.addView(
                img,
                FrameLayout.LayoutParams(sw, sh, Gravity.LEFT or Gravity.TOP)
            )
            root.addView(
                layer,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            thread.pivotX = tw / 2f
            thread.pivotY = 0f
            thread.translationX = hangX - tw / 2f
            thread.scaleY = 0f
            img.translationX = hangX - sw / 2f
            img.translationY = -sh.toFloat()
            spider.alpha = 0f

            var jx0 = 0f
            var jy0 = 0f
            var jth = 0f
            var jlen = 0f
            var jumped = false

            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = DANGLE_TOTAL
                interpolator = LinearInterpolator()
                addUpdateListener {
                    val ms = (it.animatedValue as Float) * DANGLE_TOTAL
                    val cxp: Float
                    val cyp: Float
                    var th = 0f
                    val len: Float
                    var sc = 1f
                    val rot: Float
                    var sqx = 1f
                    var sqy = 1f
                    if (ms < 900f) {
                        val t = ms / 900f - 1f
                        val ov = t * t * (2f * t + 1f) + 1f
                        cxp = hangX
                        cyp = -sh / 2f + (hangL + sh / 2f) * ov
                        len = cyp - sh / 2f + overlap
                        rot = 0f
                    } else if (ms < jumpAt) {
                        val t = (ms - 900f) / 1000f
                        th = 0.17f * exp(-0.5f * t) * sin(t * 2f * PI.toFloat() * 1.1f)
                        cxp = hangX + hangL * sin(th)
                        cyp = hangL * cos(th)
                        len = hangL - sh / 2f + overlap
                        rot = -th * 57.2958f
                    } else {
                        if (!jumped) {
                            val t0 = (jumpAt - 900f) / 1000f
                            jth = 0.17f * exp(-0.5f * t0) * sin(t0 * 2f * PI.toFloat() * 1.1f)
                            jx0 = hangX + hangL * sin(jth)
                            jy0 = hangL * cos(jth)
                            jlen = hangL - sh / 2f + overlap
                            jumped = true
                        }
                        val u = ((ms - jumpAt) / jumpDur).coerceIn(0f, 1f)
                        cxp = jx0 + (tx - jx0) * u
                        cyp = jy0 + (ty - jy0) * u - 4f * arc * u * (1f - u)
                        sc = 1f + (endScale - 1f) * u
                        rot = -jth * 57.2958f * (1f - u) - 360f * u
                        th = jth
                        len = jlen * (1f - ((ms - jumpAt) / 220f).coerceIn(0f, 1f))
                        if (ms > jumpAt + jumpDur) {
                            val f = ((ms - jumpAt - jumpDur) /
                                (DANGLE_TOTAL - jumpAt - jumpDur)).coerceIn(0f, 1f)
                            val sq = sin(f * PI.toFloat()) * 0.1f
                            sqx = 1f + sq
                            sqy = 1f - sq
                        }
                    }
                    thread.rotation = -th * 57.2958f
                    thread.scaleY = (len / threadFull).coerceIn(0f, 1f)
                    img.translationX = cxp - sw / 2f
                    img.translationY = cyp - sh / 2f
                    img.rotation = rot
                    img.scaleX = sc * sqx
                    img.scaleY = sc * sqy
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

        fun walk(activity: Activity, spider: View) {
            val ids = walkFrames(activity)
            if (ids.isEmpty()) { SpiderScuttle.run(activity, spider); return }
            val root = activity.findViewById<ViewGroup>(android.R.id.content)
            val d = spider.resources.displayMetrics.density
            val anim = AnimationDrawable()
            for (id in ids) {
                val fr = activity.getDrawable(id) ?: continue
                anim.addFrame(fr, WALK_FRAME_MS)
            }
            anim.isOneShot = false

            val a = IntArray(2)
            val r = IntArray(2)
            spider.getLocationInWindow(a)
            root.getLocationInWindow(r)
            val x0 = (a[0] - r[0]).toFloat()
            val y0 = (a[1] - r[1]).toFloat()
            val out = root.width - x0 + 8f * d
            val face = if (WALK_FACES_RIGHT) 1f else -1f

            val layer = FrameLayout(activity)
            layer.layoutDirection = View.LAYOUT_DIRECTION_LTR
            val img = ImageView(activity)
            img.setImageDrawable(anim)
            img.scaleType = ImageView.ScaleType.FIT_CENTER
            layer.addView(
                img,
                FrameLayout.LayoutParams(spider.width, spider.height, Gravity.LEFT or Gravity.TOP)
            )
            root.addView(
                layer,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            img.translationX = x0
            img.translationY = y0
            img.scaleX = face
            spider.alpha = 0f
            img.post { anim.start() }

            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = WALK_TOTAL
                interpolator = LinearInterpolator()
                addUpdateListener {
                    val ms = (it.animatedValue as Float) * WALK_TOTAL
                    if (ms < 1500f) {
                        img.translationX = x0 + out * (ms / 1500f)
                        img.scaleX = face
                    } else if (ms < 2100f) {
                        img.translationX = x0 + out
                    } else if (ms < 3600f) {
                        val u = (ms - 2100f) / 1500f
                        img.translationX = x0 + out * (1f - u) * (1f - u)
                        img.scaleX = -face
                    } else {
                        img.translationX = x0
                        img.scaleX = -face
                    }
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        anim.stop()
                        root.removeView(layer)
                        spider.alpha = 1f
                    }
                })
                start()
            }
        }
    }
}
