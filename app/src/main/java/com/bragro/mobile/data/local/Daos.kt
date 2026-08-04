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
    @Query("SELECT * FROM lookup_options WHERE category = :category ORDER BY `order` ASC")
    suspend fun byCategory(category: String): List<LookupEntity>

    @Query("DELETE FROM lookup_options")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<LookupEntity>)
}

@Dao
interface FarmDao {
    @Query("SELECT * FROM farms ORDER BY name ASC")
    suspend fun all(): List<FarmEntity>

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
}
