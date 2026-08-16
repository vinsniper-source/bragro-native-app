package com.bragro.mobile.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SessionEntity::class,
        LookupEntity::class,
        FarmEntity::class,
        DomainConfigEntity::class,
        RecordEntity::class,
        PendingSyncEntity::class,
        DashboardEntity::class,
        DreEntity::class,
        AnalisesEntity::class,
        HomeEntity::class,
        LivroCaixaEntity::class,
        ControleInsumosEntity::class,
        OperacoesEntity::class,
    ],
    version = 10,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun lookupDao(): LookupDao
    abstract fun farmDao(): FarmDao
    abstract fun domainConfigDao(): DomainConfigDao
    abstract fun recordDao(): RecordDao
    abstract fun pendingSyncDao(): PendingSyncDao
    abstract fun dashboardDao(): DashboardDao
    abstract fun dreDao(): DreDao
    abstract fun analisesDao(): AnalisesDao
    abstract fun homeDao(): HomeDao
    abstract fun livroCaixaDao(): LivroCaixaDao
    abstract fun controleInsumosDao(): ControleInsumosDao
    abstract fun operacoesDao(): OperacoesDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "bragro.db")
                    // fallbackToDestructiveMigration() foi REMOVIDO de proposito
                    // (Task #123 -- auditoria identificou que a proxima mudanca
                    // de schema apagaria, sem aviso, todo o cache local e a fila
                    // de sincronizacao pendente do usuario). Era seguro so
                    // enquanto nenhum apk publicado dependia do schema antigo;
                    // isso deixou de ser verdade. Ver Migrations.kt pro
                    // historico completo e as regras pra quem for tocar aqui:
                    // TODA mudanca de schema agora EXIGE uma Migration real
                    // adicionada em addMigrations(...) abaixo -- sem isso o
                    // Room falha explicitamente no open do banco (em vez de
                    // destruir dado do usuario silenciosamente).
                    .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                    .build()
                    .also { instance = it }
            }
    }
}
