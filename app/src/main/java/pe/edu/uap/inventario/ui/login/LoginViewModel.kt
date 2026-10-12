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
import pe.edu.uap.inventario.BuildConfig
import pe.edu.uap.inventario.data.repository.AuthRepository
import pe.edu.uap.inventario.data.repository.AuthRepositoryImpl

class LoginViewModel(
    private val authRepository: AuthRepository = AuthRepositoryImpl()
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(authRepository.hasActiveSession())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

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
                ).build()

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

                    val loginResult = authRepository.loginWithGoogle(idToken)
                    loginResult.fold(
                        onSuccess = {
                            onLoginSuccess()
                        },
                        onFailure = { error ->
                            _uiState.value = LoginUiState.Error(
                                error.message ?: "No se pudo iniciar sesión con Google en Supabase"
                            )
                        }
                    )
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

        if (!email.endsWith("@autonoma.pe")) {
            _uiState.value = LoginUiState.Error("Acceso restringido: utiliza tu correo institucional (@autonoma.pe)")
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
}