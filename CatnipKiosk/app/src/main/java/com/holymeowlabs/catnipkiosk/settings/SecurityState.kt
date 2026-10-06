package com.holymeowlabs.catnipkiosk.settings

import kotlinx.serialization.Serializable

/** Admin PIN verifier and lockout bookkeeping. The PIN itself is never stored. */
@Serializable
data class SecurityState(
    val pinHashB64: String,
    val pinSaltB64: String,
    val iterations: Int,
    val failedAttempts: Int = 0,
    val lockedUntilEpochMs: Long = 0,
)
