package com.familia.controloparental.pai

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.google.firebase.auth.FirebaseAuth
import com.familia.controloparental.data.FirestoreRepositorio
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Registo do pai: cria a conta Firebase Auth e a "família" no Firestore,
 * com a senha de administrador que protegerá a desinstalação (secção 9) e
 * o e-mail de recuperação dessa senha.
 */
@Composable
fun TelaRegistoPai(navController: NavHostController) {
    var email by remember { mutableStateOf("") }
    var senhaConta by remember { mutableStateOf("") }
    var senhaAdmin by remember { mutableStateOf("") }
    var erro by remember { mutableStateOf<String?>(null) }
    var aCarregar by remember { mutableStateOf(false) }
    val escopo = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Criar conta de pai/mãe", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(email, { email = it }, label = { Text("E-mail (usado também para recuperação)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(senhaConta, { senhaConta = it }, label = { Text("Senha da conta") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(senhaAdmin, { senhaAdmin = it }, label = { Text("Senha de administrador (protege a desinstalação)") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        erro?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(16.dp))
        Button(
            enabled = !aCarregar,
            onClick = {
                aCarregar = true
                escopo.launch {
                    try {
                        val resultado = FirebaseAuth.getInstance()
                            .createUserWithEmailAndPassword(email, senhaConta).await()
                        val uid = resultado.user?.uid ?: throw Exception("Falha ao criar utilizador")
                        val familiaId = FirestoreRepositorio().criarFamilia(uid, email, senhaAdmin)
                        navController.navigate("pai_painel/$familiaId")
                    } catch (e: Exception) {
                        erro = e.message ?: "Erro ao criar conta"
                    } finally {
                        aCarregar = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (aCarregar) "A criar..." else "Criar conta") }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { navController.navigate("pai_login") }) { Text("Já tenho conta") }
    }
}

@Composable
fun TelaLoginPai(navController: NavHostController) {
    var email by remember { mutableStateOf("") }
    var senha by remember { mutableStateOf("") }
    var erro by remember { mutableStateOf<String?>(null) }
    var aCarregar by remember { mutableStateOf(false) }
    val escopo = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Entrar", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(email, { email = it }, label = { Text("E-mail") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(senha, { senha = it }, label = { Text("Senha") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        erro?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(16.dp))
        Button(
            enabled = !aCarregar,
            onClick = {
                aCarregar = true
                escopo.launch {
                    try {
                        val resultado = FirebaseAuth.getInstance()
                            .signInWithEmailAndPassword(email, senha).await()
                        val uid = resultado.user?.uid ?: throw Exception("Falha ao entrar")
                        val familiaId = FirestoreRepositorio().buscarFamiliaPorDono(uid)
                            ?: throw Exception("Nenhuma família encontrada para esta conta")
                        navController.navigate("pai_painel/$familiaId")
                    } catch (e: Exception) {
                        erro = e.message ?: "Erro ao entrar"
                    } finally {
                        aCarregar = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (aCarregar) "A entrar..." else "Entrar") }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { navController.navigate("pai_registo") }) { Text("Criar conta") }
    }
}
