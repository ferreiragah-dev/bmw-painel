package com.bmwcockpit.app.obd

enum class ProtocolFamily { CAN_11, CAN_29, LEGACY, UNKNOWN }

/** Payload received from one ECU. [complete] is false when a multi-frame message was cut short. */
class EcuMessage(val ecu: String, val data: ByteArray, val complete: Boolean) {
    override fun toString(): String =
        "EcuMessage($ecu, ${data.joinToString(" ") { "%02X".format(it) }}, complete=$complete)"
}

/**
 * Turns ELM327 output lines (headers enabled, ATH1) into per-ECU payloads.
 *
 *  - CAN 11 bit: "7E8 06 41 0C 1A F8" → ecu 7E8, ISO-TP PCI byte handled, multi-frame (10/21/22…) reassembled.
 *  - CAN 29 bit: "18DAF110 03 41 0D 32" → ecu 18DAF110.
 *  - Legacy (ISO 9141 / KWP / J1850): "48 6B 10 41 0C 1A F8 C4" → ecu 10 (source byte), checksum dropped.
 *
 * Responses from different ECUs are never mixed: each address is reassembled separately.
 */
object ObdFrameParser {

    fun familyFromProtocolNumber(dpn: String): ProtocolFamily {
        val n = dpn.trim().uppercase().removePrefix("A").takeLast(1)
        return when (n) {
            "6", "8", "B" -> ProtocolFamily.CAN_11
            "7", "9", "A", "C" -> ProtocolFamily.CAN_29
            "1", "2", "3", "4", "5" -> ProtocolFamily.LEGACY
            else -> ProtocolFamily.UNKNOWN
        }
    }

    private fun isHex(s: String) = s.isNotEmpty() && s.all { it in '0'..'9' || it in 'A'..'F' }

    private fun guessFamily(hex: String): ProtocolFamily = when {
        hex.length % 2 == 1 -> ProtocolFamily.CAN_11
        hex.length >= 10 && (hex.startsWith("18") || hex.startsWith("1C")) -> ProtocolFamily.CAN_29
        else -> ProtocolFamily.LEGACY
    }

    private fun bytesOf(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        return ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }

    private class Assembly(val ecu: String) {
        var expected = -1
        val buf = ArrayList<Byte>()
        var nextSeq = 1
        var broken = false
        var singleDone = false
    }

    fun parse(lines: List<String>, family: ProtocolFamily): List<EcuMessage> {
        val out = ArrayList<EcuMessage>()
        val assemblies = LinkedHashMap<String, Assembly>()
        for (line in lines) {
            val hex = line.replace(" ", "").uppercase()
            if (!isHex(hex)) continue
            val fam = if (family == ProtocolFamily.UNKNOWN) guessFamily(hex) else family
            when (fam) {
                ProtocolFamily.CAN_11, ProtocolFamily.CAN_29 -> {
                    val hLen = if (fam == ProtocolFamily.CAN_11) 3 else 8
                    if (hex.length < hLen + 2) continue
                    val ecu = hex.substring(0, hLen)
                    val data = bytesOf(hex.substring(hLen)) ?: continue
                    if (data.isEmpty()) continue
                    val pci = data[0].toInt() and 0xFF
                    when (pci shr 4) {
                        0 -> {
                            val len = pci and 0x0F
                            val complete = data.size - 1 >= len && len > 0
                            val payload = data.copyOfRange(1, minOf(data.size, 1 + len))
                            out += EcuMessage(ecu, payload, complete)
                        }
                        1 -> {
                            if (data.size < 2) continue
                            val a = Assembly(ecu)
                            a.expected = ((pci and 0x0F) shl 8) or (data[1].toInt() and 0xFF)
                            for (i in 2 until data.size) a.buf += data[i]
                            assemblies[ecu] = a
                        }
                        2 -> {
                            val a = assemblies[ecu] ?: continue // consecutive frame without first frame
                            val seq = pci and 0x0F
                            if (seq != (a.nextSeq and 0x0F)) a.broken = true
                            a.nextSeq++
                            for (i in 1 until data.size) a.buf += data[i]
                        }
                        else -> Unit // flow control or unknown
                    }
                }
                ProtocolFamily.LEGACY -> {
                    val data = bytesOf(hex) ?: continue
                    if (data.size < 5) continue
                    val ecu = "%02X".format(data[2].toInt() and 0xFF)
                    out += EcuMessage(ecu, data.copyOfRange(3, data.size - 1), true)
                }
                ProtocolFamily.UNKNOWN -> Unit
            }
        }
        for (a in assemblies.values) {
            val complete = !a.broken && a.expected >= 0 && a.buf.size >= a.expected
            val payload = if (a.expected >= 0 && a.buf.size > a.expected) a.buf.subList(0, a.expected) else a.buf
            out += EcuMessage(a.ecu, payload.toByteArray(), complete)
        }
        return out
    }

    /** Messages from [ecu] only, or every message if [ecu] is null. */
    fun parseFrom(lines: List<String>, family: ProtocolFamily, ecu: String?): List<EcuMessage> =
        parse(lines, family).filter { ecu == null || it.ecu == ecu }
}
