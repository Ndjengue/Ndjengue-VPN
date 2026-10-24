package com.familia.controloparental.filho

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.familia.controloparental.comum.MotorRegras
import com.familia.controloparental.data.CacheLocal
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Deteta qual app está em primeiro plano (via eventos de mudança de janela) e,
 * se essa app estiver bloqueada agora (MotorRegras, que lê o cache local —
 * funciona mesmo offline), abre o EcraBloqueioActivity por cima dela.
 *
 * Também acumula o tempo de ecrã por app para os relatórios (secção 10).
 */
class ServicoAcessibilidade : AccessibilityService() {

    private val escopo = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var pacoteAtual: String? = null
    private var inicioSessaoMs: Long = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pacote = event?.packageName?.toString() ?: return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (pacote == packageName) return // ignora o próprio app de controlo

        registarTrocaDeApp(pacote)
        escopo.launch { avaliarBloqueio(pacote) }
    }

    private fun registarTrocaDeApp(novoPacote: String) {
        val agora = System.currentTimeMillis()
        val anterior = pacoteAtual
        if (anterior != null && inicioSessaoMs > 0) {
            val duracao = agora - inicioSessaoMs
            escopo.launch { acumularTempoEcra(anterior, duracao) }
        }
        pacoteAtual = novoPacote
        inicioSessaoMs = agora
    }

    private suspend fun avaliarBloqueio(pacote: String) {
        val cache = CacheLocal(applicationContext)
        val apps = cache.observarApps().first()
        val app = apps.firstOrNull { it.nomePacote == pacote } ?: return
        val perfis = cache.lerPerfisHorario()
        val perfil = perfis.firstOrNull { it.id == app.perfilHorarioId }

        if (MotorRegras.appEstaBloqueada(app, perfil)) {
            val intent = Intent(this, EcraBloqueioActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EcraBloqueioActivity.EXTRA_ALVO, pacote)
                putExtra(EcraBloqueioActivity.EXTRA_TIPO, EcraBloqueioActivity.TIPO_APP)
            }
            startActivity(intent)
            registarBloqueioTentado(pacote)
        }
    }

    private suspend fun acumularTempoEcra(pacote: String, duracaoMs: Long) {
        if (duracaoMs <= 0) return
        val cache = CacheLocal(applicationContext)
        val familiaId = cache.lerFamiliaId() ?: return
        val dispositivoId = cache.lerDispositivoId() ?: return
        try {
            com.familia.controloparental.data.FirestoreRepositorio().registarTempoEcra(
                familiaId, dispositivoId, LocalDate.now().toString(), pacote, duracaoMs
            )
        } catch (_: Exception) {
            // Sem rede: o tempo de ecrã desta sessão não é registado agora.
            // Produção: acumular numa fila local (Room) e reenviar ao reconectar.
        }
    }

    private suspend fun registarBloqueioTentado(pacote: String) {
        val cache = CacheLocal(applicationContext)
        val familiaId = cache.lerFamiliaId() ?: return
        val dispositivoId = cache.lerDispositivoId() ?: return
        try {
            com.familia.controloparental.data.FirestoreRepositorio().registarBloqueioTentado(
                familiaId, dispositivoId, LocalDate.now().toString(), pacote
            )
        } catch (_: Exception) {
            // idem: reenviar ao reconectar, numa versão futura
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        escopo.cancel()
        super.onDestroy()
    }
}
