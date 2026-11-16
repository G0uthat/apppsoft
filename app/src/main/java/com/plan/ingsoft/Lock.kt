package com.plan.ingsoft

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Bloqueo de edición: se abre con la contraseña o resolviendo un puzzle (10 min). */
object Lock {
    private var until = 0L

    fun isUnlocked(): Boolean = System.currentTimeMillis() < until
    fun unlock(minutes: Int = 10) {
        until = System.currentTimeMillis() + minutes * 60_000L
    }

    fun lockNow() {
        until = 0L
    }

    fun hasPassword(st: Store): Boolean = st.pwHash() != null

    private fun hash(salt: String, pw: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest((salt + pw).toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun setPassword(st: Store, pw: String) {
        val saltBytes = ByteArray(16)
        SecureRandom().nextBytes(saltBytes)
        val salt = Base64.getEncoder().encodeToString(saltBytes)
        st.savePassword(salt, hash(salt, pw))
    }

    fun check(st: Store, pw: String): Boolean {
        val salt = st.pwSalt() ?: return false
        val h = st.pwHash() ?: return false
        return hash(salt, pw) == h
    }
}
