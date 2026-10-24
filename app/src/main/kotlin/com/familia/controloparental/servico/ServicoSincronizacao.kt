package com.familia.controloparental.servico

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.familia.controloparental.R
import com.familia.controloparental.data.CacheLocal
import com.familia.controloparental.data.FirestoreRepositorio
import kotlinx.coroutines.*

/**
 * Serviço em primeiro plano que mantém o dispositivo da criança sincronizado
 * com o Firestore em tempo real (secção 8 do documento de requisitos):
 * ouve alterações a apps, regras de URL, perfis de horário e respostas a
 * pedidos de liberação, e atualiza o CacheLocal sempre que chegam — para que
 * o filtro VPN e o serviço de acessibilidade continuem a funcionar mesmo
 * offline, com a última configuração conhecida.
 */
class ServicoSincronizacao : Service() {

    private val escopo = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val repositorio = FirestoreRepositorio()
    private val registos = mutableListOf<com.google.firebase.firestore.ListenerRegistration>()

    companion object {
        private const val ID_CANAL = "sincronizacao"
        private const val ID_NOTIFICACAO = 1001
    }

    override fun onCreate() {
        super.onCreate()
        criarCanalNotificacao()
        startForeground(ID_NOTIFICACAO, construirNotificacao())
        iniciarListeners()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun iniciarListeners() {
        escopo.launch {
            val cache = CacheLocal(applicationContext)
            val familiaId = cache.lerFamiliaId() ?: return@launch
            val dispositivoId = cache.lerDispositivoId() ?: return@launch

            registos += repositorio.observarApps(familiaId, dispositivoId) { apps ->
                escopo.launch { cache.guardarApps(apps) }
            }
            registos += repositorio.observarRegrasUrl(familiaId, dispositivoId) { regras ->
                escopo.launch { cache.guardarRegrasUrl(regras) }
            }
            registos += repositorio.observarPerfisHorario(familiaId, dispositivoId) { perfis ->
                escopo.launch { cache.guardarPerfisHorario(perfis) }
            }

            repositorio.atualizarEstadoDispositivo(familiaId, dispositivoId, online = true)
        }
    }

    private fun criarCanalNotificacao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                ID_CANAL, "Sincronização", NotificationManager.IMPORTANCE_MIN
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
        }
    }

    private fun construirNotificacao(): Notification =
        NotificationCompat.Builder(this, ID_CANAL)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("A manter as regras dos pais sincronizadas")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()

    override fun onDestroy() {
        registos.forEach { it.remove() }
        escopo.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
