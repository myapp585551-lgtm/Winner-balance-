package com.example.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Exact integer-paisa currency conversion and formatting for BDT (100 paisa = 1.00 BDT).
 * Eliminates floating-point rounding discrepancies during balance updates and reservations.
 */
object CurrencyFormatter {
    const val PAISA_PER_BDT = 100L
    const val MAX_BDT_AMOUNT = 100_000_000L // 100 Million BDT safety ceiling

    fun formatBdt(paisa: Long): String {
        val bd = BigDecimal.valueOf(paisa).divide(BigDecimal.valueOf(PAISA_PER_BDT), 2, RoundingMode.UNNECESSARY)
        val symbols = DecimalFormatSymbols(Locale.ENGLISH)
        val formatter = DecimalFormat("#,##0.00", symbols)
        return "৳ ${formatter.format(bd)} BDT"
    }

    fun formatPlainDecimal(paisa: Long): String {
        val bd = BigDecimal.valueOf(paisa).divide(BigDecimal.valueOf(PAISA_PER_BDT), 2, RoundingMode.UNNECESSARY)
        return bd.toPlainString()
    }

    /**
     * Parses a user-entered BDT string into integer paisa.
     * @param allowZero true when setting an initial/manual user balance to 0.00; false for deposits/withdrawals.
     */
    fun parseBdtToPaisa(rawInput: String, allowZero: Boolean = false): Result<Long> {
        val cleaned = rawInput.trim().replace(",", "")
        if (cleaned.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please enter an amount in BDT."))
        }
        val decimal = try {
            BigDecimal(cleaned)
        } catch (e: NumberFormatException) {
            return Result.failure(IllegalArgumentException("Enter a valid numeric BDT amount (for example, 1500 or 1500.50)."))
        }

        if (decimal.scale() > 2) {
            return Result.failure(IllegalArgumentException("BDT amount can have at most 2 decimal places."))
        }
        if (decimal < BigDecimal.ZERO || (!allowZero && decimal.compareTo(BigDecimal.ZERO) == 0)) {
            val msg = if (allowZero) "Balance cannot be negative." else "Amount must be greater than 0 BDT."
            return Result.failure(IllegalArgumentException(msg))
        }
        if (decimal > BigDecimal.valueOf(MAX_BDT_AMOUNT)) {
            return Result.failure(IllegalArgumentException("Amount exceeds the maximum allowed limit."))
        }

        val paisa = decimal
            .multiply(BigDecimal.valueOf(PAISA_PER_BDT))
            .setScale(0, RoundingMode.UNNECESSARY)
            .longValueExact()

        return Result.success(paisa)
    }
}
