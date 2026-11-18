package com.netmaster.pro.presentation.analyzer

fun detectWifiStandard(capabilities: String, frequency: Int): String = when {
    capabilities.contains("EHT") || capabilities.contains("WIF7") -> "Wi-Fi 7"
    capabilities.contains("HE") || capabilities.contains("WIF6") -> "Wi-Fi 6/6E"
    capabilities.contains("VHT") -> "Wi-Fi 5 (802.11ac)"
    capabilities.contains("HT") -> "Wi-Fi 4 (802.11n)"
    frequency > 5000 -> "Wi-Fi 5 (802.11a/ac)"
    else -> "Wi-Fi 4 (802.11b/g/n)"
}

fun frequencyToChannel(freq: Int): Int = when {
    freq in 2412..2484 -> (freq - 2412) / 5 + 1
    freq in 5170..5825 -> (freq - 5000) / 5
    freq in 5955..7115 -> (freq - 5955) / 5 + 1
    else -> 0
}

fun channelBandwidthLabel(channelWidth: Int): String = when (channelWidth) {
    0 -> "20 MHz"
    1 -> "40 MHz"
    2 -> "80 MHz"
    3 -> "160 MHz"
    4 -> "80+80 MHz"
    5 -> "320 MHz"
    else -> "غير معروف"
}

fun signalStrengthLabel(rssi: Int): String = when {
    rssi >= -50 -> "ممتاز"
    rssi >= -60 -> "جيد"
    rssi >= -70 -> "متوسط"
    rssi >= -80 -> "ضعيف"
    else -> "سيئ جداً"
}

fun bestChannel2GHz(scanResults: List<NeighborAp>): Int {
    val channelLoad = IntArray(15) { 0 }
    for (ap in scanResults) {
        val ch = ap.channel
        for (overlap in -2..2) {
            val c = ch + overlap
            if (c in 1..14) channelLoad[c] += 1
        }
    }
    return listOf(1, 6, 11).minByOrNull { channelLoad[it] } ?: 1
}

fun bestChannel5GHz(scanResults: List<NeighborAp>): Int {
    val channels5G = listOf(36, 40, 44, 48, 52, 56, 60, 64, 100, 104, 108, 112, 116, 120, 124, 128, 132, 136, 140, 144, 149, 153, 157, 161, 165)
    val channelLoad = mutableMapOf<Int, Int>()
    channels5G.forEach { channelLoad[it] = 0 }
    for (ap in scanResults.filter { it.frequency > 5000 }) {
        val ch = ap.channel
        channelLoad[ch] = (channelLoad[ch] ?: 0) + 1
    }
    return channelLoad.minByOrNull { it.value }?.key ?: 36
}
