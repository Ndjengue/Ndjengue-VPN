package com.familia.controloparental.servico

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Relança o serviço de sincronização (e, no dispositivo da criança, o filtro
 * VPN) depois de reiniciar o telemóvel, para que o bloqueio continue ativo.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ContextCompat.startForegroundService(
                context, Intent(context, ServicoSincronizacao::class.java)
            )
        }
    }
}
