package com.bragro.mobile.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// Armazenamento local (Room/SQLite) -- e o que faz o app funcionar OFFLINE
// de verdade: tudo que a tela mostra vem DAQUI primeiro (nunca direto da
// rede), e as escritas offline ficam na fila (PendingSyncEntity) ate a
// conexao voltar. Ver data/repo/RecordRepository.kt.

/** Sessao do usuario logado -- so existe UMA linha aqui (id fixo "current"). */
@Entity(tableName = "session")
data class SessionEntity(
    @PrimaryKey val id: String = "current",
    val userId: String,
    val email: String,
    val orgId: String,
    val orgName: String,
    val orgLogoUrl: String?,
    val avatarUrl: String?,
    val role: String,
    val allowedModulesCsv: String, // lista separada por virgula -- Room nao grava List<String> sem TypeConverter, e nao vale a pena so por isso
    val planTier: String,
    val accessToken: String,
    val refreshToken: String,
    val atualizadoEm: Long,
)

/** Uma opcao de lista suspensa (Local, Categoria, Banco etc.), cacheada por categoria. */
@Entity(tableName = "lookup_options", primaryKeys = ["category", "value"])
data class LookupEntity(
    val category: String,
    val value: String,
    val label: String,
    val order: Int,
)

/** Fazenda cadastrada na organizacao. */
@Entity(tableName = "farms", primaryKeys = ["name"])
data class FarmEntity(
    val name: String,
    val areaHa: Double,
)

/** Configuracao (campos/tipos) de UM dos 16 modulos -- cache local do que
 * /api/mobile/config devolve, serializado como JSON (ver DomainConfig.kt). */
@Entity(tableName = "domain_configs")
data class DomainConfigEntity(
    @PrimaryKey val domainId: String,
    val label: String,
    val configJson: String,
)

/** KPIs do Início (Fase 2, Task #31) -- so existe UMA linha aqui (id fixo
 * "current"), sobrescrita a cada chamada bem-sucedida de /api/mobile/dashboard.
 * Permite abrir a tela de Dashboard offline mostrando o ultimo retrato
 * conhecido (com "atualizadoEmMillis" exibido na tela pra deixar claro que
 * pode estar desatualizado), mesmo padrao ja usado pela sessao/lookups/farms. */
@Entity(tableName = "dashboard")
data class DashboardEntity(
    @PrimaryKey val id: String = "current",
    val orgName: String,
    val saldoFinanceiroAberto: Double,
    val itensEstoque: Int,
    val safrasAtivas: Int,
    val colaboradoresAtivos: Int,
    val culturaLider: String?,
    val pedidosAtrasados: Int,
    val alertsCount: Int,
    val atualizadoEmMillis: Long,
)

/** DRE consolidado (Fase 2, Task #32) -- so existe UMA linha aqui (id fixo
 * "current"), sobrescrita a cada consulta bem-sucedida de /api/mobile/dre.
 * "dataJson" guarda o DreData inteiro serializado (varias fazendas, cada
 * uma com varios campos) -- um blob JSON aqui evita criar uma tabela SQL
 * por fazenda so pra um cache de leitura; "safra"/"cultura" guardam o
 * filtro usado na ultima consulta, so pra mostrar na tela quando estiver
 * exibindo um resultado cacheado offline. */
@Entity(tableName = "dre")
data class DreEntity(
    @PrimaryKey val id: String = "current",
    val safra: String?,
    val cultura: String?,
    val dataJson: String,
    val atualizadoEmMillis: Long,
)

/** Analises cruzadas entre modulos (Fase 2, Task #36) -- so existe UMA
 * linha aqui (id fixo "current"), sobrescrita a cada consulta bem-sucedida
 * de /api/mobile/analises. "analisesJson" guarda o objeto inteiro (15
 * analises, cada uma com seu proprio formato de linha) sem schema fixo --
 * AnalisesScreen.kt re-parseia como JsonObject e renderiza cada chave de
 * forma generica. */
@Entity(tableName = "analises")
data class AnalisesEntity(
    @PrimaryKey val id: String = "current",
    val safra: String?,
    val analisesJson: String,
    val safrasDisponiveisCsv: String,
    val atualizadoEmMillis: Long,
)

/** Um registro (lancamento) de qualquer um dos 16 modulos, cacheado
 * localmente. "fieldsJson" guarda todos os campos como JSON generico --
 * cada modulo tem colunas diferentes (ver registry.ts/DomainConfig), entao
 * uma tabela SQL fixa por modulo duplicaria demais; um blob JSON aqui e o
 * mesmo tipo de decisao que o proprio backend faz na Fase 1 do offline
 * (mobile-app/offline-shell tambem guarda os campos como JSON, ver
 * src/lib/offline/read-cache.ts no repositorio do site). */
@Entity(tableName = "records", primaryKeys = ["domainId", "id"])
data class RecordEntity(
    val domainId: String,
    val id: String,
    val fieldsJson: String,
    val criadoEmMillis: Long,
    /** true = ainda nao existe no servidor (criado offline, aguardando sync);
     * o id local, nesse caso, e um UUID temporario gerado no aparelho. */
    val pendingCreate: Boolean = false,
)

/** Fila de escrita offline (outbox) -- toda vez que o usuario salva um
 * lancamento sem internet, entra uma linha aqui. O SyncWorker (ver
 * sync/SyncWorker.kt) processa em ordem assim que a conexao volta, chamando
 * /api/offline-sync (a MESMA rota que ja reaproveita 100% da validacao e dos
 * calculos de negocio do servidor -- nada disso e duplicado aqui). */
@Entity(tableName = "pending_sync")
data class PendingSyncEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val domainId: String,
    val kind: String, // "create" | "update"
    val localRecordId: String, // id local (RecordEntity.id) -- some/vira o id real apos sync bem-sucedido
    val serverRecordId: String?, // preenchido quando kind == "update"
    val fieldsJson: String,
    val criadoEmMillis: Long,
    val ultimoErro: String? = null,
)
