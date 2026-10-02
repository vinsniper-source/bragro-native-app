package com.bragro.mobile.ui.domain

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/** "Continuo sem acesso a câmera e leitor QR" (reclamação recorrente do
 * usuário, mesmo depois da v1.3.21 ter corrigido as mensagens de erro
 * silenciosas): a causa raiz não era falta de mensagem -- é que, uma vez que
 * o usuário nega a permissão CAMERA (ou já negou no passado, inclusive antes
 * da permissão existir no manifest), o Android passa a NEGAR
 * automaticamente qualquer pedido futuro, sem nunca mais mostrar o diálogo
 * do sistema (`shouldShowRequestPermissionRationale` vira false). O app
 * ficava preso num loop de "tente de novo" que nunca funcionava, porque o
 * ÚNICO jeito de reverter isso é o usuário conceder manualmente em
 * Configurações > Apps > BRAgro > Permissões -- e nenhuma tela do app
 * oferecia esse caminho. Esta função abre essa tela de configurações
 * diretamente, usada pelos 4 pontos de câmera/QR (Orçamento, Abastecimento
 * Frota, Praga Foto, Romaneio Rápido) quando a negação é permanente. */
fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private const val PREFS_NAME = "camera_permission_prefs"
private const val KEY_JA_PEDIU = "ja_pediu_permissao_camera"

/** "Continuo sem acesso... há como forçar a abertura" (3ª reclamação -- o
 * fix anterior só detectava a negação permanente DEPOIS de uma 1ª tentativa
 * falha e silenciosa, deixando o usuário achando que o botão simplesmente
 * não faz nada). O Android não tem API direta pra distinguir "nunca pedi
 * essa permissão ainda" de "já neguei permanentemente" -- o único sinal
 * (`shouldShowRequestPermissionRationale == false`) é o MESMO nos dois
 * casos. Por isso gravamos aqui, em SharedPreferences, se este app já
 * tentou pedir CAMERA alguma vez; combinado com esse sinal, já na ABERTURA
 * da tela conseguimos saber se é negação permanente e mostrar direto o
 * botão "Abrir Configurações", sem exigir um 1º toque morto no botão da
 * câmera pra só então revelar o caminho certo. */
fun marcarCameraPermissaoJaPedida(context: Context) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_JA_PEDIU, true).apply()
}

/** true quando a permissão CAMERA está negada e, ou o sistema não vai mais
 * mostrar o diálogo (já pedimos antes), ou já constava como permanentemente
 * negada de uma instalação/pedido anterior. Chamar no início de cada tela
 * com câmera (LaunchedEffect) pra já nascer com o estado certo. */
fun isCameraPermanentementeNegada(context: Context): Boolean {
    val activity = context as? Activity ?: return false
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) return false
    val jaPediuAntes = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_JA_PEDIU, false)
    if (!jaPediuAntes) return false // 1ª vez -- deixa o fluxo normal pedir o diálogo
    return !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
}

/** Guarda final antes de chamar `takePicture.launch(uri)`: alguns aparelhos
 * (emulador sem câmera configurada, ROMs customizadas sem app de câmera
 * padrão) simplesmente não têm NENHUM app capaz de responder ao Intent de
 * captura -- aí nem é bug de permissão, é hardware/ROM ausente, e o launch()
 * falhava silenciosamente (o sintoma exato relatado: "não abre nada").
 * Confere ANTES de tentar, com uma mensagem clara em vez de nada acontecer. */
fun temAppDeCameraDisponivel(context: Context): Boolean {
    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
    return intent.resolveActivity(context.packageManager) != null
}

fun avisarSemAppDeCamera(context: Context) {
    Toast.makeText(context, "Nenhum app de câmera encontrado neste aparelho -- verifique se há um app de câmera instalado e habilitado.", Toast.LENGTH_LONG).show()
}

// Nome da ação padrão do Intent de scan QR/barcode, definida originalmente
// pelo ZXing ("Barcode Scanner") e adotada como PADRÃO DE FATO por
// praticamente todo app de leitor de QR Code do Google Play, inclusive os
// pré-instalados por fabricante (Xiaomi/Samsung respondem a este mesmo
// Intent por compatibilidade) -- usada só como string de ação, nenhuma
// dependência do ZXing é adicionada ao projeto.
private const val ACAO_SCAN_QR = "com.google.zxing.client.android.SCAN"
private const val EXTRA_RESULTADO_SCAN_QR = "SCAN_RESULT"

/** "Colocar a condição de acessar o QR nativo do celular, pq existem
 * celulares mais antigos que o QR code é separado da câmera" (pedido do
 * usuário): em vez de SEMPRE depender do fluxo "tira foto -> decodifica com
 * ML Kit" (que exige boa resolução/foco de perto da câmera do app, nem
 * sempre bom em aparelhos antigos), esta função detecta se o aparelho tem
 * um leitor de QR Code NATIVO/dedicado instalado (app de câmera com modo QR
 * próprio, ou um app de leitor dedicado) capaz de responder ao Intent de
 * scan -- se tiver, o app usa esse leitor diretamente (câmera ao vivo,
 * já otimizada pelo fabricante pra foco de perto) em vez do fluxo de foto. */
fun temLeitorQrNativoDisponivel(context: Context): Boolean {
    val intent = Intent(ACAO_SCAN_QR)
    return intent.resolveActivity(context.packageManager) != null
}

/** Intent padrão de scan (modo QR_CODE_MODE) pro leitor nativo detectado por
 * [temLeitorQrNativoDisponivel]. */
fun criarIntentLeitorQrNativo(): Intent =
    Intent(ACAO_SCAN_QR).putExtra("SCAN_MODE", "QR_CODE_MODE")

/** Extrai o texto lido do resultado devolvido pelo leitor de QR nativo --
 * chave padrão "SCAN_RESULT", a mesma usada pelo ZXing e por todo app
 * compatível com esse Intent (ver [criarIntentLeitorQrNativo]). */
fun extrairResultadoLeitorQrNativo(data: Intent?): String? = data?.getStringExtra(EXTRA_RESULTADO_SCAN_QR)
