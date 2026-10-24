package com.familia.controloparental.filho

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.familia.controloparental.data.CacheLocal
import com.familia.controloparental.data.FirestoreRepositorio
import kotlinx.coroutines.launch

/**
 * Pede a senha de administrador antes de permitir desativar o Device Admin
 * ou desinstalar o app (secção 9). A validação é local (funciona offline);
 * a senha pode também ser alterada remotamente pelo painel do pai, o que
 * atualiza o hash guardado aqui via sincronização do Firestore.
 */
class SenhaAdminActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var senha by remember { mutableStateOf("") }
                var erro by remember { mutableStateOf<String?>(null) }
                val escopo = rememberCoroutineScope()

                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Introduz a senha de administrador para continuar")
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = senha,
                            onValueChange = { senha = it },
                            label = { Text("Senha") },
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                        )
                        erro?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = {
                            escopo.launch {
                                if (validarSenha(senha)) {
                                    desativarAdminEPermitirDesinstalacao()
                                    finish()
                                } else {
                                    erro = "Senha incorreta"
                                }
                            }
                        }) {
                            Text("Confirmar")
                        }
                    }
                }
            }
        }
    }

    private suspend fun validarSenha(senha: String): Boolean {
        val cache = CacheLocal(applicationContext)
        val hashGuardado = cache.lerSenhaAdminHash() ?: return false
        val hashIntroduzido = FirestoreRepositorio().hashSenha(senha)
        return hashGuardado == hashIntroduzido
    }

    private fun desativarAdminEPermitirDesinstalacao() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, AdminDispositivoReceiver::class.java)
        if (dpm.isAdminActive(admin)) {
            dpm.removeActiveAdmin(admin)
        }
    }
}
