package com.example.concessionaria

import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun currencyInputPreservesWholeAmountsAndParsesBrazilianSeparators() {
        assertEquals("1000", normalizeCurrencyInput("1000"))
        assertEquals("1000,00", normalizeCurrencyInput("1.000,00"))
        assertEquals(1000.0, parseCurrency("1.000,00")!!, 0.001)
        assertEquals(1000.0, parseCurrency("1.000")!!, 0.001)
        assertEquals(1234.56, parseCurrency("1,234.56")!!, 0.001)
        assertEquals(109.90, parseCurrency("109,90")!!, 0.001)
    }
}