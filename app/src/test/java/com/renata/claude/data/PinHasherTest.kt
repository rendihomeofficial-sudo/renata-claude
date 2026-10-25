package com.renata.claude.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app lock stores a PIN as a salted PBKDF2 hash.
 *
 * The PIN itself is never written to disk, so these tests cover the hashing
 * rules instead of the Android preference file.
 */
class PinHasherTest {

    @Test
    fun newSaltIsRandomAndHexEncoded() {
        val first = PinHasher.newSalt()
        val second = PinHasher.newSalt()
        assertEquals(32, first.length)
        assertTrue(first.all { it in "0123456789abcdef" })
        assertNotEquals(first, second)
    }

    @Test
    fun hashIsStableForTheSameSalt() {
        val salt = PinHasher.newSalt()
        assertEquals(PinHasher.hash("1234", salt), PinHasher.hash("1234", salt))
    }

    @Test
    fun hashChangesWithTheSalt() {
        val first = PinHasher.hash("1234", PinHasher.newSalt())
        val second = PinHasher.hash("1234", PinHasher.newSalt())
        assertNotEquals(first, second)
    }

    @Test
    fun hashIsLongEnoughAndHexEncoded() {
        val hash = PinHasher.hash("1234", PinHasher.newSalt())
        assertEquals(64, hash.length)
        assertTrue(hash.all { it in "0123456789abcdef" })
    }

    @Test
    fun matchesAcceptsTheRightPinOnly() {
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash("2580", salt)
        assertTrue(PinHasher.matches("2580", salt, hash))
        assertFalse(PinHasher.matches("2581", salt, hash))
        assertFalse(PinHasher.matches("", salt, hash))
    }

    @Test
    fun matchesRefusesBlankStoredValues() {
        val salt = PinHasher.newSalt()
        assertFalse(PinHasher.matches("2580", salt, ""))
        assertFalse(PinHasher.matches("2580", "", PinHasher.hash("2580", salt)))
    }
}
