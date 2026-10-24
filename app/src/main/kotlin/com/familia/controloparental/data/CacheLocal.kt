package com.familia.controloparental.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.familia.controloparental.comum.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "controlo_parental_cache")

/**
 * Cache local das regras, usado pelo dispositivo da criança para continuar a
 * aplicar bloqueios mesmo sem ligação à internet (secção 8 do documento de requisitos).
 * As regras são escritas aqui sempre que chega uma atualização do Firestore,
 * e lidas no arranque do serviço de VPN / acessibilidade.
 */
class CacheLocal(private val context: Context) {

    private object Chaves {
        val APPS = stringPreferencesKey("apps_json")
        val REGRAS_URL = stringPreferencesKey("regras_url_json")
        val PERFIS_HORARIO = stringPreferencesKey("perfis_horario_json")
        val FAMILIA_ID = stringPreferencesKey("familia_id")
        val DISPOSITIVO_ID = stringPreferencesKey("dispositivo_id")
        val SENHA_ADMIN_HASH = stringPreferencesKey("senha_admin_hash")
    }

    suspend fun guardarIdentidade(familiaId: String, dispositivoId: String) {
        context.dataStore.edit {
            it[Chaves.FAMILIA_ID] = familiaId
            it[Chaves.DISPOSITIVO_ID] = dispositivoId
        }
    }

    suspend fun lerFamiliaId(): String? = context.dataStore.data.first()[Chaves.FAMILIA_ID]
    suspend fun lerDispositivoId(): String? = context.dataStore.data.first()[Chaves.DISPOSITIVO_ID]

    suspend fun guardarSenhaAdminHash(hash: String) {
        context.dataStore.edit { it[Chaves.SENHA_ADMIN_HASH] = hash }
    }

    suspend fun lerSenhaAdminHash(): String? = context.dataStore.data.first()[Chaves.SENHA_ADMIN_HASH]

    suspend fun guardarApps(apps: List<AppInstalada>) {
        val array = JSONArray()
        apps.forEach { app ->
            array.put(
                JSONObject().apply {
                    put("nomePacote", app.nomePacote)
                    put("nome", app.nome)
                    put("regra", app.regra.name)
                    put("perfilHorarioId", app.perfilHorarioId)
                }
            )
        }
        context.dataStore.edit { it[Chaves.APPS] = array.toString() }
    }

    fun observarApps(): Flow<List<AppInstalada>> =
        context.dataStore.data.map { prefs ->
            val json = prefs[Chaves.APPS] ?: return@map emptyList()
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                AppInstalada(
                    nomePacote = o.getString("nomePacote"),
                    nome = o.getString("nome"),
                    regra = RegraApp.valueOf(o.getString("regra")),
                    perfilHorarioId = o.optString("perfilHorarioId", null)
                )
            }
        }

    suspend fun guardarRegrasUrl(regras: List<RegraUrl>) {
        val array = JSONArray()
        regras.forEach { r ->
            array.put(
                JSONObject().apply {
                    put("id", r.id)
                    put("dominio", r.dominio)
                    put("ativo", r.ativo)
                    put("perfilHorarioId", r.perfilHorarioId)
                }
            )
        }
        context.dataStore.edit { it[Chaves.REGRAS_URL] = array.toString() }
    }

    fun observarRegrasUrl(): Flow<List<RegraUrl>> =
        context.dataStore.data.map { prefs ->
            val json = prefs[Chaves.REGRAS_URL] ?: return@map emptyList()
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                RegraUrl(
                    id = o.getString("id"),
                    dominio = o.getString("dominio"),
                    ativo = o.getBoolean("ativo"),
                    perfilHorarioId = o.optString("perfilHorarioId", null)
                )
            }
        }

    suspend fun guardarPerfisHorario(perfis: List<PerfilHorario>) {
        val array = JSONArray()
        perfis.forEach { p ->
            val janelas = JSONObject()
            p.janelasPorDia.forEach { (dia, lista) ->
                val arr = JSONArray()
                lista.forEach { j ->
                    arr.put(JSONObject().apply { put("inicio", j.inicio); put("fim", j.fim) })
                }
                janelas.put(dia.name, arr)
            }
            array.put(JSONObject().apply {
                put("id", p.id)
                put("nome", p.nome)
                put("janelas", janelas)
            })
        }
        context.dataStore.edit { it[Chaves.PERFIS_HORARIO] = array.toString() }
    }

    suspend fun lerPerfisHorario(): List<PerfilHorario> {
        val json = context.dataStore.data.first()[Chaves.PERFIS_HORARIO] ?: return emptyList()
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val janelasObj = o.getJSONObject("janelas")
            val janelasPorDia = mutableMapOf<DiaSemana, List<JanelaHorario>>()
            janelasObj.keys().forEach { diaStr ->
                val arr = janelasObj.getJSONArray(diaStr)
                val lista = (0 until arr.length()).map { j ->
                    val jo = arr.getJSONObject(j)
                    JanelaHorario(jo.getString("inicio"), jo.getString("fim"))
                }
                janelasPorDia[DiaSemana.valueOf(diaStr)] = lista
            }
            PerfilHorario(id = o.getString("id"), nome = o.getString("nome"), janelasPorDia = janelasPorDia)
        }
    }
}
