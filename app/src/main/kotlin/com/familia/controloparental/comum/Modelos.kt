package com.familia.controloparental.comum

/**
 * Modelos de dados partilhados entre o modo Pai e o modo Filho.
 * Espelham o esquema do Firestore definido no documento de requisitos (secção 12).
 */

enum class RegraApp {
    PERMITIDO,
    BLOQUEADO,
    BLOQUEADO_POR_HORARIO
}

data class Familia(
    val id: String = "",
    val donoUid: String = "",
    val emailRecuperacao: String = "",
    val senhaAdminHash: String = ""
)

data class Dispositivo(
    val id: String = "",
    val nome: String = "",
    val online: Boolean = false,
    val ultimaSincronizacao: Long = 0L,
    val localizacaoAtual: LocalizacaoPonto? = null,
    /** uid da autenticação anónima deste dispositivo — usado nas regras de segurança do Firestore. */
    val uidAutenticacao: String = ""
)

data class LocalizacaoPonto(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestamp: Long = 0L
)

data class AppInstalada(
    val nomePacote: String = "",
    val nome: String = "",
    val iconeBase64: String? = null,
    val instaladoEm: Long = 0L,
    val regra: RegraApp = RegraApp.PERMITIDO,
    val perfilHorarioId: String? = null
)

data class RegraUrl(
    val id: String = "",
    val dominio: String = "",
    val ativo: Boolean = false,
    val perfilHorarioId: String? = null
)

data class JanelaHorario(
    val inicio: String = "07:00", // HH:mm
    val fim: String = "20:00"
)

enum class DiaSemana {
    DOMINGO, SEGUNDA, TERCA, QUARTA, QUINTA, SEXTA, SABADO
}

data class PerfilHorario(
    val id: String = "",
    val nome: String = "",
    val janelasPorDia: Map<DiaSemana, List<JanelaHorario>> = emptyMap()
)

enum class StatusPedidoLiberacao {
    PENDENTE, APROVADO, RECUSADO, EXPIRADO
}

data class PedidoLiberacao(
    val id: String = "",
    val alvo: String = "", // nome do pacote ou domínio
    val status: StatusPedidoLiberacao = StatusPedidoLiberacao.PENDENTE,
    val criadoEm: Long = 0L,
    val duracaoMinutos: Int = 15
)

data class RelatorioDiario(
    val data: String = "", // YYYY-MM-DD
    val tempoEcraPorApp: Map<String, Long> = emptyMap(), // pacote -> ms
    val bloqueiosTentados: List<String> = emptyList(),
    val localizacaoHistoricoDia: List<LocalizacaoPonto> = emptyList()
)

data class Emparelhamento(
    val codigo: String = "",
    val familiaId: String = "",
    val criadoEm: Long = 0L,
    val expiraEm: Long = 0L
)
