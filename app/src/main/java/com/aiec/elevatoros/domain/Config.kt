package com.aiec.elevatoros.domain

/*
 * Business rules with the original defaults (DEFAULT_CONFIG, AIEC-Elevator-OS.html line ~5995) and allowed ranges
 * (CFG_LIMITS). Only the rules this version uses are carried; each value is checked by the CONFIG_SET rule itself.
 */
data class Config(
    val costAuto: Long = 300_000, val costManual: Long = 220_000, val costPerStop: Long = 40_000, val costSS: Long = 50_000,
    val costDismantle: Long = 30_000,
    val cap: Map<Int, Long> = mapOf(4 to 0L, 6 to 20_000L, 8 to 40_000L, 10 to 60_000L, 13 to 90_000L),
    val install: Long = 60_000, val transport: Long = 15_000, val gst: Double = 18.0,
    val targetMargin: Double = 60.0, val salesDiscountLimit: Double = 8.0, val adminDiscountLimit: Double = 18.0,
    val minMargin: Double = 20.0,
    val token: Long = 10_000, val arrivalPct: Double = 90.0,
    val warrantyMonths: Int = 12, val amcVisits: Int = 4, val validityDays: Int = 30,
    val remindDays: Int = 1, val escalateDays: Int = 3, val escOwnerDays: Int = 7, val sundayWork: Boolean = false,
    val tolWidth: Int = 1500, val tolDepth: Int = 1500, val tolPit: Int = 1200, val tolHead: Int = 3800,
    val officeNumber: String = "", val sosNumber: String = "", val cityCode: String = "PUN",
) {
    /** Numbers a Head Quarter may change, with their allowed range. */
    fun number(key: String): Double? = when (key) {
        "gst" -> gst; "targetMargin" -> targetMargin; "salesDiscountLimit" -> salesDiscountLimit
        "adminDiscountLimit" -> adminDiscountLimit; "minMargin" -> minMargin; "token" -> token.toDouble()
        "arrivalPct" -> arrivalPct; "warrantyMonths" -> warrantyMonths.toDouble(); "amcVisits" -> amcVisits.toDouble()
        "validityDays" -> validityDays.toDouble(); "remindDays" -> remindDays.toDouble()
        "escalateDays" -> escalateDays.toDouble(); "escOwnerDays" -> escOwnerDays.toDouble()
        else -> null
    }

    fun with(key: String, value: String): Config {
        val n = value.toDoubleOrNull()
        return when (key) {
            "gst" -> copy(gst = n!!); "targetMargin" -> copy(targetMargin = n!!)
            "salesDiscountLimit" -> copy(salesDiscountLimit = n!!); "adminDiscountLimit" -> copy(adminDiscountLimit = n!!)
            "minMargin" -> copy(minMargin = n!!); "token" -> copy(token = n!!.toLong()); "arrivalPct" -> copy(arrivalPct = n!!)
            "warrantyMonths" -> copy(warrantyMonths = n!!.toInt()); "amcVisits" -> copy(amcVisits = n!!.toInt())
            "validityDays" -> copy(validityDays = n!!.toInt()); "remindDays" -> copy(remindDays = n!!.toInt())
            "escalateDays" -> copy(escalateDays = n!!.toInt()); "escOwnerDays" -> copy(escOwnerDays = n!!.toInt())
            "officeNumber" -> copy(officeNumber = value.trim()); "sosNumber" -> copy(sosNumber = value.trim())
            "cityCode" -> copy(cityCode = value.trim().uppercase())
            else -> this
        }
    }

    companion object {
        val LIMITS: Map<String, ClosedFloatingPointRange<Double>> = mapOf(
            "gst" to 0.0..28.0, "targetMargin" to 1.0..90.0, "salesDiscountLimit" to 0.0..50.0,
            "adminDiscountLimit" to 0.0..60.0, "minMargin" to 0.0..80.0, "token" to 0.0..1_000_000.0,
            "arrivalPct" to 0.0..100.0, "warrantyMonths" to 0.0..120.0, "amcVisits" to 1.0..12.0,
            "validityDays" to 1.0..365.0, "remindDays" to 1.0..365.0, "escalateDays" to 1.0..365.0, "escOwnerDays" to 1.0..365.0,
        )
        val TEXT_KEYS = setOf("officeNumber", "sosNumber", "cityCode")

        /** Returns an error key, or null when [key]=[value] is allowed together with the other rules. */
        fun check(current: Config, key: String, value: String): String? {
            if (key in TEXT_KEYS) {
                return when (key) {
                    "cityCode" -> if (Regex("^[A-Za-z]{2,5}$").matches(value.trim())) null else "err_fill"
                    else -> if (value.isBlank() || Regex("^\\+?[0-9]{3,15}$").matches(value.trim())) null else "err_phone"
                }
            }
            val range = LIMITS[key] ?: return "err_unknown"
            val n = value.toDoubleOrNull() ?: return "err_number"
            if (n !in range) return "err_range"
            val next = current.with(key, value)
            if (next.salesDiscountLimit > next.adminDiscountLimit) return "err_order"
            if (!(next.remindDays <= next.escalateDays && next.escalateDays <= next.escOwnerDays)) return "err_order"
            if (next.minMargin > next.targetMargin) return "err_order"
            return null
        }
    }
}
