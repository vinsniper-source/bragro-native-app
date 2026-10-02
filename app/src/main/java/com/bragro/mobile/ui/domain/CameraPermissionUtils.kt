package com.bragro.mobile.ui.domain

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

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
