package pe.edu.uap.inventario.data.repository

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import pe.edu.uap.inventario.data.remote.SupabaseClientProvider

interface AuthRepository {
    suspend fun initializeSession(): Boolean
    suspend fun login(email: String, password: String): Result<Unit>
    suspend fun loginWithGoogle(idToken: String): Result<Unit>
    fun hasActiveSession(): Boolean
    suspend fun logout(): Result<Unit>
    fun getCurrentUserEmail(): String?
    fun getCurrentUserName(): String?
}

class AuthRepositoryImpl : AuthRepository {

    private val auth = SupabaseClientProvider.client.auth

    override suspend fun initializeSession(): Boolean {
        return try {
            auth.loadFromStorage()
            hasActiveSession()
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun login(email: String, password: String): Result<Unit> {
        return try {
            auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loginWithGoogle(idToken: String): Result<Unit> {
        return try {
            auth.signInWith(IDToken) {
                this.idToken = idToken
                this.provider = Google
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun hasActiveSession(): Boolean {
        return auth.currentSessionOrNull() != null
    }

    override suspend fun logout(): Result<Unit> {
        return try {
            auth.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getCurrentUserEmail(): String? {
        return auth.currentUserOrNull()?.email
    }

    override fun getCurrentUserName(): String? {
        val email = getCurrentUserEmail() ?: return null
        return email.substringBefore('@')
            .split('.')
            .filter { it.isNotBlank() }
            .joinToString(" ") { parte ->
                parte.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
    }
}