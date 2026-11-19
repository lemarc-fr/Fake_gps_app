package com.lemarc.fake_gps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class SpeedExpressionTest {

    private fun eval(expr: String, t: Double = 0.0, d: Double = 0.0) =
        SpeedExpression.parse(expr).evaluate(t, d)

    @Test
    fun constants() {
        assertEquals(50.0, eval("50"), 1e-9)
        assertEquals(2.5, eval("2,5"), 1e-9)
        assertEquals(2.5, eval("2.5"), 1e-9)
        assertEquals(1500.0, eval("1.5e3"), 1e-9)
        assertTrue(SpeedExpression.parse(" 12 ").isConstant)
        assertFalse(SpeedExpression.parse("t").isConstant)
    }

    @Test
    fun precedenceAndAssociativity() {
        assertEquals(14.0, eval("2 + 3 * 4"), 1e-9)
        assertEquals(20.0, eval("(2 + 3) * 4"), 1e-9)
        assertEquals(512.0, eval("2^3^2"), 1e-9)
        assertEquals(-4.0, eval("-2^2"), 1e-9)
        assertEquals(1.0, eval("7 % 3"), 1e-9)
        assertEquals(2.0, eval("10 - 5 - 3"), 1e-9)
    }

    @Test
    fun variablesAndFunctions() {
        assertEquals(30 + 20 * sin(1.5), eval("30 + 20*sin(t/10)", t = 15.0), 1e-9)
        assertEquals(130.0, eval("min(5*t, 130)", t = 100.0), 1e-9)
        assertEquals(25.0, eval("min(5*t, 130)", t = 5.0), 1e-9)
        assertEquals(3.0, eval("max(1, 2.5, 3)"), 1e-9)
        assertEquals(Math.PI, eval("PI"), 1e-9)
        assertEquals(7.0, eval("d / 100", d = 700.0), 1e-9)
        assertEquals(2.0, eval("sqrt(4)"), 1e-9)
        assertEquals(8.0, eval("pow(2; 3)"), 1e-9)
    }

    @Test
    fun conditionals() {
        assertEquals(5.0, eval("if(t < 60, 5, 15)", t = 10.0), 1e-9)
        assertEquals(15.0, eval("if(t < 60, 5, 15)", t = 60.0), 1e-9)
        assertEquals(1.0, eval("t >= 3", t = 3.0), 1e-9)
    }

    @Test
    fun randomStaysInRange() {
        repeat(100) {
            val v = eval("rand(10, 20)")
            assertTrue(v in 10.0..20.0)
        }
    }

    @Test
    fun speedIsClampedAtZeroWithoutUpperBound() {
        val e = SpeedExpression.parse("t - 10")
        assertEquals(0.0, e.speedKmh(0.0), 1e-9)
        assertEquals(1e9, e.speedKmh(1e9 + 10), 1.0)
        assertEquals(0.0, SpeedExpression.parse("0/0").speedKmh(0.0), 1e-9)
    }

    @Test
    fun invalidExpressions() {
        for (bad in listOf("", "2 +", "foo(1)", "x", "(1", "sin()", "1 $ 2", "if(1, 2)")) {
            try {
                SpeedExpression.parse(bad)
                throw AssertionError("« $bad » aurait dû être refusée")
            } catch (_: SpeedExpression.ParseException) {
            }
        }
    }
}
