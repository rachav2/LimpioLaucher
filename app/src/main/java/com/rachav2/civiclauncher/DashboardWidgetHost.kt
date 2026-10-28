package com.rachav2.civiclauncher

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Reusable dashboard widget container. Add widgets with addWidget(); positions and dimensions
 * are persisted in SharedPreferences. Long-press to enter edit mode, drag to move, drag the
 * bottom-right corner to resize. Coordinates use the launcher's 1280x720 design space.
 */
class DashboardWidgetHost(
    context: Context,
    private val prefs: SharedPreferences,
    private val designScale: Float
) : FrameLayout(context) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val density get() = resources.displayMetrics.density
    private var activeWidget: FrameLayout? = null
    private var downX = 0f
    private var downY = 0f
    private var startLeft = 0
    private var startTop = 0
    private var startWidth = 0
    private var startHeight = 0
    private var editing = false
    private var resizing = false
    private val densityScale get() = if (width > 0) width / 1280f else designScale

    init { setBackgroundColor(Color.TRANSPARENT) }

    fun addWidget(id: String, content: View, left: Float, top: Float, width: Float, height: Float) {
        val saved = prefs.getString("widget.$id", null)?.split(",")
        val x = saved?.getOrNull(0)?.toFloatOrNull() ?: left
        val y = saved?.getOrNull(1)?.toFloatOrNull() ?: top
        val w = saved?.getOrNull(2)?.toFloatOrNull() ?: width
        val h = saved?.getOrNull(3)?.toFloatOrNull() ?: height
        val frame = FrameLayout(context)
        frame.tag = id
        frame.background = GradientDrawable().apply {
            setColor(0xB30A1930.toInt())
            cornerRadius = 9f * density
            setStroke((1.3f * density).roundToInt().coerceAtLeast(1), 0xFF2F8CFF.toInt())
        }
        frame.addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        val handle = TextView(context).apply {
            text = "◢"
            textSize = 12f
            setTextColor(0xFF6CB4FF.toInt())
            gravity = android.view.Gravity.CENTER
            visibility = View.GONE
            tag = "resize-handle"
        }
        frame.addView(handle, LayoutParams((24f * density).roundToInt(), (24f * density).roundToInt(), android.view.Gravity.RIGHT or android.view.Gravity.BOTTOM))
        frame.setOnTouchListener { view, event -> handleTouch(view as FrameLayout, event, id) }
        addView(frame, LayoutParams((w * densityScale).roundToInt(), (h * densityScale).roundToInt()).apply {
            leftMargin = (x * densityScale).roundToInt()
            topMargin = (y * densityScale).roundToInt()
        })
    }

    private fun handleTouch(frame: FrameLayout, event: MotionEvent, id: String): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeWidget = frame
                downX = event.rawX
                downY = event.rawY
                val lp = frame.layoutParams as LayoutParams
                startLeft = lp.leftMargin
                startTop = lp.topMargin
                startWidth = lp.width
                startHeight = lp.height
                resizing = event.x >= frame.width - 28f * density && event.y >= frame.height - 28f * density
                frame.postDelayed({
                    if (activeWidget === frame && !editing) {
                        editing = true
                        showEditState(frame, true)
                    }
                }, ViewConfiguration.getLongPressTimeout().toLong())
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val current = activeWidget ?: return true
                if (!editing) return true
                val dx = (event.rawX - downX).roundToInt()
                val dy = (event.rawY - downY).roundToInt()
                val lp = current.layoutParams as LayoutParams
                if (resizing) {
                    lp.width = max((100f * densityScale).roundToInt(), startWidth + dx)
                    lp.height = max((28f * densityScale).roundToInt(), startHeight + dy)
                } else {
                    lp.leftMargin = (startLeft + dx).coerceIn(0, max(0, this.width - current.width))
                    lp.topMargin = (startTop + dy).coerceIn(0, max(0, this.height - current.height))
                }
                current.layoutParams = lp
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val current = activeWidget
                if (current != null && editing) {
                    val lp = current.layoutParams as LayoutParams
                    val scale = densityScale.coerceAtLeast(0.1f)
                    prefs.edit().putString(
                        "widget.$id",
                        listOf(lp.leftMargin / scale, lp.topMargin / scale, lp.width / scale, lp.height / scale)
                            .joinToString(",")
                    ).apply()
                    showEditState(current, false)
                    editing = false
                }
                activeWidget = null
                return true
            }
        }
        return true
    }

    private fun showEditState(frame: FrameLayout, visible: Boolean) {
        frame.background = GradientDrawable().apply {
            setColor(0xB30A1930.toInt())
            cornerRadius = 9f * density
            setStroke((if (visible) 2.5f else 1f).times(density).roundToInt().coerceAtLeast(1),
                if (visible) 0xFFFFC857.toInt() else 0xFF2F8CFF.toInt())
        }
        for (i in 0 until frame.childCount) {
            val child = frame.getChildAt(i)
            if (child.tag == "resize-handle") child.visibility = if (visible) View.VISIBLE else View.GONE
        }
    }
}
