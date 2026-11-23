package com.aiec.elevatoros.domain

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/* Deterministic money maths, ported from calcQuote / paySchedule / materialList (original lines 6082–6158). */
object Pricing {
    /** JavaScript Math.round semantics (half up toward +∞), so totals match the original to the rupee. */
    fun jsRound(x: Double): Long = kotlin.math.floor(x + 0.5).toLong()
    private fun r100(n: Double): Long = jsRound(n / 100.0) * 100

    data class Calc(
        val lines: List<Pair<String, Long>>, val cost: Long, val list: Long, val discountPct: Double, val discount: Long,
        val price: Long, val gstPct: Double, val gst: Long, val total: Long, val margin: Double, val why: List<String>
    ) { val needsApproval get() = why.isNotEmpty() }

    fun quote(cfg: Config, req: Requirements, discountPct: Double): Calc {
        val d = max(0.0, min(90.0, discountPct))
        val base = if (req.type == "manual") cfg.costManual else cfg.costAuto
        val extra = max(0, req.stops - 2) * cfg.costPerStop
        val cap = cfg.cap[req.capacity] ?: 0L
        val fin = if (req.finish == "ss") cfg.costSS else 0L
        val parts = listOf(
            "ql_lift" to base, "ql_stops" to extra, "ql_cap" to cap, "ql_finish" to fin,
            "ql_install" to cfg.install, "ql_transport" to cfg.transport,
            "ql_dismantle" to if (req.job == "replace") cfg.costDismantle else 0L,
        )
        val cost = parts.sumOf { it.second }
        val k = 1.0 / (1.0 - cfg.targetMargin / 100.0)
        val lines = parts.filter { it.second > 0 }.map { it.first to r100(it.second * k) }
        val list = lines.sumOf { it.second }
        val discount = r100(list * d / 100.0)
        val price = list - discount
        val gst = jsRound(price * cfg.gst / 100.0)
        val total = price + gst
        val margin = if (price > 0) jsRound((price - cost).toDouble() / price * 1000.0) / 10.0 else 0.0
        val why = buildList {
            if (d > cfg.salesDiscountLimit) add("over_limit")
            if (d > cfg.adminDiscountLimit) add("over_admin")
            if (margin < cfg.minMargin) add("below_floor")
        }
        return Calc(lines, cost, list, d, discount, price, cfg.gst, gst, total, margin, why)
    }

    /** Plan 2: token, then arrival % of the total (token credited), the rest at handover. */
    fun schedule(cfg: Config, total: Long): Map<String, Long> {
        val token = min(cfg.token, total)
        val arrival = max(0L, jsRound(total * cfg.arrivalPct / 100.0) - token)
        return linkedMapOf("token" to token, "arrival" to arrival, "final" to max(0L, total - token - arrival))
    }

    fun materials(req: Requirements): List<Pair<String, Int>> {
        val st = req.stops
        return listOf(
            "m_machine" to 1, "m_controller" to 1, "m_rails" to st * 2, "m_cabin" to 1, "m_landing" to st, "m_cardoor" to 1,
            "m_cwt" to 1, "m_ropes" to 1, "m_buffers" to 1, "m_governor" to 1, "m_wiring" to 1, "m_buttons" to st + 1
        )
    }

    fun money(n: Long): String {
        // Indian grouping: 12,34,567
        val neg = n < 0; val s = kotlin.math.abs(n).toString()
        val out = if (s.length <= 3) s else {
            val last3 = s.takeLast(3); var rest = s.dropLast(3); val parts = mutableListOf<String>()
            while (rest.length > 2) { parts.add(0, rest.takeLast(2)); rest = rest.dropLast(2) }
            if (rest.isNotEmpty()) parts.add(0, rest)
            parts.joinToString(",") + "," + last3
        }
        return (if (neg) "-₹" else "₹") + out
    }

    fun roundedPct(x: Double): Long = x.roundToLong()
}
