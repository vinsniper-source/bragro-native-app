package com.bragro.mobile.ui.basededados

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.bragro.mobile.ui.theme.LinhaTextField as OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.bragro.mobile.ui.i18n.Text
import com.bragro.mobile.ui.theme.appFieldColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URL
import java.net.URLEncoder

/** v139 -- 📍 minha localização e 🔍 buscar por município (mockup v139 item 8),
 * para o campo "latitude, longitude" da fazenda. Mesma busca do site
 * (Open-Meteo Geocoding, gratuita e sem chave). */
@Composable
fun LocalizacaoBotoes(onLocal: (String) -> Unit, onAviso: (String) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var buscando by remember { mutableStateOf(false) }

    fun pegar() {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val loc = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).firstNotNullOfOrNull { p ->
            runCatching { lm?.getLastKnownLocation(p) }.getOrNull()
        }
        if (loc == null) onAviso("Localização indisponível. Ligue o GPS e tente de novo.")
        else onLocal("%.4f, %.4f".format(java.util.Locale.US, loc.latitude, loc.longitude))
    }
    val permissao = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) pegar() else onAviso("Libere a localização para o BRAgro nas configurações do celular.")
    }

    Row {
        IconButton(onClick = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) pegar()
            else permissao.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.MyLocation, contentDescription = "Minha localização", modifier = Modifier.size(18.dp)) }
        IconButton(onClick = { buscando = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.Search, contentDescription = "Buscar por município", modifier = Modifier.size(18.dp)) }
    }

    if (buscando) {
        var termo by remember { mutableStateOf("") }
        var resultados by remember { mutableStateOf<List<Triple<String, Double, Double>>?>(null) }
        AlertDialog(
            onDismissRequest = { buscando = false },
            title = { Text("Buscar município") },
            text = {
                Column {
                    OutlinedTextField(value = termo, onValueChange = { termo = it }, label = { Text("Município") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = appFieldColors())
                    resultados?.let { lista ->
                        if (lista.isEmpty()) Text("Nenhum município encontrado.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                        lista.forEach { (rotulo, lat, lon) ->
                            Text(
                                "$rotulo  ($lat, $lon)",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth().clickable { onLocal("$lat, $lon"); buscando = false }.padding(vertical = 8.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        resultados = withContext(Dispatchers.IO) {
                            runCatching {
                                val url = "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(termo.trim(), "UTF-8")}&count=5&language=pt&countryCode=BR&format=json"
                                val txt = URL(url).readText()
                                Json.parseToJsonElement(txt).jsonObject["results"]?.jsonArray?.map {
                                    val o = it.jsonObject
                                    val nome = o["name"]?.jsonPrimitive?.contentOrNull ?: ""
                                    val uf = o["admin1"]?.jsonPrimitive?.contentOrNull
                                    Triple(if (uf != null) "$nome · $uf" else nome, Math.round((o["latitude"]?.jsonPrimitive?.doubleOrNull ?: 0.0) * 10000) / 10000.0, Math.round((o["longitude"]?.jsonPrimitive?.doubleOrNull ?: 0.0) * 10000) / 10000.0)
                                } ?: emptyList()
                            }.getOrDefault(emptyList())
                        }
                    }
                }, enabled = termo.trim().length >= 3) { Text("Buscar") }
            },
            dismissButton = { TextButton(onClick = { buscando = false }) { Text("Fechar") } },
        )
    }
}
