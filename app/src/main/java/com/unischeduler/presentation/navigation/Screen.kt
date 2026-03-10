package com.unischeduler.presentation.navigation

import kotlinx.serialization.Serializable

@Serializable sealed interface Screen {
    @Serializable data object Splash : Screen
    @Serializable data object Login : Screen
    @Serializable data object RegisterWithCode : Screen
    @Serializable data object Home : Screen
    @Serializable data object Calendar : Screen
    @Serializable data object Data : Screen
    @Serializable data object Settings : Screen
    @Serializable data object Drafts : Screen
    @Serializable data object ImportPreview : Screen
    @Serializable data object ScheduleConfig : Screen
    @Serializable data object Alternatives : Screen
    @Serializable data class DraftEditor(val draftId: Int = 0) : Screen
    @Serializable data class DraftReview(val draftId: Int) : Screen
    @Serializable data object Requests : Screen
    @Serializable data object CreateRequest : Screen
    @Serializable data class RequestDetail(val requestId: Int) : Screen
}
