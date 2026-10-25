package com.renata.claude.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LatexTest {

    @Test
    fun `converts greek and operators`() {
        assertEquals("α + β", Latex.toUnicode("\\alpha + \\beta"))
        assertEquals("x ≤ y", Latex.toUnicode("x \\leq y"))
    }

    @Test
    fun `converts fraction`() {
        assertEquals("(a)/(b)", Latex.toUnicode("\\frac{a}{b}"))
    }

    @Test
    fun `converts nested fraction`() {
        assertEquals("(1)/((x)/(y))", Latex.toUnicode("\\frac{1}{\\frac{x}{y}}"))
    }

    @Test
    fun `converts sqrt`() {
        assertEquals("√(x + 1)", Latex.toUnicode("\\sqrt{x + 1}"))
    }

    @Test
    fun `keeps text command content`() {
        assertEquals("sin x", Latex.toUnicode("\\text{sin x}"))
    }

    @Test
    fun `converts scripts`() {
        assertEquals("x²", Latex.toUnicode("x^2"))
        assertEquals("x₁", Latex.toUnicode("x_1"))
        assertEquals("aⁿ", Latex.toUnicode("a^{n}"))
    }

    @Test
    fun `converts blackboard bold`() {
        assertEquals("ℝ", Latex.toUnicode("\\mathbb{R}"))
    }

    @Test
    fun `unknown command falls back to its name`() {
        assertTrue(Latex.toUnicode("\\foo").contains("foo"))
    }
}
