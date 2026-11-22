package com.aaideas.krossvord.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.MonthDay

class GreetingTest {
    @Test fun helloByTimeOfDay() {
        assertEquals("Sabahınız xeyir, Ana!", Greeting.hello(8, "Ana"))
        assertEquals("Axşamınız xeyir!", Greeting.hello(20, " "))
        assertEquals("Salam, Ata!", Greeting.hello(2, "Ata"))
    }

    @Test fun birthdayStoredAndMatched() {
        val md = MonthDay.of(3, 21)
        assertEquals(md, Greeting.parse(Greeting.format(md)))
        assertNull(Greeting.parse("junk"))
        assertTrue(Greeting.isBirthday(LocalDate.of(2027, 3, 21), md))
        assertFalse(Greeting.isBirthday(LocalDate.of(2027, 3, 22), md))
        assertFalse(Greeting.isBirthday(LocalDate.of(2027, 3, 21), null))
    }

    @Test fun leapDayBirthdayOn28FebruaryOtherYears() {
        val md = MonthDay.of(2, 29)
        assertTrue(Greeting.isBirthday(LocalDate.of(2027, 2, 28), md))
        assertFalse(Greeting.isBirthday(LocalDate.of(2028, 2, 28), md))
        assertTrue(Greeting.isBirthday(LocalDate.of(2028, 2, 29), md))
    }

    @Test fun giftAddsOneLifeEvenWhenFull() {
        assertEquals(4, Lives(3, 0).gift(1000).count)
        assertEquals(1, Lives(0, 0).gift(1000).count)
        assertEquals(Lives.MAX, Lives(Lives.MAX, 0).gift(1000).count)
    }
}
