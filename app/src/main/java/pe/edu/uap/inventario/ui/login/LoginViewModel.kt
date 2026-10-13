package pe.edu.uap.inventario.ui.login

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.util.Base64
import org.json.JSONObject
import pe.edu.uap.inventario.BuildConfig
import pe.edu.uap.inventario.data.repository.AuthRepository
import pe.edu.uap.inventario.data.repository.AuthRepositoryImpl

private const val DOMINIO_CORPORATIVO = "@autonoma.pe"
private const val MSG_SOLO_CORREO_UA =
    "Solo puedes iniciar sesión con tu correo corporativo de la UA (@autonoma.pe)"

class LoginViewModel(
    private val authRepository: AuthRepository = AuthRepositoryImpl()
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _sessionReady = MutableStateFlow(false)
    val sessionReady: StateFlow<Boolean> = _sessionReady.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    init {
        viewModelScope.launch {
            val hasSession = authRepository.initializeSession()
            _isLoggedIn.value = hasSession
            _sessionReady.value = true
        }
    }

    fun getCurrentUserName(): String? = authRepository.getCurrentUserName()

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _uiState.value = LoginUiState.Idle
            _isLoggedIn.value = false
        }
    }

    fun onLoginSuccess() {
        _isLoggedIn.value = true
        _uiState.value = LoginUiState.Success
    }

    fun loginWithGoogle(context: Context) {
        _uiState.value = LoginUiState.Loading

        viewModelScope.launch {
            try {
                val credentialManager = CredentialManager.create(context)

                // GetSignInWithGoogleOption: la opción oficial para botones que abre el selector de cuentas
                val signInOption = GetSignInWithGoogleOption.Builder(
                    serverClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
                )
                    .setHostedDomainFilter("autonoma.pe")
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(signInOption)
                    .build()

                val result = credentialManager.getCredential(context = context, request = request)
                val credential = result.credential

                if (credential is CustomCredential &&
                    (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL ||
                            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL)
                ) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val idToken = googleIdTokenCredential.idToken

                    val emailGoogle = correoDesdeGoogleIdToken(idToken)
                        ?: googleIdTokenCredential.id
                            .trim()
                            .lowercase()
                            .takeIf { it.contains("@") }
                        ?: ""

                    if (!esCorreoCorporativo(emailGoogle)) {
                        _uiState.value = LoginUiState.Error(MSG_SOLO_CORREO_UA)
                        return@launch
                    }

                    val loginResult = authRepository.loginWithGoogle(idToken)
                    if (loginResult.isSuccess) {
                        val email = authRepository.getCurrentUserEmail()?.lowercase()
                        if (!esCorreoCorporativo(email)) {
                            authRepository.logout()
                            _isLoggedIn.value = false
                            _uiState.value = LoginUiState.Error(MSG_SOLO_CORREO_UA)
                        } else {
                            onLoginSuccess()
                        }
                    } else {
                        val error = loginResult.exceptionOrNull()
                        val detalle = error?.message?.lowercase().orEmpty()
                        _uiState.value = LoginUiState.Error(
                            when {
                                detalle.contains("invalid") ||
                                    detalle.contains("not authorized") ||
                                    detalle.contains("domain") ->
                                    MSG_SOLO_CORREO_UA
                                else ->
                                    error?.message ?: "No se pudo iniciar sesión con Google"
                            }
                        )
                    }
                } else {
                    _uiState.value = LoginUiState.Error("Tipo de credencial no soportado")
                }
            } catch (e: GetCredentialCancellationException) {
                // El usuario simplemente cerró la ventana de Google
                _uiState.value = LoginUiState.Idle
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(
                    e.message ?: "Error al conectar con los servicios de Google"
                )
            }
        }
    }

    fun login(emailInput: String, passwordInput: String) {
        val email = emailInput.trim().lowercase()
        val password = passwordInput.trim()

        if (email.isBlank() || password.isBlank()) {
            _uiState.value = LoginUiState.Error("Por favor ingresa tu correo y contraseña")
            return
        }

        if (!esCorreoCorporativo(email)) {
            _uiState.value = LoginUiState.Error(MSG_SOLO_CORREO_UA)
            return
        }

        _uiState.value = LoginUiState.Loading

        viewModelScope.launch {
            val result = authRepository.login(email, password)
            result.fold(
                onSuccess = {
                    onLoginSuccess()
                },
                onFailure = { error ->
                    val mensaje = when {
                        error.message?.contains("Invalid login credentials", ignoreCase = true) == true ->
                            "Correo o contraseña incorrectos"
                        else ->
                            "Error al iniciar sesión. Verifica tu conexión e intenta nuevamente"
                    }
                    _uiState.value = LoginUiState.Error(mensaje)
                }
            )
        }
    }

    fun resetError() {
        if (_uiState.value is LoginUiState.Error) {
            _uiState.value = LoginUiState.Idle
        }
    }

    private fun esCorreoCorporativo(email: String?): Boolean {
        return !email.isNullOrBlank() && email.trim().lowercase().endsWith(DOMINIO_CORPORATIVO)
    }

    private fun correoDesdeGoogleIdToken(idToken: String): String? {
        return try {
            val payload = idToken.split(".").getOrNull(1) ?: return null
            val decoded = String(
                Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP),
                Charsets.UTF_8
            )
            JSONObject(decoded).optString("email").trim().lowercase().takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }
}