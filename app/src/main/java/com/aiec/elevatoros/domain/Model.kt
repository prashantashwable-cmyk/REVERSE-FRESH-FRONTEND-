package com.aiec.elevatoros.domain

import kotlinx.serialization.json.JsonObject

/*
 * Domain model ported from the original single-file app (AIEC-Elevator-OS.html, commit 605575a):
 *  - ROLES        line 5842
 *  - PHASES/STAGES line 5844–5851
 * Every change is an Event; the State is rebuilt from events by the Reducer, after the Rules accepted each one.
 */

enum class Role(val key: String, val icon: String) {
    OWNER("owner", "🏛️"), ADMIN("admin", "🛡️"), FIELD("field", "🏍️"), SALES("sales", "🤝"),
    FINANCE("finance", "💰"), QC("qc", "🔍"), SUPPLIER("supplier", "🏭"), PARTNER("partner", "🏢"),
    TECH("tech", "🔧"), CUSTOMER("customer", "🏠");

    /** People outside the company: never see customer contacts or money (original OUTSIDERS rule). */
    val outsider: Boolean get() = this == SUPPLIER || this == PARTNER || this == TECH
    val office: Boolean get() = this == OWNER || this == ADMIN

    companion object {
        fun of(key: String?): Role? = entries.firstOrNull { it.key == key }
        val ALL: Set<Role> = entries.toSet()
    }
}

enum class Phase(val key: String, val icon: String) {
    LEAD("lead", "📍"), SALES("sales", "🤝"), AGREE("agree", "✍️"), SITE("site", "🏗️"),
    MATERIAL("material", "🚚"), INSTALL("install", "🔧"), HANDOVER("handover", "🔑")
}

enum class Stage(val key: String, val phaseIndex: Int, val icon: String) {
    NEW("new", 0, "🆕"), RETURNED("returned", 0, "↩️"),
    ACCEPTED("accepted", 1, "✅"), CONTACTED("contacted", 1, "📞"), REQUIREMENTS("requirements", 1, "📝"),
    QUOTE_APPROVAL("quote_approval", 1, "⏳"), QUOTE_READY("quote_ready", 1, "📄"), QUOTED("quoted", 1, "💬"),
    WON("won", 2, "🎉"), AGREEMENT("agreement", 2, "✍️"), SIGNED("signed", 2, "💰"),
    SITE_PREP("site_prep", 3, "🧱"), QC_SITE("qc_site", 3, "🔍"), SITE_FIX("site_fix", 3, "🛠️"), SITE_OK("site_ok", 3, "✅"),
    MATERIAL_ORDER("material_order", 4, "📦"), DISPATCHED("dispatched", 4, "🚚"), ARRIVED("arrived", 4, "📍"),
    INSTALLING("installing", 5, "🔧"), QC_FINAL("qc_final", 5, "🔍"), INSTALL_FIX("install_fix", 5, "🛠️"),
    FINAL_PAY("final_pay", 6, "💰"), HANDOVER("handover", 6, "✍️"), AMC("amc", 6, "🔑"),
    LOST("lost", -1, "❌");

    val phase: Phase? get() = if (phaseIndex < 0) null else Phase.entries[phaseIndex]

    companion object {
        fun of(key: String?): Stage? = entries.firstOrNull { it.key == key }
        val PRE_WON = setOf(NEW, RETURNED, ACCEPTED, CONTACTED, REQUIREMENTS, QUOTE_APPROVAL, QUOTE_READY, QUOTED)
        val SALES_TALK = setOf(ACCEPTED, CONTACTED, REQUIREMENTS, QUOTE_READY, QUOTED)
        val OPEN = entries.filter { it != LOST && it != AMC }.toSet()
    }
}

/** Site preparation checklist (original PREP_ITEMS, line 5893). */
val PREP_ITEMS = listOf("shaft" to "🧱", "pit" to "⬇️", "power" to "⚡", "clean" to "🧹", "light" to "💡", "barricade" to "🚧")

/** 12 installation SOP steps in order (original SOP, line 5986). */
val SOP_STEPS = listOf(
    "sitesafe" to "🦺", "plumb" to "📏", "brackets" to "🔩", "rails" to "🛤️", "machine" to "⚙️", "car" to "🚡",
    "cwt" to "🧱", "ropes" to "➰", "doors" to "🚪", "wiring" to "🔌", "safetydev" to "🛡️", "test" to "▶️"
)
val QC_SITE_CHECKS = listOf("plumb", "pit_dry", "power3", "barricade", "access")
val QC_FINAL_CHECKS = listOf("ride", "level", "doors", "alarm", "plate", "safetydev", "wiring")

/** Payment purposes and the stage at which each may be paid (original PAY_STAGE, line 5885, payment plan 2). */
val PAY_STAGE = mapOf("token" to Stage.SIGNED, "arrival" to Stage.ARRIVED, "final" to Stage.FINAL_PAY)

val RETURN_REASONS = listOf("photo", "phone", "location", "not_site", "duplicate", "other")
val LOST_REASONS = listOf("price", "competitor", "no_budget", "not_now", "no_shaft", "other")

/** One saved action. [id] is globally unique and doubles as the idempotency key for sync. */
data class Event(
    val id: String,
    val type: String,
    val by: String,
    val pid: String?,
    val t: String,
    val dev: String,
    val v: Int = EVENT_VERSION,
    val data: JsonObject = JsonObject(emptyMap()),
) {
    companion object { const val EVENT_VERSION = 2 }
}

/** A refusal: a dictionary key plus optional {placeholders}. */
data class Err(val key: String, val vars: Map<String, String> = emptyMap())

data class GeoPoint(val lat: Double, val lon: Double, val acc: Double? = null, val t: String? = null)

/** Proof attached to an event: a photo or signature kept on the phone, identified by its SHA-256. */
data class EvidenceRef(
    val id: String, val kind: String, val sha256: String, val t: String,
    val gps: GeoPoint? = null, val tag: String = ""
)

data class Org(val id: String, val name: String, val t: String)

data class User(
    val uid: String, var name: String, var role: Role, var pin: String?, var salt: String?,
    var pinLen: Int = 4, var active: Boolean = true, var phone: String = "", var projectId: String? = null,
    val t: String, val by: String
)

data class StageMark(val stage: Stage, val t: String, val by: String)

data class Contact(val t: String, val by: String, val channel: String, val outcome: String, val note: String)

data class Requirements(
    val type: String = "auto", val stops: Int = 2, val capacity: Int = 6, val finish: String = "ms",
    val door: String = "auto", val power3: Boolean = true, val job: String = "new"
)

data class Quote(
    val v: Int, val t: String, val by: String, val discountPct: Double, val list: Long, val discount: Long,
    val price: Long, val gstPct: Double, val gst: Long, val total: Long, val cost: Long, val margin: Double,
    val why: List<String>, var status: String, val reason: String, val validity: Int,
    val lines: List<Pair<String, Long>>,
    var decision: Triple<String, String, String>? = null, // by, t, reason
    var sentT: String? = null, var acceptedT: String? = null
)

data class Payment(
    val id: String, val purpose: String, val amount: Long, val method: String, val ref: String,
    val recordedBy: String, val t: String, var status: String = "pending",
    var confirmedBy: String? = null, var confirmedAt: String? = null, var reason: String = ""
)

data class Done(val t: String, val by: String, val note: String = "", val ev: List<String> = emptyList())

data class Correction(val id: String, val scope: String, val text: String, val role: String, var done: Done? = null)

data class Incident(val id: String, val type: String, val t: String, val by: String, val note: String, var open: Boolean = true)

data class Ticket(
    val tid: String, val kind: String, val urgency: String, val note: String, val t: String, val by: String,
    var status: String = "open", var assignee: String? = null, var fixNote: String = "", var history: MutableList<String> = mutableListOf()
)

data class Signature(val slot: String, val signer: String, val t: String, val by: String, val evId: String)

class Project(val id: String, val no: String, val t: String, val createdBy: String) {
    var stage: Stage = Stage.NEW
    var since: String = t
    val hist = mutableListOf(StageMark(Stage.NEW, t, createdBy))
    var address = ""; var landmark = ""; var gps: GeoPoint? = null; var tower = ""
    var contactName = ""; var contactPhone = ""; var noPhone = false
    var floors: Int? = null; var shaft = "unknown"; var bType = ""; var note = ""
    var dupOf: List<String> = emptyList()
    var sales: String? = null; var tech: String? = null; var supplier: String? = null; var qc: String? = null; var customer: String? = null
    val contacts = mutableListOf<Contact>()
    var followUp: String? = null
    var req: Requirements? = null
    val quotes = mutableListOf<Quote>()
    var schedule: Map<String, Long> = emptyMap()
    var agreementPrepared: Done? = null
    var agreementSigned: Signature? = null
    val payments = mutableListOf<Payment>()
    val prep = mutableMapOf<String, Done>()
    val corrections = mutableListOf<Correction>()
    var qcSiteOutcome: String? = null
    var drawings = ""
    var materials: List<Pair<String, Int>> = emptyList()
    var dispatch: Map<String, String>? = null
    var arrival: Map<String, String>? = null
    val incidents = mutableListOf<Incident>()
    var materialHanded: Done? = null
    val sop = mutableMapOf<Int, Done>()
    var qcFinalOutcome: String? = null
    val hand = mutableMapOf<String, Signature>()
    var handoverDone: Done? = null
    var warrantyEnd: String? = null
    var amcVisits: List<String> = emptyList()
    var liftCode: String? = null
    val tickets = mutableListOf<Ticket>()
    var lostReason: String? = null
    var returnedReason: String? = null
    /** every event id on this job, oldest first: the timeline */
    val log = mutableListOf<String>()
    val evidence = mutableListOf<EvidenceRef>()

    fun lastQuote(): Quote? = quotes.lastOrNull()
    fun payConfirmed(purpose: String) = payments.any { it.purpose == purpose && it.status == "confirmed" }
    fun payPending(purpose: String) = payments.any { it.purpose == purpose && it.status == "pending" }
    fun sopNext(): Int = (0 until SOP_STEPS.size).firstOrNull { it !in sop } ?: SOP_STEPS.size
    fun openCorrections(scope: String) = corrections.filter { it.scope == scope && it.done == null }
    fun people(): Set<String> = setOfNotNull(createdBy, sales, tech, supplier, qc, customer)
}

class SosAlert(val sid: String, val by: String, val t: String, val gps: GeoPoint?, val pid: String?, var open: Boolean = true, var closedBy: String? = null)

class State {
    var org: Org? = null
    val users = linkedMapOf<String, User>()
    var config = Config()
    val projects = linkedMapOf<String, Project>()
    val jobNos = mutableMapOf<String, String>()
    val byId = mutableMapOf<String, Event>()
    val sos = linkedMapOf<String, SosAlert>()

    fun user(uid: String?): User? = uid?.let { users[it] }
    fun roleOf(uid: String?): Role? = user(uid)?.role
    fun activeUsers(role: Role? = null) = users.values.filter { it.active && (role == null || it.role == role) }
}
