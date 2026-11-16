package com.atelierpro.couture.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinSecurityHelperTest {

    @Test
    fun saltedSha256HashVerifiesCorrectPinAndRejectsWrongPin() {
        val salt = PinSecurityHelper.generateSalt()
        val hash = PinSecurityHelper.hashPin("4829", salt)

        assertTrue(PinSecurityHelper.verifyPin("4829", hash, salt, ""))
        assertFalse(PinSecurityHelper.verifyPin("1234", hash, salt, ""))
        assertFalse(PinSecurityHelper.verifyPin("482", hash, salt, ""))
    }

    @Test
    fun distinctSaltsProduceDifferentHashesForSamePin() {
        val salt1 = PinSecurityHelper.generateSalt()
        val salt2 = PinSecurityHelper.generateSalt()
        val hash1 = PinSecurityHelper.hashPin("1234", salt1)
        val hash2 = PinSecurityHelper.hashPin("1234", salt2)

        assertNotEquals(salt1, salt2)
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun legacyPlainFallbackWorksOnlyWhenHashIsEmpty() {
        assertTrue(PinSecurityHelper.verifyPin("9999", "", "", "9999"))
        assertFalse(PinSecurityHelper.verifyPin("0000", "", "", "9999"))
    }
}
