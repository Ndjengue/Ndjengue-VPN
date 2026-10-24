package com.familia.controloparental.pai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.familia.controloparental.comum.AppInstalada
import com.familia.controloparental.comum.Dispositivo
import com.familia.controloparental.comum.RegraApp
import com.familia.controloparental.comum.RegraUrl
import com.familia.controloparental.data.FirestoreRepositorio
import kotlinx.coroutines.launch

/**
 * Painel principal do pai: dispositivos (secção 11), apps instaladas com
 * regra configurável (secção 5), e regras de URL com toggle (secção 4).
 * Gera também o código de emparelhamento (secção 3).
 */
@Composable
fun TelaPainelPai(familiaId: String) {
    val repositorio = remember { FirestoreRepositorio() }
    var dispositivos by remember { mutableStateOf<List<Dispositivo>>(emptyList()) }
    var dispositivoSelecionado by remember { mutableStateOf<String?>(null) }
    var codigoEmparelhamento by remember { mutableStateOf<String?>(null) }
    val escopo = rememberCoroutineScope()

    DisposableEffect(familiaId) {
        val registo = repositorio.observarDispositivos(familiaId) { dispositivos = it }
        onDispose { registo.remove() }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Os teus dispositivos", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = {
                escopo.launch {
                    codigoEmparelhamento = repositorio.gerarCodigoEmparelhamento(familiaId)
                }
            }) { Text("Emparelhar novo") }
        }

        codigoEmparelhamento?.let {
            Spacer(Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Código de emparelhamento (válido 5 min):")
                    Text(it, style = MaterialTheme.typography.headlineMedium)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (dispositivos.isEmpty()) {
            Text("Nenhum dispositivo emparelhado ainda.")
        } else {
            LazyColumn {
                items(dispositivos) { dispositivo ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(dispositivo.nome, style = MaterialTheme.typography.titleMedium)
                                    Text(if (dispositivo.online) "Online" else "Offline")
                                }
                                TextButton(onClick = { dispositivoSelecionado = dispositivo.id }) {
                                    Text("Gerir")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    dispositivoSelecionado?.let { dispositivoId ->
        TelaGerirDispositivo(
            familiaId = familiaId,
            dispositivoId = dispositivoId,
            aoFechar = { dispositivoSelecionado = null }
        )
    }
}

@Composable
private fun TelaGerirDispositivo(familiaId: String, dispositivoId: String, aoFechar: () -> Unit) {
    val repositorio = remember { FirestoreRepositorio() }
    var apps by remember { mutableStateOf<List<AppInstalada>>(emptyList()) }
    var regrasUrl by remember { mutableStateOf<List<RegraUrl>>(emptyList()) }
    var novoDominio by remember { mutableStateOf("") }
    val escopo = rememberCoroutineScope()

    DisposableEffect(dispositivoId) {
        val r1 = repositorio.observarApps(familiaId, dispositivoId) { apps = it }
        val r2 = repositorio.observarRegrasUrl(familiaId, dispositivoId) { regrasUrl = it }
        onDispose { r1.remove(); r2.remove() }
    }

    AlertDialog(
        onDismissRequest = aoFechar,
        confirmButton = { TextButton(onClick = aoFechar) { Text("Fechar") } },
        title = { Text("Gerir dispositivo") },
        text = {
            Column(modifier = Modifier.heightIn(max = 500.dp)) {
                Text("URLs/domínios bloqueados", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = novoDominio,
                        onValueChange = { novoDominio = it },
                        label = { Text("ex.: instagram.com") },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        escopo.launch {
                            if (novoDominio.isNotBlank()) {
                                repositorio.adicionarRegraUrl(familiaId, dispositivoId, novoDominio.trim())
                                novoDominio = ""
                            }
                        }
                    }) { Text("Adicionar") }
                }
                LazyColumn(modifier = Modifier.heightIn(max = 150.dp)) {
                    items(regrasUrl) { regra ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text(regra.dominio)
                            Switch(
                                checked = regra.ativo,
                                onCheckedChange = { ativo ->
                                    escopo.launch {
                                        repositorio.alternarRegraUrl(familiaId, dispositivoId, regra.id, ativo)
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text("Apps instaladas", style = MaterialTheme.typography.titleSmall)
                LazyColumn(modifier = Modifier.heightIn(max = 250.dp)) {
                    items(apps) { app ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text(app.nome, modifier = Modifier.weight(1f))
                            var expandido by remember { mutableStateOf(false) }
                            Box {
                                TextButton(onClick = { expandido = true }) {
                                    Text(app.regra.name)
                                }
                                DropdownMenu(expanded = expandido, onDismissRequest = { expandido = false }) {
                                    RegraApp.values().forEach { opcao ->
                                        DropdownMenuItem(
                                            text = { Text(opcao.name) },
                                            onClick = {
                                                expandido = false
                                                escopo.launch {
                                                    repositorio.definirRegraApp(
                                                        familiaId, dispositivoId, app.nomePacote, opcao, app.perfilHorarioId
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}
