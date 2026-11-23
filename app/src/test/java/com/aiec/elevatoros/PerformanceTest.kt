package com.aiec.elevatoros

import com.aiec.elevatoros.domain.Event
import com.aiec.elevatoros.domain.Reducer
import com.aiec.elevatoros.domain.Tasks
import com.aiec.elevatoros.domain.jsonOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Start-up cost: the whole history is replayed through the rules on every launch. Measured on the JVM. */
class PerformanceTest {
    @Test fun replay20000RecordsQuickly() {
        val h = Harness(); h.boot()
        val evs = h.events.toMutableList()
        var t = Instant.parse("2026-01-05T04:00:00Z"); var n = 0
        fun ev(by: String, type: String, pid: String, vararg d: Pair<String, Any?>) { t = t.plusSeconds(60); evs.add(Event("X" + (n++).toString().padStart(6, '0'), type, by, pid, t.toString(), "D", data = jsonOf(*d))) }
        var jobs = 0
        while (evs.size < 20_000) {
            val pid = "J${jobs++}"
            ev("fld", "LEAD_CREATED", pid, "name" to "C$jobs", "phone" to (9000000000L + jobs).toString(), "address" to "Pune", "ev" to h.photo("building"))
            ev("sal", "LEAD_ACCEPTED", pid)
            repeat(6) { ev("sal", "CONTACT_LOGGED", pid, "channel" to "call", "outcome" to "no_answer", "note" to "try again") }
            ev("sal", "CONTACT_LOGGED", pid, "channel" to "call", "outcome" to "interested")
            ev("sal", "REQ_SAVED", pid, "req" to mapOf("type" to "auto", "stops" to 4, "capacity" to 6, "finish" to "ms", "door" to "auto", "power3" to true, "job" to "new"))
            ev("sal", "QUOTE_CREATED", pid, "discountPct" to 0)
        }
        val rejected = mutableListOf<String>()
        Reducer.rebuild(evs) { e, err -> rejected.add(e.type + err.key) } // warm-up (JIT)
        val t0 = System.nanoTime()
        val s = Reducer.rebuild(evs) { e, err -> rejected.add(e.type + err.key) }
        val ms = (System.nanoTime() - t0) / 1_000_000
        val t1 = System.nanoTime()
        val tasks = Tasks.tasksFor(s, s.users.getValue("sal"), "2026-12-01T00:00:00Z")
        val taskMs = (System.nanoTime() - t1) / 1_000_000
        println("PERF replay ${evs.size} records, ${s.projects.size} jobs: $ms ms; Sales task list (${tasks.size} tasks): $taskMs ms")
        assertTrue(rejected.isEmpty())
        assertEquals(jobs, s.projects.size)
        assertTrue("replay too slow: $ms ms", ms < 5_000)
    }
}
