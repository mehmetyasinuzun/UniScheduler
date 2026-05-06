// Centralized error message mapper — converts raw exceptions into user-friendly English messages.
// Use ErrorMessages.map(e) in all ViewModel catch blocks instead of e.message.
package com.unischeduler.util

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object ErrorMessages {

    /**
     * Maps a raw exception to a user-friendly message.
     * Falls back to the exception message if no specific mapping is found.
     */
    fun map(e: Throwable): String {
        // Known validation errors — pass through as-is
        if (e is IllegalArgumentException) return e.message ?: "Invalid input."
        if (e is IllegalStateException) return e.message ?: "Unexpected error."

        val msg = e.message.orEmpty().lowercase()

        return when {
            // Network errors
            e is UnknownHostException || e is ConnectException ->
                "No internet connection. Please check your network."
            e is SocketTimeoutException ->
                "Connection timed out. Please try again."
            msg.contains("unable to resolve host") || msg.contains("no address associated") ->
                "No internet connection. Please check your network."
            msg.contains("timeout") || msg.contains("timed out") ->
                "Connection timed out. Please try again."

            // Auth errors
            msg.contains("invalid login credentials") || msg.contains("invalid username or password") ->
                "Invalid username or password."
            msg.contains("email not confirmed") ->
                "Account not activated. Contact your administrator."
            msg.contains("user already registered") || msg.contains("already been registered") ->
                "This username is already taken."
            msg.contains("rate limit") || msg.contains("too many requests") ->
                "Too many requests. Please wait a few minutes and try again."

            // Database constraint errors
            msg.contains("duplicate key") || msg.contains("unique constraint") || msg.contains("already exists") ->
                "This record already exists. Please use a different value."
            msg.contains("violates foreign key") || msg.contains("foreign key constraint") ->
                "Cannot complete this action — related records exist."
            msg.contains("violates check constraint") ->
                "Invalid data. Please check your input values."
            msg.contains("schedule conflict") ->
                "Schedule conflict detected. The lecturer or classroom is already booked at this time."

            // RLS / permission errors
            msg.contains("new row violates row-level security") || msg.contains("rls") ->
                "You don't have permission to perform this action."
            msg.contains("jwt expired") || msg.contains("token is expired") ->
                "Your session has expired. Please log in again."
            msg.contains("not authenticated") || msg.contains("no api key") ->
                "Authentication required. Please log in again."

            // Database internal errors — hide from user
            msg.contains("stack depth") || msg.contains("max_stack_depth") ->
                "Server configuration error. Please contact your administrator."
            msg.contains("statement timeout") ->
                "The server is busy. Please try again in a moment."
            msg.contains("connection refused") || msg.contains("could not connect") ->
                "Cannot connect to the server. Please try again later."

            // Catch-all
            else -> e.message ?: "An unexpected error occurred. Please try again."
        }
    }
}
