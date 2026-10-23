package com.bragro.mobile.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Agriculture
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Engineering
import androidx.compose.material.icons.outlined.Grass
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Warehouse
import androidx.compose.material.icons.outlined.WbSunny
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
    // Pecuária (Task #685-692, gap analysis) -- mesma seção "campo" do site
    // (lib/modules.ts: section: "campo").
    "pecuaria" to "campo",
    // Pastagem (Módulo de Suplementação e Pastagem, gap analysis do usuário)
    // -- mesma seção "campo" do site (lib/modules.ts).
    "pastagem" to "campo",
    "estoque" to "estoque",
    "inventario" to "estoque",
    "frota" to "estoque",
    "controleinterno" to "estoque",
    "rh" to "pessoas",
    // Mão de Obra e Familiar (Task #752, gap analysis Agricultura Familiar)
    // -- mesma seção "pessoas" do site (lib/modules.ts).
    "maodeobra" to "pessoas",
)

// domainId -> icone (Material Icons Extended, ja e dependencia do projeto).
// Escolhidos pra corresponder ao espirito dos icones lucide-react usados no
// site (nomes diferentes, mesma ideia visual) -- nao ha lucide no Compose.
private val DOMAIN_ICON: Map<String, ImageVector> = mapOf(
    // Trocado de AccountBalanceWallet -- pedido do usuário ("mude o ícone
    // do módulo lançamentos"); esse ícone era igual ao da própria aba
    // "Financeiro" e ao de DRE/Análises no dropdown, confundia.
    "financeiro" to Icons.Outlined.Receipt,
    // Entrada própria "Gestão Financeira" na lista suspensa do Financeiro
    // (ver BottomNavBar.kt) -- ícone diferente do "Lançamentos" (Receipt)
    // pra não confundir os dois na lista.
    "gestaofinanceira" to Icons.Outlined.AccountBalanceWallet,
    "pedidos" to Icons.Outlined.ShoppingCart,
    // Balanca (comparacao de propostas) -- mesmo espirito do icone "Scale"
    // do site (lucide-react) pro modulo novo de Cotacoes de Fornecedores.
    "cotacoesfornecedores" to com.bragro.mobile.ui.domain.IconesVazados.Balance,
    "contratos" to Icons.Outlined.Description,
    "caixainterno" to Icons.Outlined.Payments,
    "cobrancas" to Icons.Outlined.CreditCard,
    "nfse" to Icons.Outlined.ReceiptLong,
    "safra" to Icons.Outlined.Eco,
    "planejamentosafra" to Icons.Outlined.Assignment,
    "colheita" to com.bragro.mobile.ui.domain.IconesVazados.Agriculture,
    "romaneios" to Icons.Outlined.LocalShipping,
    "receituarios" to Icons.Outlined.Science,
    "pragas" to com.bragro.mobile.ui.domain.IconesVazados.BugReport,
    "clima" to Icons.Outlined.WbSunny,
    // Pets (pata) -- Material Icons não tem um ícone de boi/gado no set
    // base; mesmo critério de "espírito visual" usado nos demais ícones
    // deste mapa (ver comentário no topo do arquivo).
    "pecuaria" to com.bragro.mobile.ui.domain.IconePecuaria,
    "pastagem" to com.bragro.mobile.ui.domain.IconePastagem,
    "estoque" to Icons.Outlined.Inventory2,
    // Trocado de Inventory (caixa única) -- pedido do usuário ("trocar
    // ícone inventário"); parecia demais com o ícone de "Estoque"
    // (Inventory2, caixas empilhadas), confundia. Warehouse (galpão) é
    // bem mais distinto visualmente.
    "inventario" to Icons.Outlined.Warehouse,
    "frota" to Icons.Outlined.DirectionsCar,
    "controleinterno" to Icons.Outlined.Security,
    "rh" to Icons.Outlined.People,
    "maodeobra" to Icons.Outlined.Engineering,
)

fun domainIcon(domainId: String): ImageVector = DOMAIN_ICON[domainId] ?: com.bragro.mobile.ui.domain.IconesVazados.Apps

fun domainSectionMeta(domainId: String): DomainSectionMeta {
    val sectionId = DOMAIN_SECTION_ID[domainId] ?: return FALLBACK_SECTION
    return SECTIONS.firstOrNull { it.id == sectionId } ?: FALLBACK_SECTION
}
