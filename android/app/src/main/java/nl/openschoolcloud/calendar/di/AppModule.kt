/*
 * OpenSchoolCloud Calendar
 * Copyright (C) 2025 OpenSchoolCloud / Aldewereld Consultancy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package nl.openschoolcloud.calendar.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import nl.openschoolcloud.calendar.BuildConfig
import nl.openschoolcloud.calendar.data.local.AppDatabase
import nl.openschoolcloud.calendar.data.local.dao.AccountDao
import nl.openschoolcloud.calendar.data.local.dao.CalendarDao
import nl.openschoolcloud.calendar.data.local.dao.EventDao
import nl.openschoolcloud.calendar.data.local.dao.HolidayDao
import nl.openschoolcloud.calendar.data.local.dao.ReflectionDao
import nl.openschoolcloud.calendar.data.repository.AccountRepositoryImpl
import nl.openschoolcloud.calendar.data.repository.BookingRepositoryImpl
import nl.openschoolcloud.calendar.data.repository.CalendarRepositoryImpl
import nl.openschoolcloud.calendar.data.repository.EventRepositoryImpl
import nl.openschoolcloud.calendar.data.repository.HolidayRepositoryImpl
import nl.openschoolcloud.calendar.data.repository.ReflectionRepositoryImpl
import nl.openschoolcloud.calendar.domain.repository.AccountRepository
import nl.openschoolcloud.calendar.domain.repository.BookingRepository
import nl.openschoolcloud.calendar.domain.repository.CalendarRepository
import nl.openschoolcloud.calendar.domain.repository.EventRepository
import nl.openschoolcloud.calendar.domain.repository.HolidayRepository
import nl.openschoolcloud.calendar.domain.repository.ReflectionRepository
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt module providing application-wide dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            })
            .build()
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "openschoolcloud_calendar.db"
        )
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()
    }

    @Provides
    fun provideAccountDao(db: AppDatabase): AccountDao = db.accountDao()

    @Provides
    fun provideCalendarDao(db: AppDatabase): CalendarDao = db.calendarDao()

    @Provides
    fun provideEventDao(db: AppDatabase): EventDao = db.eventDao()

    @Provides
    fun provideHolidayDao(db: AppDatabase): HolidayDao = db.holidayDao()

    @Provides
    fun provideReflectionDao(db: AppDatabase): ReflectionDao = db.reflectionDao()
}

/**
 * Hilt module binding repository implementations to interfaces
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAccountRepository(impl: AccountRepositoryImpl): AccountRepository

    @Binds
    @Singleton
    abstract fun bindCalendarRepository(impl: CalendarRepositoryImpl): CalendarRepository

    @Binds
    @Singleton
    abstract fun bindEventRepository(impl: EventRepositoryImpl): EventRepository

    @Binds
    @Singleton
    abstract fun bindBookingRepository(impl: BookingRepositoryImpl): BookingRepository

    @Binds
    @Singleton
    abstract fun bindHolidayRepository(impl: HolidayRepositoryImpl): HolidayRepository

    @Binds
    @Singleton
    abstract fun bindReflectionRepository(impl: ReflectionRepositoryImpl): ReflectionRepository
}
