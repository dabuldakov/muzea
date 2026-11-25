package com.example.muzea.utils

import android.content.Context
import com.example.muzea.R
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

/** Компактный формат счётчиков: 999, 1,2 тыс., 3,4 млн. */
object CountFormatter {

    fun format(context: Context, value: Long): String {
        val abs = abs(value)
        return when {
            abs < 1_000 -> value.toString()
            abs < 1_000_000 -> compact(context, value, 1_000.0, R.string.count_thousand_short)
            abs < 1_000_000_000 -> compact(context, value, 1_000_000.0, R.string.count_million_short)
            else -> compact(context, value, 1_000_000_000.0, R.string.count_billion_short)
        }
    }

    private fun compact(
        context: Context,
        value: Long,
        divisor: Double,
        suffixRes: Int
    ): String {
        val scaled = value / divisor
        val symbols = DecimalFormatSymbols(Locale.getDefault())
        val pattern = if (abs(scaled) >= 10) "0" else "0.#"
        val number = DecimalFormat(pattern, symbols).format(scaled)
        return context.getString(R.string.count_compact_format, number, context.getString(suffixRes))
    }
}
