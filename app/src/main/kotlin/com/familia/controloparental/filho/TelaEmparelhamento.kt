package com.familia.controloparental.filho

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.familia.controloparental.comum.AppInstalada
import com.familia.controloparental.comum.RegraApp
import com.familia.controloparental.data.CacheLocal
import com.familia.controloparental.data.FirestoreRepositorio
import com.familia.controloparental.servico.ServicoSincronizacao
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Ecrã mostrado no telemóvel da criança: introduzir o código de 6 dígitos
 * gerado pelo pai (secção 3), e, uma vez emparelhado, enviar a lista inicial
 * de apps instaladas (secção 5) e arrancar a sincronização em segundo plano.
 */
@Composable
fun TelaEmparelhamento() {
    val contexto = LocalContext.current
    var codigo by remember { mutableStateOf("") }
    var estado by remember { mutableStateOf(EstadoEmparelhamento.AGUARDANDO_CODIGO) }
    var erro by remember { mutableStateOf<String?>(null) }
    val escopo = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        when (estado) {
            EstadoEmparelhamento.AGUARDANDO_CODIGO -> {
                Text("Pede aos teus pais o código de emparelhamento")
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = codigo,
                    onValueChange = { if (it.length <= 6) codigo = it },
                    label = { Text("Código de 6 dígitos") },
                    modifier = Modifier.fillMaxWidth()
                )
                erro?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.height(16.dp))
                Button(
                    enabled = codigo.length == 6,
                    onClick = {
                        estado = EstadoEmparelhamento.A_EMPARELHAR
                        escopo.launch {
                            try {
                                // O dispositivo da criança nunca tem e-mail/senha próprios;
                                // autentica-se de forma anónima só para poder escrever nos
                                // seus próprios documentos, conforme as regras do Firestore.
                                val uidAnonimo = FirebaseAuth.getInstance().currentUser?.uid
                                    ?: FirebaseAuth.getInstance().signInAnonymously().await().user?.uid
                                    ?: throw Exception("Não foi possível autenticar este dispositivo")

                                val repositorio = FirestoreRepositorio()
                                val familiaId = repositorio.resolverEmparelhamento(codigo)
                                if (familiaId == null) {
                                    erro = "Código inválido ou expirado"
                                    estado = EstadoEmparelhamento.AGUARDANDO_CODIGO
                                    return@launch
                                }
                                val dispositivoId = UUID.randomUUID().toString()
                                repositorio.registarDispositivo(
                                    familiaId, dispositivoId,
                                    nome = "${Build.MANUFACTURER} ${Build.MODEL}",
                                    uidAutenticacao = uidAnonimo
                                )
                                val cache = CacheLocal(contexto)
                                cache.guardarIdentidade(familiaId, dispositivoId)

                                val apps = listarAppsInstaladas(contexto)
                                repositorio.sincronizarAppsInstaladas(familiaId, dispositivoId, apps)

                                ContextCompat.startForegroundService(
                                    contexto, Intent(contexto, ServicoSincronizacao::class.java)
                                )
                                estado = EstadoEmparelhamento.CONCLUIDO
                            } catch (e: Exception) {
                                erro = e.message ?: "Erro ao emparelhar"
                                estado = EstadoEmparelhamento.AGUARDANDO_CODIGO
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Emparelhar") }
            }
            EstadoEmparelhamento.A_EMPARELHAR -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("A emparelhar...")
            }
            EstadoEmparelhamento.CONCLUIDO -> {
                Text("Dispositivo emparelhado! A proteção já está ativa.")
                Spacer(Modifier.height(16.dp))
                Text(
                    "De seguida, ativa a Proteção contra desinstalação e o Serviço de acessibilidade " +
                        "nas Definições do telemóvel, quando pedido.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private enum class EstadoEmparelhamento { AGUARDANDO_CODIGO, A_EMPARELHAR, CONCLUIDO }

private fun listarAppsInstaladas(contexto: android.content.Context): List<AppInstalada> {
    val pm = contexto.packageManager
    val agora = System.currentTimeMillis()
    return pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
        .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 } // ignora apps de sistema
        .map { info ->
            AppInstalada(
                nomePacote = info.packageName,
                nome = pm.getApplicationLabel(info).toString(),
                instaladoEm = agora,
                regra = RegraApp.PERMITIDO
            )
        }
}
