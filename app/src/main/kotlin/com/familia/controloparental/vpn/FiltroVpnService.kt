package com.familia.controloparental.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.familia.controloparental.comum.MotorRegras
import com.familia.controloparental.data.CacheLocal
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlinx.coroutines.flow.first

/**
 * Filtra o tráfego DNS do dispositivo para bloquear domínios marcados como
 * bloqueados (secção 4 do documento de requisitos). Em vez de interceptar
 * HTTPS (frágil e inseguro, como descrito no documento), este serviço:
 *
 *  1. Cria uma interface VPN local que captura todo o tráfego do dispositivo.
 *  2. Deixa passar o tráfego normal, mas resolve e filtra os pedidos DNS:
 *     se o domínio pedido (ou um dos seus subdomínios) está bloqueado agora
 *     (via MotorRegras, que combina toggle + horário + liberações), devolve
 *     NXDOMAIN em vez de encaminhar o pedido — o telemóvel nunca descobre o
 *     IP do domínio bloqueado, logo não consegue ligar-se a ele.
 *
 * Esta é uma implementação de referência: um filtro DNS real e robusto
 * (parsing completo de pacotes UDP/DNS, suporte a DNS-over-HTTPS dos
 * browsers, etc.) é mais extenso — aqui fica a estrutura e os pontos de
 * extensão principais, prontos para endurecer no Android Studio.
 */
class FiltroVpnService : VpnService() {

    private var interfaceVpn: ParcelFileDescriptor? = null
    private var job: Job? = null
    private val escopo = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "FiltroVpnService"
        const val ACAO_PARAR = "com.familia.controloparental.PARAR_VPN"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACAO_PARAR) {
            pararVpn()
            return START_NOT_STICKY
        }
        iniciarVpn()
        return START_STICKY
    }

    private fun iniciarVpn() {
        if (interfaceVpn != null) return

        val builder = Builder()
            .addAddress("10.8.0.2", 32)
            .addDnsServer("10.8.0.1") // o próprio serviço atua como resolvedor DNS
            .addRoute("0.0.0.0", 0)
            .setSession("Controlo Parental")
            .setBlocking(false)

        interfaceVpn = builder.establish()
        Log.i(TAG, "VPN de filtragem iniciada")

        job = escopo.launch {
            correrLoopDeFiltragem()
        }
    }

    private fun pararVpn() {
        job?.cancel()
        interfaceVpn?.close()
        interfaceVpn = null
        stopSelf()
    }

    private suspend fun correrLoopDeFiltragem() {
        val vpnInterface = interfaceVpn ?: return
        val entrada = FileInputStream(vpnInterface.fileDescriptor)
        val saida = FileOutputStream(vpnInterface.fileDescriptor)
        val cache = CacheLocal(applicationContext)
        val buffer = ByteBuffer.allocate(32767)

        while (currentCoroutineContext().isActive) {
            buffer.clear()
            val tamanho = try {
                entrada.read(buffer.array())
            } catch (e: Exception) {
                Log.w(TAG, "Erro a ler pacote da VPN: ${e.message}")
                break
            }
            if (tamanho <= 0) {
                delay(10)
                continue
            }
            buffer.limit(tamanho)

            val pacote = AnalisadorPacoteDns.extrairConsultaDns(buffer)
            if (pacote != null) {
                val dominio = pacote.dominio.removeSuffix(".")
                val regrasUrl = cache.observarRegrasUrl().first()
                val perfis = cache.lerPerfisHorario()

                val regraCorrespondente = regrasUrl.firstOrNull { regra ->
                    dominio == regra.dominio || dominio.endsWith(".${regra.dominio}")
                }

                val bloqueado = regraCorrespondente != null && MotorRegras.urlEstaBloqueada(
                    regraCorrespondente,
                    perfis.firstOrNull { it.id == regraCorrespondente.perfilHorarioId }
                )

                if (bloqueado) {
                    // Responde com NXDOMAIN em vez de encaminhar — o domínio fica inacessível.
                    val resposta = AnalisadorPacoteDns.construirRespostaNxDomain(pacote)
                    saida.write(resposta)
                    continue
                }
            }

            // Tráfego normal (ou não-DNS): encaminha para um resolvedor real.
            // Produção: reencaminhar via um DatagramChannel protect()ido para 8.8.8.8:53
            // ou para o DNS original da rede, e devolver a resposta à interface VPN.
        }
    }

    override fun onDestroy() {
        pararVpn()
        super.onDestroy()
    }
}
