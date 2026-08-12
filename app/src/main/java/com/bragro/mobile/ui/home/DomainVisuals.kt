package com.bragro.mobile.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.bragro.mobile.ui.theme.BrBlue
import com.bragro.mobile.ui.theme.BrGreen
import com.bragro.mobile.ui.theme.BrOrange
import com.bragro.mobile.ui.theme.BrYellow

// Mapeamento de icone + secao por modulo, so pro app nativo -- espelha o
// agrupamento visual do site (ver sistema-agro-nextjs/src/lib/modules.ts,
// campos icon/section de MODULES, e SECTION_LABELS em sidebar-nav.tsx) sem
// precisar mudar o backend: a rota /api/mobile/config manda os DOMAINS
// (estrutura de formulario, ver registry.ts), que nao tem icone/secao --
// so serve pra montar telas de cadastro, nao pra navegacao. Mudar essa rota
// pra mandar os MODULES em vez disso mexeria numa API ja usada e testada
// (alto risco pra um ajuste so visual); manter esse mapeamento aqui, chaveado
// por domainId, e mais seguro e mais simples. Se o site ganhar um modulo novo
// sem entrada aqui, ele cai no fallback (icone generico + secao "Outros") em
// vez de quebrar o app.
data class DomainSectionMeta(val id: String, val label: String, val color: Color, val order: Int)

private val SECTIONS = listOf(
    DomainSectionMeta("financeiro", "Financeiro", BrBlue, 0),
    DomainSectionMeta("campo", "Campo", BrGreen, 1),
    DomainSectionMeta("estoque", "Estoque e Frota", BrYellow, 2),
    DomainSectionMeta("pessoas", "Pessoas", BrOrange, 3),
)

private val FALLBACK_SECTION = DomainSectionMeta("outros", "Outros", Color(0xFF6B7280), 9)

// domainId -> id da secao (mesmo agrupamento de sidebar-nav.tsx).
private val DOMAIN_SECTION_ID: Map<String, String> = mapOf(
    "financeiro" to "financeiro",
    "pedidos" to "financeiro",
    "cotacoesfornecedores" to "financeiro",
    "contratos" to "financeiro",
    "caixainterno" to "financeiro",
    "cobrancas" to "financeiro",
    "nfse" to "financeiro",
    "safra" to "campo",
    "planejamentosafra" to "campo",
    "colheita" to "campo",
    "romaneios" to "campo",
    "receituarios" to "campo",
    "pragas" to "campo",
    "clima" to "campo",
    "estoque" to "estoque",
    "inventario" to "estoque",
    "frota" to "estoque",
    "controleinterno" to "estoque",
    "rh" to "pessoas",
)

// domainId -> icone (Material Icons Extended, ja e dependencia do projeto).
// Escolhidos pra corresponder ao espirito dos icones lucide-react usados no
// site (nomes diferentes, mesma ideia visual) -- nao ha lucide no Compose.
private val DOMAIN_ICON: Map<String, ImageVector> = mapOf(
    // Trocado de AccountBalanceWallet -- pedido do usuário ("mude o ícone
    // do módulo lançamentos"); esse ícone era igual ao da própria aba
    // "Financeiro" e ao de DRE/Análises no dropdown, confundia.
    "financeiro" to Icons.Filled.Receipt,
    "pedidos" to Icons.Filled.ShoppingCart,
    // Balanca (comparacao de propostas) -- mesmo espirito do icone "Scale"
    // do site (lucide-react) pro modulo novo de Cotacoes de Fornecedores.
    "cotacoesfornecedores" to Icons.Filled.Balance,
    "contratos" to Icons.Filled.Description,
    "caixainterno" to Icons.Filled.Payments,
    "cobrancas" to Icons.Filled.CreditCard,
    "nfse" to Icons.Filled.ReceiptLong,
    "safra" to Icons.Filled.Eco,
    "planejamentosafra" to Icons.Filled.Assignment,
    "colheita" to Icons.Filled.Agriculture,
    "romaneios" to Icons.Filled.LocalShipping,
    "receituarios" to Icons.Filled.Science,
    "pragas" to Icons.Filled.BugReport,
    "clima" to Icons.Filled.WbSunny,
    "estoque" to Icons.Filled.Inventory2,
    // Trocado de Inventory (caixa única) -- pedido do usuário ("trocar
    // ícone inventário"); parecia demais com o ícone de "Estoque"
    // (Inventory2, caixas empilhadas), confundia. Warehouse (galpão) é
    // bem mais distinto visualmente.
    "inventario" to Icons.Filled.Warehouse,
    "frota" to Icons.Filled.DirectionsCar,
    "controleinterno" to Icons.Filled.Security,
    "rh" to Icons.Filled.People,
)

fun domainIcon(domainId: String): ImageVector = DOMAIN_ICON[domainId] ?: Icons.Filled.Apps

fun domainSectionMeta(domainId: String): DomainSectionMeta {
    val sectionId = DOMAIN_SECTION_ID[domainId] ?: return FALLBACK_SECTION
    return SECTIONS.firstOrNull { it.id == sectionId } ?: FALLBACK_SECTION
}
