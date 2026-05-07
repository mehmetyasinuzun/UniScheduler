# UniScheduler

## Overview
UniScheduler is a native Android application designed to streamline the process of university course scheduling. The system serves two primary roles: Administrators and Lecturers. It automates the complex task of avoiding schedule conflicts while providing an intuitive interface for data management and availability tracking.

## Architecture & Tech Stack
The project adheres to modern Android development standards, utilizing the MVVM architecture to separate UI logic from business logic and data access.

- **Language:** Kotlin
- **Architecture:** MVVM (Model-View-ViewModel) with Repository Pattern
- **Asynchrony:** Kotlin Coroutines & Flow
- **Backend:** Supabase (Auth, PostgreSQL via PostgREST, Realtime)
- **Networking:** Ktor Client
- **Data Integration:** Apache POI for Excel (.xlsx) parsing and generation

## Core Features
- **Role-Based Access Control:** Secure authentication and routing for Admin and Lecturer profiles.
- **Automated Scheduling Engine:** An algorithmic approach to distributing courses based on lecturer availability, classroom capacity, and organizational constraints. Includes built-in conflict detection.
- **Bulk Data Operations:** Support for importing and exporting schedule data, lecturer lists, and course catalogs via Excel files.
- **Lecturer Portal:** Allows academic staff to submit their weekly availability and view assigned courses in real-time.
- **Admin Dashboard:** Provides comprehensive oversight over the entire academic schedule, including manual overrides and data management.

## Setup Instructions

### Prerequisites
- Android Studio (Iguana or newer recommended)
- JDK 17
- A Supabase project instance

### Environment Configuration
The application requires Supabase credentials to communicate with the backend. These must be provided securely via a `local.properties` file.

1. Clone the repository.
2. In the root directory of the project, create a file named `local.properties` if it does not already exist.
3. Add your Supabase project credentials to the file:
   ```
   SUPABASE_URL=https://your-project-id.supabase.co
   SUPABASE_ANON_KEY=your-anon-key
   ```
4. Sync the project with Gradle files.
5. Build and run the application on an emulator or physical device running Android 8.0 (API level 26) or higher.

## License

**Proprietary and Confidential**

Copyright (c) 2026. All rights reserved.

This software and its documentation are proprietary. Unauthorized copying, distribution, modification, or use of this software, via any medium, is strictly prohibited. Any unauthorized use will result in legal action. For licensing or usage inquiries, please contact the repository owner.
