# UniScheduler

> 🇹🇷 **Hızlı başlangıç (kurulum, deployment, test, sorun giderme):** [TESLIM_REHBERI.md](TESLIM_REHBERI.md)

## Overview
UniScheduler is a comprehensive, native Android application engineered to solve one of the most notoriously complex administrative challenges in higher education: **academic course scheduling**. 

At its core, UniScheduler is a centralized hub where administrative oversight meets lecturer autonomy. It eliminates the friction of manual scheduling, prevents logical impossibilities (like double-booking a professor or over-capacitating a classroom), and establishes a single source of truth for an institution's academic calendar.

## The Problem It Solves

Traditional university scheduling is typically a chaotic process relying on fragmented emails, disconnected spreadsheets, and manual cross-referencing. This archaic approach leads to inevitable human errors. UniScheduler was built to directly address and eliminate these pain points:

- **The Double-Booking Dilemma:** Administrators often mistakenly assign a single lecturer to two different courses at the exact same time. UniScheduler's **Conflict Detection Engine** actively monitors assignments in real-time, instantly blocking or flagging overlapping schedules.
- **The Availability Disconnect:** Tracking when dozens of lecturers are actually available to teach is a logistical nightmare. UniScheduler introduces a **Lecturer Portal** where academic staff input their precise weekly availability. The system strictly respects these boundaries during schedule generation.
- **Data Entry Fatigue:** Manually inputting hundreds of courses, classrooms, and personnel into a new system is unscalable. UniScheduler leverages **Bulk Data Operations**, allowing administrators to upload and sync entire databases instantly using standard Excel (`.xlsx`) files.
- **The "Tetris" Problem (Automated Scheduling):** Fitting courses into limited classrooms while respecting lecturer constraints is mathematically complex. UniScheduler features an **Automated Scheduling Engine** that algorithmically drafts optimal, conflict-free schedules, turning weeks of administrative headache into a one-click operation.

## Architecture & Tech Stack
The project adheres to modern Android development standards, utilizing the MVVM architecture to separate UI logic from business logic and data access, ensuring maintainability and scalability.

- **Language:** Kotlin
- **Architecture:** MVVM (Model-View-ViewModel) with Repository Pattern
- **Asynchrony:** Kotlin Coroutines & Flow
- **Backend:** Supabase (Auth, PostgreSQL via PostgREST, Realtime)
- **Networking:** Ktor Client
- **Data Integration:** Apache POI for Excel parsing and generation

## Core Capabilities

### 1. Advanced Conflict Resolution
The system does not just warn about conflicts; it prevents them. Whether an administrator is manually dragging and dropping a course or the auto-scheduler is running, the underlying logic verifies lecturer availability, classroom capacity, and existing time slot occupancy before allowing a commit.

### 2. Algorithmic Auto-Scheduling
By analyzing the cross-section of institutional needs (course credits, classroom sizes) and constraints (lecturer time-offs), the application can generate a baseline weekly schedule automatically. Administrators can then fine-tune this output, saving immense amounts of preliminary planning time.

### 3. Real-Time Synchronization
Powered by Supabase Realtime, schedule updates pushed by an administrator instantly reflect on a lecturer's personal device. No more printed schedules or outdated PDF attachments.

### 4. Excel-Driven Workflows
Recognizing that universities operate heavily on spreadsheets, the system supports bidirectional Excel integration. Export the final schedule for printing or import bulk user data to instantly populate the application state.

## Setup Instructions

### Prerequisites
- Android Studio (Iguana or newer recommended)
- JDK 17
- A Supabase project instance

### Environment Configuration
The application requires Supabase credentials to communicate with the backend. These must be provided securely via a `local.properties` file.

1. Clone the repository.
2. Copy `local.properties.example` to `local.properties`.
3. Fill in `SUPABASE_URL` and `SUPABASE_ANON_KEY` (and optionally release-signing keystore values).
4. Run the SQL files in `supabase/` against your Supabase project (see [TESLIM_REHBERI.md §3](TESLIM_REHBERI.md)).
5. Build:
   - Debug: `./gradlew assembleDebug`
   - Release (signed, minified): `./gradlew assembleRelease`
6. Companion **super-admin web panel** lives in `super-admin-paneli/` — see [TESLIM_REHBERI.md §4](TESLIM_REHBERI.md).

## License

**Proprietary and Confidential**

Copyright (c) 2026. All rights reserved.

This software and its documentation are proprietary. Unauthorized copying, distribution, modification, or use of this software, via any medium, is strictly prohibited. Any unauthorized use will result in legal action. For licensing or usage inquiries, please contact the repository owner.
