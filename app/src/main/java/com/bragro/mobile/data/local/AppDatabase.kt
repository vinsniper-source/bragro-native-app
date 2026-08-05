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
    ],
    version = 5,
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

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "bragro.db")
                    // Fase 1 ainda nao foi lancada (nenhum apk publicado com o
                    // schema antigo) -- destrutiva e segura aqui e evita ter
                    // que escrever uma Migration manual so pra uma tabela nova
                    // (dashboard, Task #31) num app que ainda ninguem instalou.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
