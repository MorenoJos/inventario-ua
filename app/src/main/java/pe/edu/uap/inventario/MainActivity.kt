package pe.edu.uap.inventario

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import pe.edu.uap.inventario.ui.login.LoginScreen
import pe.edu.uap.inventario.ui.login.LoginViewModel
import pe.edu.uap.inventario.ui.theme.UAInventarioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UAInventarioTheme {
                MainApp()
            }
        }
    }
}

@Composable
fun MainApp(viewModel: LoginViewModel = viewModel()) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()

    if (isLoggedIn) {
        HomeScreen(
            userName = viewModel.getCurrentUserName() ?: "Usuario",
            onLogout = {
                viewModel.logout()
            }
        )
    } else {
        LoginScreen(
            onLoginSuccess = {
                // El ViewModel ya actualiza isLoggedIn automáticamente
            },
            viewModel = viewModel
        )
    }
}

@Composable
fun HomeScreen(
    userName: String,
    onLogout: () -> Unit
) {
    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "¡Bienvenido, $userName!",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Sesión activa en Almacén 5C",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = onLogout,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Cerrar sesión")
                }
            }
        }
    }
}