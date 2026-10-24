package com.familia.controloparental.filho

import android.content.Context
import com.familia.controloparental.data.CacheLocal
import com.familia.controloparental.data.FirestoreRepositorio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Pequeno ajudante síncrono-para-assíncrono para disparar alertas a partir de
 * um BroadcastReceiver (que não pode correr suspend functions diretamente).
 */
object AlertaRepositorioSingleton {
    private val escopo = CoroutineScope(Dispatchers.IO)

    fun registarAlerta(context: Context, mensagem: String) {
        escopo.launch {
            val cache = CacheLocal(context)
            val familiaId = cache.lerFamiliaId() ?: return@launch
            val dispositivoId = cache.lerDispositivoId() ?: return@launch
            try {
                FirestoreRepositorio().registarBloqueioTentado(
                    familiaId, dispositivoId,
                    java.time.LocalDate.now().toString(),
                    "ALERTA: $mensagem"
                )
            } catch (_: Exception) {
                // Sem rede: tentativa não notificada de imediato.
            }
        }
    }
}
