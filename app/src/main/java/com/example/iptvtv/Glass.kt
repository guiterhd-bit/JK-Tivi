package com.example.iptvtv

import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable

/**
 * Phong cách "liquid glass" đen – vàng – trắng, vẽ bằng drawable nhẹ (không cần thư viện):
 * viền vàng chuyển sắc, nền kính tối trong mờ và vệt sáng phản chiếu ở nửa trên.
 */
object Glass {
    const val GOLD = 0xFFFFC83D.toInt()
    const val GOLD_LIGHT = 0xFFFFE08A.toInt()
    const val MUTED = 0xFFC9C2B0.toInt()
    const val MUTED_DIM = 0xFFB5AE9A.toInt()

    fun card(d: Float, radiusDp: Int): Drawable {
        val r = radiusDp * d
        val bw = (2 * d).toInt().coerceAtLeast(1)
        val border = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFFFFE08A.toInt(), 0x55FFC83D, 0x33FFFFFF, 0xCCFFC83D.toInt())
        ).apply { cornerRadius = r }
        val fill = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xD91C150A.toInt(), 0xE6080808.toInt())
        ).apply { cornerRadius = r - bw }
        val sheen = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0x33FFFFFF, 0x00FFFFFF, 0x00FFFFFF)
        ).apply { cornerRadius = r - bw }
        return LayerDrawable(arrayOf(border, fill, sheen)).apply {
            setLayerInset(1, bw, bw, bw, bw)
            setLayerInset(2, bw, bw, bw, bw)
        }
    }

    /** Ô nền mờ cho logo kênh. */
    fun logoTile(d: Float): Drawable = GradientDrawable().apply {
        cornerRadius = 9 * d
        setColor(0x2AFFFFFF)
        setStroke(1.coerceAtLeast(d.toInt()), 0x44FFFFFF)
    }

    /** Thanh chọn dạng viên thuốc vàng trong suốt cho dòng đang được chọn. */
    fun rowSelector(d: Float): Drawable {
        val pill = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(0x66FFC83D, 0x1FFFC83D)
        ).apply {
            cornerRadius = 16 * d
            setStroke((1.5f * d).toInt().coerceAtLeast(1), 0xAAFFD666.toInt())
        }
        val inset = InsetDrawable(pill, (6 * d).toInt(), (2 * d).toInt(), (6 * d).toInt(), (2 * d).toInt())
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), inset)
            addState(intArrayOf(android.R.attr.state_pressed), inset)
            addState(intArrayOf(android.R.attr.state_selected), inset)
            addState(intArrayOf(), GradientDrawable().apply { setColor(0) })
        }
    }
}
