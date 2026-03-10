package com.unischeduler.di

import com.unischeduler.data.repository.AuthRepositoryImpl
import com.unischeduler.data.repository.CourseRepositoryImpl
import com.unischeduler.data.repository.DraftRepositoryImpl
import com.unischeduler.data.repository.LecturerRepositoryImpl
import com.unischeduler.data.repository.RequestRepositoryImpl
import com.unischeduler.data.repository.ScheduleRepositoryImpl
import com.unischeduler.data.repository.SettingsRepositoryImpl
import com.unischeduler.domain.repository.AuthRepository
import com.unischeduler.domain.repository.CourseRepository
import com.unischeduler.domain.repository.DraftRepository
import com.unischeduler.domain.repository.LecturerRepository
import com.unischeduler.domain.repository.RequestRepository
import com.unischeduler.domain.repository.ScheduleRepository
import com.unischeduler.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindCourseRepository(impl: CourseRepositoryImpl): CourseRepository

    @Binds
    @Singleton
    abstract fun bindLecturerRepository(impl: LecturerRepositoryImpl): LecturerRepository

    @Binds
    @Singleton
    abstract fun bindScheduleRepository(impl: ScheduleRepositoryImpl): ScheduleRepository

    @Binds
    @Singleton
    abstract fun bindDraftRepository(impl: DraftRepositoryImpl): DraftRepository

    @Binds
    @Singleton
    abstract fun bindRequestRepository(impl: RequestRepositoryImpl): RequestRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
