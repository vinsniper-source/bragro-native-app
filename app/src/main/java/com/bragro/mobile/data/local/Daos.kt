package com.bragro.mobile.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM session WHERE id = 'current' LIMIT 1")
    fun observe(): Flow<SessionEntity?>

    @Query("SELECT * FROM session WHERE id = 'current' LIMIT 1")
    suspend fun get(): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: SessionEntity)

    @Query("DELETE FROM session")
    suspend fun clear()
}

@Dao
interface LookupDao {
    // Ordem alfabetica (pedido do usuario: "coloque todas as listas
    // suspensas em ordem alfabetica na plataforma e mobile") -- antes era
    // por "order" (ordem de insercao no servidor). Mesmo criterio do
    // bootstrap/route.ts no site (orderBy label asc), so que aqui e o DAO
    // local (Room) que realmente decide a ordem exibida no app, ja que os
    // dados sao cacheados localmente e relidos por categoria.
    @Query("SELECT * FROM lookup_options WHERE category = :category ORDER BY label ASC")
    suspend fun byCategory(category: String): List<LookupEntity>

    // Versao reativa de byCategory -- BUG real corrigido (pedido do usuario:
    // "as listas suspensas de todo app continuam desatualizadas, ainda ha
    // fazendas que ja exclui"): telas que buscavam a lista com
    // LaunchedEffect(Unit) + byCategory() (suspend, uma unica vez) SO liam
    // o valor que o Room tinha NAQUELE INSTANTE -- se o bootstrap em segundo
    // plano (ver ConfigRepository.bootstrapAndCacheConfig, chamado sozinho a
    // cada abertura do Inicio) atualizasse a tabela um pouco DEPOIS dessa
    // leitura (ou a tela ja estivesse aberta havia um tempo), a lista
    // continuava mostrando o valor antigo pelo resto da sessao, sem nenhum
    // jeito de se auto-corrigir a nao ser fechar e reabrir o app inteiro.
    // Flow (Room gera a query reativa sozinho) resolve isso na raiz: quem
    // observa via collectAsState() recompoe automaticamente assim que
    // clearAll()+insertAll() do bootstrap terminar, sem precisar de nenhuma
    // logica extra de "recarregar".
    @Query("SELECT * FROM lookup_options WHERE category = :category ORDER BY label ASC")
    fun observeByCategory(category: String): Flow<List<LookupEntity>>

    @Query("DELETE FROM lookup_options")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<LookupEntity>)
}

@Dao
interface FarmDao {
    @Query("SELECT * FROM farms ORDER BY name ASC")
    suspend fun all(): List<FarmEntity>

    // Versao reativa de all() -- mesmo motivo de LookupDao.observeByCategory
    // acima (bug de fazenda excluida continuando na lista suspensa/filtro).
    @Query("SELECT * FROM farms ORDER BY name ASC")
    fun observeAll(): Flow<List<FarmEntity>>

    @Query("DELETE FROM farms")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<FarmEntity>)
}

@Dao
interface DomainConfigDao {
    @Query("SELECT * FROM domain_configs ORDER BY label ASC")
    fun observeAll(): Flow<List<DomainConfigEntity>>

    @Query("SELECT * FROM domain_configs WHERE domainId = :domainId LIMIT 1")
    suspend fun byId(domainId: String): DomainConfigEntity?

    @Query("DELETE FROM domain_configs")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<DomainConfigEntity>)
}

@Dao
interface DashboardDao {
    @Query("SELECT * FROM dashboard WHERE id = 'current' LIMIT 1")
    fun observe(): Flow<DashboardEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(dashboard: DashboardEntity)
}

@Dao
interface HomeDao {
    @Query("SELECT * FROM home WHERE id = 'current' LIMIT 1")
    fun observe(): Flow<HomeEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(home: HomeEntity)
}

@Dao
interface DreDao {
    @Query("SELECT * FROM dre WHERE id = 'current' LIMIT 1")
    fun observe(): Flow<DreEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(dre: DreEntity)
}

@Dao
interface AnalisesDao {
    @Query("SELECT * FROM analises WHERE id = 'current' LIMIT 1")
    fun observe(): Flow<AnalisesEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(analises: AnalisesEntity)
}

@Dao
interface RecordDao {
    @Query("SELECT * FROM records WHERE domainId = :domainId ORDER BY criadoEmMillis DESC")
    fun observeByDomain(domainId: String): Flow<List<RecordEntity>>

    @Query("SELECT * FROM records WHERE domainId = :domainId AND id = :id LIMIT 1")
    suspend fun byId(domainId: String, id: String): RecordEntity?

    // "Copiar último lançamento" (mesma ideia de records[0] em data-table.tsx,
    // que ja vem ordenado por criadoEm DESC do servidor) -- usado pra
    // pré-preencher um novo formulário com os valores do lançamento mais
    // recente do módulo, em QUALQUER domínio (mecanismo genérico no site).
    @Query("SELECT * FROM records WHERE domainId = :domainId ORDER BY criadoEmMillis DESC LIMIT 1")
    suspend fun mostRecent(domainId: String): RecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: RecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(records: List<RecordEntity>)

    /** Substitui todo o cache de UM modulo pelo que veio do servidor, exceto
     * os registros ainda pendentes de sincronizar (senao um lancamento feito
     * offline "sumiria" da tela ate a sync terminar). */
    @Query("DELETE FROM records WHERE domainId = :domainId AND pendingCreate = 0")
    suspend fun clearSyncedForDomain(domainId: String)

    @Query("UPDATE records SET id = :novoId, pendingCreate = 0 WHERE domainId = :domainId AND id = :idAntigo")
    suspend fun marcarSincronizado(domainId: String, idAntigo: String, novoId: String)

    @Delete
    suspend fun delete(record: RecordEntity)

    // Ícone Excluir por lançamento (DomainListScreen.kt) -- exclusão local
    // imediata, mesmo princípio de createRecord/updateRecord (o app nunca
    // espera rede pra refletir a ação do usuário na tela).
    @Query("DELETE FROM records WHERE domainId = :domainId AND id = :id")
    suspend fun deleteById(domainId: String, id: String)

    // Task #124 -- grava o "expectedVersion" (última edição conhecida,
    // vinda de /api/mobile/audit-info) sem reescrever o registro inteiro
    // (evitaria sobrescrever fieldsJson/pendingCreate por engano). Usado
    // por DomainListViewModel.loadAuditInfo() sempre que a tela busca o
    // rótulo "Editado por" de cada card.
    @Query("UPDATE records SET expectedVersion = :version WHERE domainId = :domainId AND id = :id")
    suspend fun updateExpectedVersion(domainId: String, id: String, version: String)

    // Task #124 (isolamento de cache por organização) -- limpa TODO o
    // cache de registros de TODOS os módulos, não só um domainId (ver
    // clearSyncedForDomain acima). Chamado só por AuthRepository.login()
    // quando detecta que a organização recém-autenticada é diferente da
    // última conhecida neste aparelho.
    @Query("DELETE FROM records")
    suspend fun clearAll()
}

@Dao
interface PendingSyncDao {
    @Query("SELECT * FROM pending_sync ORDER BY criadoEmMillis ASC")
    fun observeAll(): Flow<List<PendingSyncEntity>>

    @Query("SELECT COUNT(*) FROM pending_sync")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM pending_sync ORDER BY criadoEmMillis ASC")
    suspend fun allOnce(): List<PendingSyncEntity>

    @Insert
    suspend fun insert(item: PendingSyncEntity): Long

    @Query("DELETE FROM pending_sync WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE pending_sync SET ultimoErro = :erro WHERE id = :id")
    suspend fun marcarErro(id: Long, erro: String)

    // Task #124 (conflito de sync) -- diferente de marcarErro: um item
    // marcado aqui não entra mais no retry automático (ver
    // RecordRepository.syncAll/hasPending), só volta a ser tentado se o
    // usuário editar o lançamento de novo (o que cria um NOVO item na
    // fila) ou se este for removido manualmente.
    @Query("UPDATE pending_sync SET conflictMessage = :mensagem WHERE id = :id")
    suspend fun marcarConflito(id: Long, mensagem: String)

    // Task #124 (isolamento de cache por organização) -- ver
    // RecordDao.clearAll(). Some com a fila pendente inteira quando a
    // organização recém-autenticada é diferente da última conhecida neste
    // aparelho (evita sincronizar um lançamento contra a organização
    // errada).
    @Query("DELETE FROM pending_sync")
    suspend fun clearAll()
}
