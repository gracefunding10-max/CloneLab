package com.example

import com.example.clonelab.security.KycDenylistPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KycDenylistPolicyTest {

    @Test
    fun testKnownBankingPackagesAreRefused() {
        assertTrue(KycDenylistPolicy.isDenied("com.chase.sig.android"))
        assertTrue(KycDenylistPolicy.isDenied("com.infonow.bofa"))
        assertTrue(KycDenylistPolicy.isDenied("com.wellsfargo.mobile"))
        assertTrue(KycDenylistPolicy.isDenied("com.revolut.revolut"))
        assertTrue(KycDenylistPolicy.isDenied("com.citi.citimobile"))
    }

    @Test
    fun testIdentityAndLivenessVendorsAreRefused() {
        assertTrue(KycDenylistPolicy.isDenied("com.onfido.sample"))
        assertTrue(KycDenylistPolicy.isDenied("com.jumio.nv"))
        assertTrue(KycDenylistPolicy.isDenied("com.veriff.sdk"))
        assertTrue(KycDenylistPolicy.isDenied("com.idme.wallet"))
    }

    @Test
    fun testPatternKeywordsAreRefused() {
        assertTrue(KycDenylistPolicy.isDenied("com.randombank.banking.app"))
        assertTrue(KycDenylistPolicy.isDenied("org.example.kyc.verification"))
        assertTrue(KycDenylistPolicy.isDenied("io.fintech.identity.check"))
    }

    @Test
    fun testAllowedPackagesPassThrough() {
        assertFalse(KycDenylistPolicy.isDenied("com.mock.qrscanner"))
        assertFalse(KycDenylistPolicy.isDenied("com.mock.socialsnap"))
        assertFalse(KycDenylistPolicy.isDenied("com.example.videoplayer"))
        assertFalse(KycDenylistPolicy.isDenied("com.mock.retrophoto"))
    }
}
