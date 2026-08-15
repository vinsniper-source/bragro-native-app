package com.bragro.mobile.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Task #123/#124 -- primeira Migration real do Room deste app. Ate aqui o
// AppDatabase usava fallbackToDestructiveMigration() (justificado, na epoca,
// por nenhum apk publicado ainda depender do schema antigo -- ver historico
// do comentario removido em AppDatabase.kt). Isso deixou de ser seguro: a
// PRIMEIRA vez que o app for atualizado no aparelho de um usuario real com
// cache/fila de sincronizacao pendente, uma migracao destrutiva apagaria
// tudo sem aviso. A partir de agora, TODA mudanca de schema (bump de
// "version" em AppDatabase.kt) PRECISA vir acompanhada de uma nova
// Migration aqui cobrindo exatamente as colunas/tabelas que mudaram -- sem
// isso o Room lanca IllegalStateException no primeiro open do banco depois
// do update (o app falha de forma visivel/logada, em vez de destruir dado
// do usuario silenciosamente). Quem for adicionar uma coluna/tabela nova:
// 1) adicione o campo na entidade (Entities.kt) com valor default (nullable
//    ou primitivo com default) -- ALTER TABLE ADD COLUMN exige isso; 2) bata
//    a version em AppDatabase.kt; 3) escreva a Migration correspondente
//    aqui; 4) registre em AppDatabase.databaseBuilder(...).addMigrations(...).

/** version 5 -> 6: 3 riscos resolvidos juntos numa unica migracao (auditoria
 * de sync offline, Fase 3):
 * - records.orgId / pending_sync.orgId (Task #124, isolamento de cache por
 *   organizacao) -- permite AuthRepository.login() saber, ao logar uma
 *   organizacao diferente da ultima usada neste aparelho, que a fila
 *   pendente antiga pertence a outra organizacao e deve ser limpa antes de
 *   sincronizar (evita sincronizar lancamento contra org errada).
 * - records.expectedVersion / pending_sync.expectedVersion (Task #124,
 *   deteccao de conflito) -- guarda a "ultima edicao conhecida" (timestamp
 *   ISO de RecordLastEdit.updatedAt) de cada registro, mandada de volta ao
 *   backend em toda tentativa de update; o backend (ja ajustado, ver
 *   /api/offline-sync) responde 409 CONFLICT se outro aparelho editou o
 *   mesmo registro nesse meio tempo, em vez de sobrescrever silenciosamente.
 * - pending_sync.conflictMessage (Task #124) -- marca um item da fila que
 *   bateu nesse 409, pra parar de tentar de novo automaticamente (ver
 *   RecordRepository.syncAll/hasPending) e mostrar aviso na UI (Início).
 * Todas as colunas sao aditivas (ADD COLUMN, nullable/default null) --
 * nenhuma linha existente e reescrita ou perdida. */
val MIGRATION_5_6: Migration = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE records ADD COLUMN orgId TEXT")
        db.execSQL("ALTER TABLE records ADD COLUMN expectedVersion TEXT")
        db.execSQL("ALTER TABLE pending_sync ADD COLUMN orgId TEXT")
        db.execSQL("ALTER TABLE pending_sync ADD COLUMN expectedVersion TEXT")
        db.execSQL("ALTER TABLE pending_sync ADD COLUMN conflictMessage TEXT")
    }
}

/** version 6 -> 7: seletor de fazenda no import de KML/KMZ (FieldviewScreen)
 * -- farms.id, o id real da Farm no backend, pra poder mandar farmId em
 * FieldviewImportRequest. Coluna aditiva com DEFAULT '' (nao null, mesmo
 * criterio de "primitivo com default" do comentario acima) -- farms e uma
 * tabela 100% "espelho" (clearAll + insertAll a cada bootstrap, ver
 * ConfigRepository.bootstrapAndCacheConfig), entao nenhuma linha existente
 * fica com dado incompleto por muito tempo: a proxima sincronizacao ja
 * substitui tudo. */
val MIGRATION_6_7: Migration = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE farms ADD COLUMN id TEXT NOT NULL DEFAULT ''")
    }
}

/** version 7 -> 8: tabela nova "livro_caixa" (Task #58, cache offline do
 * Livro Caixa do Produtor Rural) -- mesmo formato de blob JSON com id fixo
 * "current" que "dre"/"analises"/"home" ja usam (LivroCaixaEntity,
 * Entities.kt); tabela nova = CREATE TABLE em vez de ALTER TABLE. Colunas e
 * tipos espelham exatamente a entidade Kotlin (id/dataJson TEXT NOT NULL,
 * ano INTEGER NOT NULL, banco TEXT nullable, atualizadoEmMillis INTEGER NOT
 * NULL) -- Room valida essa correspondencia byte a byte na abertura do
 * banco depois da migracao. */
val MIGRATION_7_8: Migration = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `livro_caixa` (
              `id` TEXT NOT NULL,
              `ano` INTEGER NOT NULL,
              `banco` TEXT,
              `dataJson` TEXT NOT NULL,
              `atualizadoEmMillis` INTEGER NOT NULL,
              PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
    }
}
