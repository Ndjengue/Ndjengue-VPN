package com.familia.controloparental.data

import com.familia.controloparental.comum.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import kotlin.random.Random

/**
 * Camada de acesso ao Firestore. Único ponto de leitura/escrita remota;
 * tanto o modo Pai como o modo Filho passam por aqui.
 *
 * Estrutura (ver secção 12 do documento de requisitos):
 *   familias/{familiaId}
 *   familias/{familiaId}/dispositivos/{dispositivoId}
 *   familias/{familiaId}/dispositivos/{dispositivoId}/apps/{nomePacote}
 *   familias/{familiaId}/dispositivos/{dispositivoId}/regrasUrl/{regraId}
 *   familias/{familiaId}/dispositivos/{dispositivoId}/perfisHorario/{perfilId}
 *   familias/{familiaId}/dispositivos/{dispositivoId}/pedidosLiberacao/{pedidoId}
 *   familias/{familiaId}/dispositivos/{dispositivoId}/relatorios/{data}
 *   emparelhamentos/{codigo}
 */
class FirestoreRepositorio(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private fun dispositivoRef(familiaId: String, dispositivoId: String) =
        db.collection("familias").document(familiaId)
            .collection("dispositivos").document(dispositivoId)

    // ---------- Família ----------

    suspend fun criarFamilia(donoUid: String, emailRecuperacao: String, senhaAdmin: String): String {
        val familiaId = db.collection("familias").document().id
        val familia = mapOf(
            "donoUid" to donoUid,
            "emailRecuperacao" to emailRecuperacao,
            "senhaAdminHash" to hashSenha(senhaAdmin)
        )
        db.collection("familias").document(familiaId).set(familia).await()
        return familiaId
    }

    /** Uma conta de pai gere uma única família (ver secção 11: dispositivos ilimitados DENTRO dela). */
    suspend fun buscarFamiliaPorDono(donoUid: String): String? {
        val resultado = db.collection("familias")
            .whereEqualTo("donoUid", donoUid)
            .limit(1)
            .get()
            .await()
        return resultado.documents.firstOrNull()?.id
    }

    fun hashSenha(senha: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(senha.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // ---------- Emparelhamento ----------

    suspend fun gerarCodigoEmparelhamento(familiaId: String): String {
        val codigo = (100000..999999).random().toString()
        val agora = System.currentTimeMillis()
        val emparelhamento = mapOf(
            "familiaId" to familiaId,
            "criadoEm" to agora,
            "expiraEm" to agora + 5 * 60 * 1000L // 5 minutos
        )
        db.collection("emparelhamentos").document(codigo).set(emparelhamento).await()
        return codigo
    }

    /** Devolve o familiaId se o código for válido e não tiver expirado; apaga-o em seguida. */
    suspend fun resolverEmparelhamento(codigo: String): String? {
        val doc = db.collection("emparelhamentos").document(codigo).get().await()
        if (!doc.exists()) return null
        val expiraEm = doc.getLong("expiraEm") ?: 0L
        if (System.currentTimeMillis() > expiraEm) {
            db.collection("emparelhamentos").document(codigo).delete()
            return null
        }
        val familiaId = doc.getString("familiaId")
        db.collection("emparelhamentos").document(codigo).delete()
        return familiaId
    }

    suspend fun registarDispositivo(familiaId: String, dispositivoId: String, nome: String, uidAutenticacao: String) {
        dispositivoRef(familiaId, dispositivoId).set(
            mapOf(
                "nome" to nome,
                "online" to true,
                "ultimaSincronizacao" to System.currentTimeMillis(),
                "uidAutenticacao" to uidAutenticacao
            )
        ).await()
    }

    // ---------- Dispositivos (painel do pai) ----------

    fun observarDispositivos(
        familiaId: String,
        aoAtualizar: (List<Dispositivo>) -> Unit
    ): ListenerRegistration {
        return db.collection("familias").document(familiaId).collection("dispositivos")
            .addSnapshotListener { snap, _ ->
                val lista = snap?.documents?.map { d ->
                    Dispositivo(
                        id = d.id,
                        nome = d.getString("nome") ?: "",
                        online = d.getBoolean("online") ?: false,
                        ultimaSincronizacao = d.getLong("ultimaSincronizacao") ?: 0L
                    )
                } ?: emptyList()
                aoAtualizar(lista)
            }
    }

    suspend fun atualizarEstadoDispositivo(familiaId: String, dispositivoId: String, online: Boolean) {
        dispositivoRef(familiaId, dispositivoId).update(
            mapOf(
                "online" to online,
                "ultimaSincronizacao" to System.currentTimeMillis()
            )
        ).await()
    }

    // ---------- Apps instaladas ----------

    fun observarApps(
        familiaId: String,
        dispositivoId: String,
        aoAtualizar: (List<AppInstalada>) -> Unit
    ): ListenerRegistration {
        return dispositivoRef(familiaId, dispositivoId).collection("apps")
            .addSnapshotListener { snap, _ ->
                val lista = snap?.documents?.map { it.toAppInstalada() } ?: emptyList()
                aoAtualizar(lista)
            }
    }

    suspend fun sincronizarAppsInstaladas(familiaId: String, dispositivoId: String, apps: List<AppInstalada>) {
        val colecao = dispositivoRef(familiaId, dispositivoId).collection("apps")
        val lote = db.batch()
        apps.forEach { app ->
            lote.set(
                colecao.document(app.nomePacote),
                mapOf(
                    "nome" to app.nome,
                    "pacote" to app.nomePacote,
                    "icone" to app.iconeBase64,
                    "instaladoEm" to app.instaladoEm,
                    "regra" to app.regra.name
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )
        }
        lote.commit().await()
    }

    suspend fun definirRegraApp(familiaId: String, dispositivoId: String, nomePacote: String, regra: RegraApp, perfilHorarioId: String?) {
        dispositivoRef(familiaId, dispositivoId).collection("apps").document(nomePacote)
            .update(
                mapOf(
                    "regra" to regra.name,
                    "perfilHorarioId" to perfilHorarioId
                )
            ).await()
    }

    // ---------- Regras de URL ----------

    fun observarRegrasUrl(
        familiaId: String,
        dispositivoId: String,
        aoAtualizar: (List<RegraUrl>) -> Unit
    ): ListenerRegistration {
        return dispositivoRef(familiaId, dispositivoId).collection("regrasUrl")
            .addSnapshotListener { snap, _ ->
                val lista = snap?.documents?.map { d ->
                    RegraUrl(
                        id = d.id,
                        dominio = d.getString("dominio") ?: "",
                        ativo = d.getBoolean("ativo") ?: false,
                        perfilHorarioId = d.getString("perfilHorarioId")
                    )
                } ?: emptyList()
                aoAtualizar(lista)
            }
    }

    suspend fun adicionarRegraUrl(familiaId: String, dispositivoId: String, dominio: String): String {
        val ref = dispositivoRef(familiaId, dispositivoId).collection("regrasUrl").document()
        ref.set(mapOf("dominio" to dominio, "ativo" to true, "perfilHorarioId" to null)).await()
        return ref.id
    }

    suspend fun alternarRegraUrl(familiaId: String, dispositivoId: String, regraId: String, ativo: Boolean) {
        dispositivoRef(familiaId, dispositivoId).collection("regrasUrl").document(regraId)
            .update("ativo", ativo).await()
    }

    // ---------- Perfis de horário ----------

    suspend fun criarPerfilHorario(familiaId: String, dispositivoId: String, perfil: PerfilHorario): String {
        val ref = dispositivoRef(familiaId, dispositivoId).collection("perfisHorario").document()
        val janelas = perfil.janelasPorDia.entries.associate { (dia, janelas) ->
            dia.name to janelas.map { mapOf("inicio" to it.inicio, "fim" to it.fim) }
        }
        ref.set(mapOf("nome" to perfil.nome, "janelasPorDia" to janelas)).await()
        return ref.id
    }

    fun observarPerfisHorario(
        familiaId: String,
        dispositivoId: String,
        aoAtualizar: (List<PerfilHorario>) -> Unit
    ): ListenerRegistration {
        return dispositivoRef(familiaId, dispositivoId).collection("perfisHorario")
            .addSnapshotListener { snap, _ ->
                val lista = snap?.documents?.map { it.toPerfilHorario() } ?: emptyList()
                aoAtualizar(lista)
            }
    }

    // ---------- Pedidos de liberação ----------

    suspend fun criarPedidoLiberacao(familiaId: String, dispositivoId: String, alvo: String, duracaoMinutos: Int = 15): String {
        val ref = dispositivoRef(familiaId, dispositivoId).collection("pedidosLiberacao").document()
        ref.set(
            mapOf(
                "alvo" to alvo,
                "status" to StatusPedidoLiberacao.PENDENTE.name,
                "criadoEm" to System.currentTimeMillis(),
                "duracaoMinutos" to duracaoMinutos
            )
        ).await()
        return ref.id
    }

    fun observarPedidosLiberacao(
        familiaId: String,
        dispositivoId: String,
        aoAtualizar: (List<PedidoLiberacao>) -> Unit
    ): ListenerRegistration {
        return dispositivoRef(familiaId, dispositivoId).collection("pedidosLiberacao")
            .whereEqualTo("status", StatusPedidoLiberacao.PENDENTE.name)
            .addSnapshotListener { snap, _ ->
                val lista = snap?.documents?.map { d ->
                    PedidoLiberacao(
                        id = d.id,
                        alvo = d.getString("alvo") ?: "",
                        status = StatusPedidoLiberacao.valueOf(d.getString("status") ?: "PENDENTE"),
                        criadoEm = d.getLong("criadoEm") ?: 0L,
                        duracaoMinutos = (d.getLong("duracaoMinutos") ?: 15L).toInt()
                    )
                } ?: emptyList()
                aoAtualizar(lista)
            }
    }

    suspend fun responderPedidoLiberacao(familiaId: String, dispositivoId: String, pedidoId: String, aprovado: Boolean) {
        dispositivoRef(familiaId, dispositivoId).collection("pedidosLiberacao").document(pedidoId)
            .update("status", if (aprovado) StatusPedidoLiberacao.APROVADO.name else StatusPedidoLiberacao.RECUSADO.name)
            .await()
    }

    // ---------- Relatórios ----------

    suspend fun registarTempoEcra(familiaId: String, dispositivoId: String, data: String, pacote: String, incrementoMs: Long) {
        val ref = dispositivoRef(familiaId, dispositivoId).collection("relatorios").document(data)
        db.runTransaction { transacao ->
            val atual = transacao.get(ref)
            @Suppress("UNCHECKED_CAST")
            val tempos = (atual.get("tempoEcraPorApp") as? Map<String, Long>)?.toMutableMap() ?: mutableMapOf()
            tempos[pacote] = (tempos[pacote] ?: 0L) + incrementoMs
            transacao.set(ref, mapOf("tempoEcraPorApp" to tempos), com.google.firebase.firestore.SetOptions.merge())
        }.await()
    }

    suspend fun registarBloqueioTentado(familiaId: String, dispositivoId: String, data: String, alvo: String) {
        val ref = dispositivoRef(familiaId, dispositivoId).collection("relatorios").document(data)
        ref.update("bloqueiosTentados", com.google.firebase.firestore.FieldValue.arrayUnion(alvo))
            .addOnFailureListener {
                // Documento ainda não existe nesse dia; cria-o.
                ref.set(mapOf("bloqueiosTentados" to listOf(alvo)), com.google.firebase.firestore.SetOptions.merge())
            }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toAppInstalada(): AppInstalada {
        return AppInstalada(
            nomePacote = getString("pacote") ?: id,
            nome = getString("nome") ?: "",
            iconeBase64 = getString("icone"),
            instaladoEm = getLong("instaladoEm") ?: 0L,
            regra = RegraApp.valueOf(getString("regra") ?: RegraApp.PERMITIDO.name),
            perfilHorarioId = getString("perfilHorarioId")
        )
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toPerfilHorario(): PerfilHorario {
        @Suppress("UNCHECKED_CAST")
        val janelasRaw = get("janelasPorDia") as? Map<String, List<Map<String, String>>> ?: emptyMap()
        val janelas = janelasRaw.entries.associate { (dia, lista) ->
            DiaSemana.valueOf(dia) to lista.map { JanelaHorario(it["inicio"] ?: "00:00", it["fim"] ?: "23:59") }
        }
        return PerfilHorario(id = id, nome = getString("nome") ?: "", janelasPorDia = janelas)
    }
}
