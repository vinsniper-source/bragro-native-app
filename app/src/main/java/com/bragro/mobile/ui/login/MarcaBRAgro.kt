package com.bragro.mobile.ui.login

import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.bragro.mobile.R

/**
 * v317 (mockup v317 aprovado, mesmo do site) -- logo nova sem fundo branco.
 * No modo escuro o "BRAgro" sai num verde mais claro (o verde escuro sumia
 * no fundo escuro). Argentina continua com a logo própria.
 */
@Composable
fun MarcaBRAgro(modifier: Modifier = Modifier) {
    val argentina = com.bragro.mobile.ui.i18n.Idioma.argentina
    val escuro = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Image(
        painter = painterResource(
            when {
                argentina -> R.drawable.logo_argro
                escuro -> R.drawable.logo_bragro_escuro
                else -> R.drawable.logo_bragro
            }
        ),
        contentDescription = if (argentina) "ARGro" else "BRAgro",
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}
