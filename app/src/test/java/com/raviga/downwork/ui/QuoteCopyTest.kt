package com.raviga.downwork.ui

import com.raviga.downwork.ui.quote.complexitySentence
import com.raviga.downwork.ui.quote.settlementLine
import com.raviga.downwork.util.Money
import org.junit.Assert.assertEquals
import org.junit.Test

class QuoteCopyTest {

    @Test fun namesKeepTheirCapitals() {
        assertEquals(
            "Sized for Android app, iOS app, web app and admin panel.",
            complexitySentence(listOf("Android app", "iOS app", "Web app", "Admin panel")),
        )
        assertEquals("Sized for payments and WhatsApp alerts.", complexitySentence(listOf("Payments.", "WhatsApp alerts")))
        assertEquals("", complexitySentence(listOf(" ")))
        // Same case as iOS 6c457ef.
        assertEquals(
            "Sized for Android app, iOS app, web app and Stripe payments.",
            complexitySentence(listOf("Android app", "iOS app", "Web app", "Stripe payments")),
        )
        assertEquals("Sized for Zapier hooks and iPhone widgets.", complexitySentence(listOf("Zapier hooks", "iPhone widgets")))
    }

    @Test fun settlementMatchesIos() {
        assertEquals("You already paid 40 credits. This adds 8 credits.", settlementLine(48, 40))
        assertEquals("You already paid 40 credits. This returns 1 credit to your balance.", settlementLine(39, 40))
        assertEquals("You already paid 1 credit. No change.", settlementLine(1, 1))
    }

    @Test fun dollarsShowCentsOnlyWhenThereAreSome() {
        assertEquals("$320", Money.usd(320.0))
        assertEquals("$1,250", Money.usd(1250.0))
        assertEquals("$99.99", Money.usd(99.99))
        assertEquals("$10", Money.usd(10.0))
    }
}
