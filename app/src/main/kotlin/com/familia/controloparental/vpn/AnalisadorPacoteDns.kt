package com.familia.controloparental.vpn

import java.nio.ByteBuffer

/**
 * Leitura e construção mínimas de pacotes DNS sobre UDP/IP, suficientes para
 * extrair o domínio pedido e responder NXDOMAIN quando bloqueado.
 *
 * Nota de implementação: isto cobre o caso comum (IPv4 + UDP + consulta DNS
 * tipo A/AAAA de um único nome). Um filtro de produção deve também tratar
 * TCP (para respostas DNS grandes) e IPv6; ponto a reforçar no Android Studio
 * com testes contra tráfego real.
 */
object AnalisadorPacoteDns {

    data class ConsultaDns(
        val dominio: String,
        val pacoteIpOriginal: ByteArray,
        val offsetUdp: Int,
        val offsetDns: Int
    )

    /** Devolve a consulta DNS extraída do pacote IP, ou null se não for uma consulta DNS UDP válida. */
    fun extrairConsultaDns(buffer: ByteBuffer): ConsultaDns? {
        val dados = ByteArray(buffer.limit())
        buffer.get(dados, 0, buffer.limit())
        buffer.rewind()

        if (dados.isEmpty()) return null
        val versao = (dados[0].toInt() shr 4) and 0xF
        if (versao != 4) return null // apenas IPv4 nesta versão de referência

        val tamanhoCabecalhoIp = (dados[0].toInt() and 0xF) * 4
        val protocolo = dados[9].toInt() and 0xFF
        if (protocolo != 17) return null // não é UDP

        val offsetUdp = tamanhoCabecalhoIp
        if (dados.size < offsetUdp + 8) return null
        val portoDestino = ((dados[offsetUdp + 2].toInt() and 0xFF) shl 8) or (dados[offsetUdp + 3].toInt() and 0xFF)
        if (portoDestino != 53) return null // não é tráfego DNS

        val offsetDns = offsetUdp + 8
        val dominio = lerNomeDns(dados, offsetDns + 12) ?: return null
        return ConsultaDns(dominio, dados, offsetUdp, offsetDns)
    }

    private fun lerNomeDns(dados: ByteArray, offsetInicial: Int): String? {
        val partes = mutableListOf<String>()
        var offset = offsetInicial
        while (offset < dados.size) {
            val tamanho = dados[offset].toInt() and 0xFF
            if (tamanho == 0) break
            if (offset + 1 + tamanho > dados.size) return null
            partes.add(String(dados, offset + 1, tamanho, Charsets.US_ASCII))
            offset += 1 + tamanho
        }
        return if (partes.isEmpty()) null else partes.joinToString(".")
    }

    /**
     * Constrói uma resposta DNS mínima com RCODE=3 (NXDOMAIN), reaproveitando
     * o ID e a pergunta original, e envolve-a de volta num pacote IP/UDP com
     * origem e destino trocados, pronta a escrever de volta na interface VPN.
     */
    fun construirRespostaNxDomain(consulta: ConsultaDns): ByteArray {
        val dados = consulta.pacoteIpOriginal
        val offsetDns = consulta.offsetDns

        // Cabeçalho DNS: mantém o ID, marca como resposta (QR=1), RCODE=3 (NXDOMAIN), 0 respostas.
        val respostaDns = dados.copyOfRange(offsetDns, dados.size)
        respostaDns[2] = 0x81.toByte() // QR=1, Opcode=0, AA=0, TC=0, RD=1
        respostaDns[3] = 0x83.toByte() // RA=1, Z=0, RCODE=3 (NXDOMAIN)
        // ANCOUNT continua 0 (bytes 6-7 já são 0 na consulta original)

        // Troca IP/porta de origem e destino (resposta vai do "servidor" 10.8.0.1 para o cliente).
        val pacoteIp = dados.copyOfRange(0, offsetDns)
        for (i in 0 until 4) {
            val tmp = pacoteIp[12 + i]
            pacoteIp[12 + i] = pacoteIp[16 + i]
            pacoteIp[16 + i] = tmp
        }
        val offsetUdp = consulta.offsetUdp
        for (i in 0 until 2) {
            val tmp = pacoteIp[offsetUdp + i]
            pacoteIp[offsetUdp + i] = pacoteIp[offsetUdp + 2 + i]
            pacoteIp[offsetUdp + 2 + i] = tmp
        }

        return pacoteIp + respostaDns
        // Nota: checksums de IP/UDP não são recalculados nesta versão de
        // referência — necessário no endurecimento em Android Studio para
        // que alguns resolvedores aceitem o pacote sem o descartar.
    }
}
