package com.familia.controloparental.comum

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Decide, neste preciso momento, se um app ou domínio deve estar bloqueado.
 * Combina as duas camadas descritas no documento de requisitos:
 *   - toggle manual (secção 4): bloqueado/permitido, independente da hora
 *   - horário semanal por app/URL (secção 6): janelas de acesso permitido
 * mais as liberações pontuais aprovadas (secção 7).
 */
object MotorRegras {

    private val formatoHora = DateTimeFormatter.ofPattern("HH:mm")

    private fun diaSemanaAtual(): DiaSemana = when (java.time.LocalDate.now().dayOfWeek) {
        DayOfWeek.SUNDAY -> DiaSemana.DOMINGO
        DayOfWeek.MONDAY -> DiaSemana.SEGUNDA
        DayOfWeek.TUESDAY -> DiaSemana.TERCA
        DayOfWeek.WEDNESDAY -> DiaSemana.QUARTA
        DayOfWeek.THURSDAY -> DiaSemana.QUINTA
        DayOfWeek.FRIDAY -> DiaSemana.SEXTA
        DayOfWeek.SATURDAY -> DiaSemana.SABADO
    }

    private fun dentroDeAlgumaJanela(perfil: PerfilHorario, agora: LocalTime = LocalTime.now()): Boolean {
        val janelas = perfil.janelasPorDia[diaSemanaAtual()] ?: return true // sem horário definido = sempre permitido
        if (janelas.isEmpty()) return true
        return janelas.any { janela ->
            val inicio = LocalTime.parse(janela.inicio, formatoHora)
            val fim = LocalTime.parse(janela.fim, formatoHora)
            !agora.isBefore(inicio) && !agora.isAfter(fim)
        }
    }

    /**
     * @param liberacaoAtivaAte timestamp (epoch ms) até ao qual uma liberação pontual aprovada
     *        continua válida para este alvo, ou null se não houver nenhuma.
     */
    fun estaBloqueadoAgora(
        bloqueioManualAtivo: Boolean,
        perfilHorario: PerfilHorario?,
        liberacaoAtivaAte: Long? = null
    ): Boolean {
        if (liberacaoAtivaAte != null && System.currentTimeMillis() < liberacaoAtivaAte) {
            return false
        }
        val bloqueadoPeloToggle = bloqueioManualAtivo
        val bloqueadoPeloHorario = perfilHorario != null && !dentroDeAlgumaJanela(perfilHorario)
        return bloqueadoPeloToggle || bloqueadoPeloHorario
    }

    fun appEstaBloqueada(app: AppInstalada, perfilHorario: PerfilHorario?, liberacaoAtivaAte: Long? = null): Boolean {
        return when (app.regra) {
            RegraApp.PERMITIDO -> false
            RegraApp.BLOQUEADO -> estaBloqueadoAgora(bloqueioManualAtivo = true, perfilHorario = null, liberacaoAtivaAte = liberacaoAtivaAte)
            RegraApp.BLOQUEADO_POR_HORARIO -> estaBloqueadoAgora(bloqueioManualAtivo = false, perfilHorario = perfilHorario, liberacaoAtivaAte = liberacaoAtivaAte)
        }
    }

    fun urlEstaBloqueada(regra: RegraUrl, perfilHorario: PerfilHorario?, liberacaoAtivaAte: Long? = null): Boolean {
        return estaBloqueadoAgora(bloqueioManualAtivo = regra.ativo, perfilHorario = perfilHorario, liberacaoAtivaAte = liberacaoAtivaAte)
    }
}
