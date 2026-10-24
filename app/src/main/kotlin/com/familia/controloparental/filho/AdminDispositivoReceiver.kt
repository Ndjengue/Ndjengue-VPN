package com.familia.controloparental.filho

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Registo como Device Admin: impede a desinstalação normal do app pelas
 * definições do Android (secção 9 do documento de requisitos). A senha de
 * administrador é pedida separadamente, no SenhaAdminActivity, antes de se
 * permitir desativar esta política.
 */
class AdminDispositivoReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        notificarPaiTentativa(context, "Proteção contra desinstalação ativada")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        // Chamado quando algo tenta desativar o Device Admin (passo prévio à desinstalação).
        notificarPaiTentativa(context, "Tentativa de desativar a proteção contra desinstalação")
        return "Desativar isto permite desinstalar o Controlo Parental. É necessária a senha de administrador."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        notificarPaiTentativa(context, "Proteção contra desinstalação foi desativada")
    }

    private fun notificarPaiTentativa(context: Context, mensagem: String) {
        // Envia um alerta imediato ao pai (secção 9/10). A implementação real
        // usa Firebase Cloud Messaging a partir de uma Cloud Function que reage
        // a uma escrita nesta coleção, ou escreve diretamente e o painel do pai
        // mostra o alerta via listener em tempo real.
        AlertaRepositorioSingleton.registarAlerta(context, mensagem)
    }
}
