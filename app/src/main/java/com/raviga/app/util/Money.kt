package com.raviga.app.util

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Prices are shown in US dollars (contract v0.7); pack buttons use the store's own price string. */
object Money {
    /** "$320", "$1,250", "$99.99": cents only when there are some. */
    fun usd(amount: Double): String {
        val whole = abs(amount - amount.roundToLong()) < 0.005
        val format = NumberFormat.getCurrencyInstance(Locale.US).apply {
            minimumFractionDigits = if (whole) 0 else 2
            maximumFractionDigits = if (whole) 0 else 2
        }
        return format.format(amount)
    }

    fun credits(n: Int): String = if (n == 1) "1 credit" else "$n credits"
}
