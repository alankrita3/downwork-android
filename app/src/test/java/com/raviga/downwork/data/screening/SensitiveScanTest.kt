package com.raviga.downwork.data.screening

import com.raviga.downwork.data.screening.SensitiveScan.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveScanTest {

    private fun kinds(text: String) = SensitiveScan.scan(text).map { it.kind }

    @Test fun ordinaryProjectTextIsClean() {
        val text = "A salon booking app in Delhi. Customers pay with UPI, about 1200 bookings a month, " +
            "call 98765 43210 for details. Launch by 2026-12-01. Budget 5,00,000."
        assertEquals(emptyList<Kind>(), kinds(text))
    }

    @Test fun apiKeysAndTokens() {
        assertEquals(listOf(Kind.API_KEY), kinds("aws key AKIAIOSFODNN7EXAMPLE ok"))
        assertEquals(listOf(Kind.API_KEY), kinds("token ghp_" + "a".repeat(36)))
        assertEquals(listOf(Kind.API_KEY), kinds("use sk-proj-abcdefghijklmnopqrstuvwx"))
        assertEquals(listOf(Kind.API_KEY), kinds("stripe sk_live_" + "4eC39HqLyjWDarjtT1zdp7dc"))
        assertEquals(listOf(Kind.API_KEY), kinds("slack xoxb-1234567890-abcdef"))
        assertEquals(listOf(Kind.API_KEY), kinds("maps AIza" + "SyD-9tSrke72PouQMnMX-a7eZSW0jkFMBWY"))
    }

    @Test fun privateKeyBlock() {
        val text = "here:\n-----BEGIN RSA PRIVATE KEY-----\nMIIEow\nabc\n-----END RSA PRIVATE KEY-----\nthanks"
        val f = SensitiveScan.scan(text).single()
        assertEquals(Kind.PRIVATE_KEY, f.kind)
        assertEquals("here:\n[removed]\nthanks", SensitiveScan.redact(text))
    }

    @Test fun passwordKeepsItsLabel() {
        val text = "Admin login is admin, password: Hunter2! and that's it"
        assertEquals(listOf(Kind.PASSWORD), kinds(text))
        assertEquals("Admin login is admin, password: [removed] and that's it", SensitiveScan.redact(text))
        assertEquals(emptyList<Kind>(), kinds("users reset their password by email"))
    }

    @Test fun cardNumbersNeedLuhn() {
        assertEquals(listOf(Kind.CARD), kinds("card 4111 1111 1111 1111 exp 12/29"))
        assertEquals(listOf(Kind.CARD), kinds("card 4111-1111-1111-1111"))
        assertEquals(emptyList<Kind>(), kinds("order 4111 1111 1111 1112"))
    }

    @Test fun aadhaarNeedsVerhoeffAndPanIsUppercase() {
        // 2341 2341 2346 passes Verhoeff; 2341 2341 2345 does not.
        assertTrue(SensitiveScan.verhoeff("234123412346"))
        assertFalse(SensitiveScan.verhoeff("234123412345"))
        assertEquals(listOf(Kind.AADHAAR), kinds("aadhaar 2341 2341 2346"))
        assertEquals(emptyList<Kind>(), kinds("ref 2341 2341 2345"))
        assertEquals(listOf(Kind.PAN), kinds("PAN ABCPE1234F please"))
        assertEquals(emptyList<Kind>(), kinds("abcpe1234f"))
    }

    @Test fun titleListsUpToTwoKinds() {
        val one = SensitiveScan.scan("4111 1111 1111 1111")
        assertEquals("This looks like it includes a card number.", SensitiveScan.title(one))
        val two = SensitiveScan.scan("4111 1111 1111 1111 and AKIAIOSFODNN7EXAMPLE")
        assertEquals("This looks like it includes a card number and an API key or token.", SensitiveScan.title(two))
        val three = SensitiveScan.scan("4111 1111 1111 1111, AKIAIOSFODNN7EXAMPLE, PAN ABCPE1234F")
        assertEquals("This looks like it includes a card number, an API key or token and other secrets.", SensitiveScan.title(three))
    }

    @Test fun redactReplacesEveryFinding() {
        val text = "key AKIAIOSFODNN7EXAMPLE card 4111111111111111 pan ABCPE1234F"
        assertEquals("key [removed] card [removed] pan [removed]", SensitiveScan.redact(text))
    }
}
