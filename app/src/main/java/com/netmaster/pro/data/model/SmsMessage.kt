package com.netmaster.pro.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SmsMessage(
    /** Router-side message identifier */
    val id: String,
    /** Sender number or name */
    val from: String,
    /** Message body text */
    val body: String,
    /** Epoch millis of message receipt */
    val timestamp: Long,
    /** Whether the message has been read */
    val isRead: Boolean = false,
    /** true = inbox (received), false = sent */
    val isInbox: Boolean = true
)
