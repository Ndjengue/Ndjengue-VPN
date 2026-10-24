package com.familia.controloparental.filho

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.familia.controloparental.R
import com.familia.controloparental.data.CacheLocal
import com.familia.controloparental.data.FirestoreRepositorio
import kotlinx.coroutines.launch

/**
 * Ecrã mostrado por cima de um app/site bloqueado (secção 4) com a opção de
 * pedir liberação pontual ao pai, válida tanto para bloqueio manual como por
 * horário (secção 7).
 */
class EcraBloqueioActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ALVO = "extra_alvo"
        const val EXTRA_TIPO = "extra_tipo"
        const val TIPO_APP = "app"
        const val TIPO_URL = "url"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val alvo = intent.getStringExtra(EXTRA_ALVO) ?: ""

        setContent {
            MaterialTheme {
                var pedidoEnviado by remember { mutableStateOf(false) }
                val escopo = rememberCoroutineScope()

                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.titulo_bloqueio),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(text = alvo)
                        Spacer(Modifier.height(24.dp))

                        if (!pedidoEnviado) {
                            Button(onClick = {
                                escopo.launch {
                                    enviarPedidoLiberacao(alvo)
                                    pedidoEnviado = true
                                }
                            }) {
                                Text(stringResource(R.string.botao_pedir_liberacao))
                            }
                        } else {
                            Text("Pedido enviado. Aguarda a resposta dos teus pais.")
                        }

                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = { finish() }) {
                            Text("Fechar")
                        }
                    }
                }
            }
        }
    }

    private suspend fun enviarPedidoLiberacao(alvo: String) {
        val cache = CacheLocal(applicationContext)
        val familiaId = cache.lerFamiliaId() ?: return
        val dispositivoId = cache.lerDispositivoId() ?: return
        try {
            FirestoreRepositorio().criarPedidoLiberacao(familiaId, dispositivoId, alvo)
        } catch (_: Exception) {
            // Sem rede: o pedido fica por enviar. Produção: fila local + retry ao reconectar.
        }
    }
}
