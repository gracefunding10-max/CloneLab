package com.example.clonelab.security

/**
 * Hard-gate policy enforcement for CloneLab virtual container.
 *
 * Strict Compliance Guardrail:
 * Camera feed redirection MUST NEVER attach to banking, financial, government,
 * identity verification, liveness check, or KYC (Know Your Customer) applications.
 *
 * If a target app package belongs to this category, [isDenied] returns true,
 * and the container refuses to attach the fake camera stack, logging an audit refusal.
 */
object KycDenylistPolicy {

    private val DENYLISTED_EXACT_PACKAGES = setOf(
        // Common Banking Apps
        "com.chase.sig.android",
        "com.infonow.bofa",
        "com.wellsfargo.mobile",
        "com.citi.citimobile",
        "com.capitalone.mobile",
        "com.revolut.revolut",
        "com.monzo.android",
        "com.n26.android",
        "com.barclays.android.barclaysmobilebanking",
        "com.santander.app",
        "com.hsbc.hsbcukmobilebanking",
        // Fintech & Crypto
        "com.coinbase.android",
        "com.binance.dev",
        "com.kraken.invest.app",
        "com.robinhood.android",
        // Identity Verification / KYC SDKs / Liveness check vendors
        "com.onfido.sample",
        "com.jumio.nv",
        "com.veriff.sdk",
        "com.idme.wallet",
        "com.yoti.mobile.android",
        "com.au10tix.smartmobilesdk",
        "com.shuftipro.android",
        "com.sumsub.idensic.sample",
        "com.facetech.sdk",
        // Government & Passports
        "gov.dhs.cbp.mpc",
        "gov.login.app",
        "gov.ssa.mobile",
        "uk.gov.identity.passport"
    )

    private val SUSPICIOUS_SUBSTRINGS = listOf(
        ".bank.",
        ".banking.",
        ".kyc.",
        ".identity.",
        ".liveness.",
        ".passbase.",
        ".veriff.",
        ".onfido.",
        ".jumio."
    )

    fun isDenied(packageName: String): Boolean {
        val lower = packageName.lowercase()
        if (DENYLISTED_EXACT_PACKAGES.contains(lower)) {
            return true
        }
        for (pattern in SUSPICIOUS_SUBSTRINGS) {
            if (lower.contains(pattern)) {
                return true
            }
        }
        return false
    }

    fun getDenialReason(packageName: String): String {
        return "Security Guardrail Violation: Target package '$packageName' is categorized under " +
            "Banking, Financial, Government, or Identity Verification (KYC/Liveness). " +
            "CloneLab CameraRedirectModule strictly refuses to attach to this application category."
    }
}
