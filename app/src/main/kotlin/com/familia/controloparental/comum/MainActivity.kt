package com.familia.controloparental.comum

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.familia.controloparental.filho.TelaEmparelhamento
import com.familia.controloparental.pai.TelaLoginPai
import com.familia.controloparental.pai.TelaPainelPai
import com.familia.controloparental.pai.TelaRegistoPai

/**
 * Ponto de entrada único do APK (secção 2 do documento de requisitos):
 * no primeiro arranque, a pessoa escolhe "Sou pai" ou "Sou filho", e essa
 * escolha decide a navegação a partir daqui.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "escolha_modo") {
                        composable("escolha_modo") { TelaEscolhaModo(navController) }
                        composable("pai_login") { TelaLoginPai(navController) }
                        composable("pai_registo") { TelaRegistoPai(navController) }
                        composable("pai_painel/{familiaId}") { backStackEntry ->
                            val familiaId = backStackEntry.arguments?.getString("familiaId") ?: ""
                            TelaPainelPai(familiaId)
                        }
                        composable("filho_emparelhamento") { TelaEmparelhamento() }
                    }
                }
            }
        }
    }
}

@Composable
fun TelaEscolhaModo(navController: NavHostController) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Controlo Parental", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = { navController.navigate("pai_login") },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Sou pai") }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = { navController.navigate("filho_emparelhamento") },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Sou filho") }
    }
}
