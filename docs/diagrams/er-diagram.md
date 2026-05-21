# UniScheduler — Veritabanı ER Diyagramı

Aşağıdaki diyagram `supabase/schema.sql`'deki 14 tablonun gerçek
ilişkisini gösterir. GitHub Markdown'ı Mermaid'i otomatik render eder.

## Tam Şema

```mermaid
erDiagram
    organizations ||--|| org_settings : "1:1"
    organizations ||--o{ users : "has"
    organizations ||--o{ departments : "has"
    organizations ||--o{ lecturers : "has"
    organizations ||--o{ courses : "has"
    organizations ||--o{ classrooms : "has"
    organizations ||--o{ offerings : "has"
    organizations ||--o{ schedule_entries : "has"
    organizations ||--o{ lecturer_availability : "has"
    organizations ||--o{ client_error_logs : "logs"
    organizations ||--o{ audit_log : "audited by"

    users ||--o| lecturers : "may be"
    users }o--|| departments : "(via lecturer)"

    departments ||--o{ lecturers : "employs"
    departments ||--o{ courses : "offers"
    departments ||--o{ classrooms : "owns"

    courses ||--o{ offerings : "opened as"
    lecturers ||--o{ offerings : "teaches (optional)"
    lecturers ||--o{ schedule_entries : "scheduled in"
    lecturers ||--o{ lecturer_availability : "blocks time"

    offerings ||--o{ schedule_entries : "appears in"
    classrooms ||--o{ schedule_entries : "hosts"

    organizations {
        int id PK
        text name
        text code "UNIQUE [A-Z0-9_-]{2,20}"
        timestamp created_at
        timestamp updated_at
    }

    org_settings {
        int org_id PK,FK
        int time_step_minutes "5-60"
        text_array active_days "Mon-Fri default"
        text day_start "HH:MM"
        text day_end "HH:MM"
    }

    users {
        uuid id PK,FK "→ auth.users"
        int org_id FK
        text username "UNIQUE [a-z0-9_]{3,40}"
        text role "admin|lecturer"
        bool must_change_password
        bool is_active
        timestamp deleted_at "soft delete"
        timestamp last_login_at
    }

    departments {
        int id PK
        int org_id FK
        text name
        timestamp deleted_at
    }

    lecturers {
        int id PK
        int org_id FK
        uuid user_id FK "UNIQUE → users"
        text title
        text first_name
        text last_name
        citext email
        text phone
        int department_id FK
        timestamp deleted_at
    }

    courses {
        int id PK
        int org_id FK
        text code "UNIQUE per org"
        text name
        int theory_hours "0-20"
        int lab_hours "0-20"
        int credits "0-30"
        int department_id FK
        timestamp deleted_at
    }

    classrooms {
        int id PK
        int org_id FK
        text room_code "UNIQUE per org"
        int capacity "1-1000"
        text type "theory|lab"
        int department_id FK
        timestamp deleted_at
    }

    offerings {
        int id PK
        int org_id FK
        int course_id FK
        int lecturer_id FK "nullable"
        text academic_year "YYYY-YYYY"
        text term "Fall|Spring|Summer"
        int class_year "1-4"
        text section "A-Z0-9{1,4}"
        int capacity "0-1000"
        timestamp deleted_at
    }

    schedule_entries {
        int id PK
        int org_id FK
        int offering_id FK
        int lecturer_id FK
        int classroom_id FK
        text day "Mon-Sun"
        text start_time "HH:MM"
        text end_time "HH:MM"
    }

    lecturer_availability {
        int id PK
        int org_id FK
        int lecturer_id FK
        text day
        text start_time
        text end_time
        text note
    }

    client_error_logs {
        bigint id PK
        int org_id FK
        uuid user_id FK
        text username
        text role
        text screen
        text action
        text message
        text stack_trace
        text app_version
        text device_model
        text os_version
        text source "mobile|panel|server"
    }

    audit_log {
        bigint id PK
        int org_id
        uuid actor_id
        text actor_role
        text table_name
        text record_id
        text operation "INSERT|UPDATE|DELETE"
        jsonb old_data
        jsonb new_data
        timestamp created_at
    }
```

## Çok-Kiracılı İzolasyon (org_id = Tenant Key)

```mermaid
flowchart LR
    A[Mobile App<br/>JWT token] -->|"select * from lecturers"| B[Supabase Postgrest]
    B -->|"current_org_id() = 1"| C{"RLS Policy<br/>org_id = 1"}
    C -->|"WHERE org_id = 1"| D[(lecturers)]
    D -->|"yalnızca org 1 satırları"| A

    E[Mobile App<br/>JWT token<br/>org_id=2] -.->|"select * from lecturers"| B
    B -.->|"current_org_id() = 2"| F{"RLS Policy<br/>org_id = 2"}
    F -.->|"WHERE org_id = 2"| D
    D -.->|"yalnızca org 2 satırları"| E

    style C fill:#e3f2fd,stroke:#1565c0
    style F fill:#fff3e0,stroke:#e65100
    style D fill:#f3e5f5,stroke:#6a1b9a
```

`current_org_id()` SECURITY DEFINER fonksiyonu her isteğe JWT'nin
`sub` claim'inden bağlı kullanıcının `org_id`'sini döner. Her tablonun
SELECT/INSERT/UPDATE/DELETE policy'leri bu değere göre satırları filtreler.

## Çakışma Engeli (Race-Condition Korumalı)

```mermaid
sequenceDiagram
    participant A1 as Admin A
    participant A2 as Admin B
    participant DB as PostgreSQL
    participant T as prevent_schedule_overlap<br/>(BEFORE INSERT trigger)

    A1->>DB: BEGIN; INSERT schedule_entries<br/>(L=42, R=15, Pzt 10:00-12:00)
    A2->>DB: BEGIN; INSERT schedule_entries<br/>(L=42, R=15, Pzt 10:00-12:00)

    DB->>T: trigger fires (TX_A)
    T->>DB: SELECT FROM schedule_entries<br/>WHERE org_id=1 AND day='Mon' ...
    DB-->>T: 0 satır (henüz yok)
    T->>DB: OK
    DB->>A1: INSERT başarılı, COMMIT

    DB->>T: trigger fires (TX_B)
    T->>DB: SELECT FROM schedule_entries WHERE ...
    DB-->>T: 1 satır (Admin A'nın commit'i)
    T->>DB: RAISE EXCEPTION 'Schedule conflict'
    DB->>A2: ❌ check_violation, ROLLBACK
```

İki admin aynı anda atama yapsa bile, trigger transaction içinde son
durumu okur ve ikinci insert'i reddeder. TOCTOU yarış koşulu kapalı.
```

## Login + Şifre Değiştirme Akışı

```mermaid
sequenceDiagram
    participant U as Kullanıcı
    participant App as Mobile App
    participant Auth as Supabase Auth
    participant DB as users tablosu
    participant LA as login_attempts

    U->>App: "halit_bakir" + "xT4k9Z"
    App->>App: username → username@unischeduler.app
    App->>Auth: signInWithPassword(email, pwd)
    Auth-->>App: JWT (sub = UUID)
    App->>DB: SELECT role, must_change_password, org_id WHERE id=UUID
    DB-->>App: { role: 'lecturer', must_change_password: true, org_id: 1 }
    App->>LA: INSERT login_attempts (succeeded=true, ip, device_id)

    alt must_change_password = true
        App->>App: PasswordChangeFragment'a yönlendir
        U->>App: yeni şifre + onay
        App->>Auth: updateUser(password = new)
        App->>DB: UPDATE users SET must_change_password=false
        App->>App: Lecturer Home'a yönlendir
    else
        App->>App: Lecturer Home'a yönlendir
    end
```

## Atama Akışı (Çakışma Kontrolü Dahil)

```mermaid
sequenceDiagram
    participant Admin
    participant Fragment as AssignmentFragment
    participant VM as AssignmentViewModel
    participant Repo as ScheduleRepository
    participant DB as schedule_entries
    participant Trig as overlap trigger

    Admin->>Fragment: Offering, Hoca, Derslik, Gün, Saat seç + "ATA"
    Fragment->>VM: assign(offeringId, lecturerId, classroomId, day, start, end)
    VM->>Repo: checkConflicts(...)
    Repo->>DB: SELECT WHERE lecturer_id=L AND day=D AND overlap(...)
    Repo->>DB: SELECT WHERE classroom_id=C AND day=D AND overlap(...)
    Repo->>Repo: check availability (lecturer_availability)
    DB-->>Repo: 0 satır
    Repo-->>VM: { conflicts: empty, availability: ok }

    VM->>Repo: insertEntry(...)
    Repo->>DB: INSERT schedule_entries(...)
    DB->>Trig: BEFORE INSERT — prevent_schedule_overlap
    Trig->>DB: SELECT for re-check (race-safe)
    DB-->>Trig: 0 satır
    Trig-->>DB: OK
    DB-->>Repo: success
    Repo-->>VM: UiState.Success
    VM-->>Fragment: Snackbar "Atama eklendi"
```

## Bileşen Mimarisi

```mermaid
flowchart TB
    subgraph Mobile["📱 Mobile App (Android · Kotlin · MVVM)"]
        UI[UI Layer<br/>Fragments + ViewBinding]
        VM[ViewModel<br/>StateFlow + UiState]
        Repo[Repository<br/>Suspend functions]
        SK[Supabase Kotlin SDK<br/>auth + postgrest + realtime]
    end

    subgraph Panel["🖥️ Süper-Admin Paneli (Node.js)"]
        Web[Express + Helmet + CORS<br/>HSTS + CSP + SRI]
        WebUI[Vanilla JS + Bootstrap 5<br/>i18n + Dark mode]
        SrvSDK[Supabase JS SDK<br/>service_role]
    end

    subgraph Supabase["☁️ Supabase Cloud"]
        Auth["Auth (GoTrue)<br/>JWT + bcrypt"]
        PG[(PostgreSQL 15<br/>14 tablo + RLS + Trigger)]
        Realtime[Realtime<br/>WebSocket]
    end

    UI <--> VM
    VM <--> Repo
    Repo <--> SK
    SK -->|anon key + JWT<br/>RLS uygulanır| Auth
    SK -->|anon key + JWT<br/>RLS uygulanır| PG
    SK <-->|WebSocket subscribe| Realtime

    WebUI <--> Web
    Web <--> SrvSDK
    SrvSDK -->|service_role<br/>RLS bypass| PG
    SrvSDK -->|admin API| Auth

    style Mobile fill:#e3f2fd,stroke:#1565c0
    style Panel fill:#f3e5f5,stroke:#6a1b9a
    style Supabase fill:#e8f5e9,stroke:#2e7d32
```

## Klasör Yapısı

```
UniScheduler/
├── app/                        ← Android mobile app
│   ├── src/main/java/com/unischeduler/
│   │   ├── data/
│   │   │   ├── model/          ← @Serializable data class'lar
│   │   │   ├── remote/         ← SupabaseClient
│   │   │   └── repository/     ← 10 repository
│   │   ├── notif/              ← AlarmManager, BootReceiver, ReminderScheduler
│   │   ├── scheduler/          ← Otomatik program algoritması
│   │   ├── ui/
│   │   │   ├── admin/          ← 5 Fragment + 5 ViewModel
│   │   │   ├── auth/           ← Login, PasswordChange
│   │   │   ├── lecturer/       ← Home, Calendar, Availability
│   │   │   └── shared/         ← WeeklyScheduleView, AvailabilityGridView
│   │   └── util/               ← Crash, ErrorReport, Excel, iCal, PDF, JSON
│   └── src/main/res/
│       ├── drawable/           ← Material vector icons
│       ├── layout/             ← 24 layout XML
│       ├── layout-sw600dp/     ← Tablet variant
│       ├── values/             ← strings (TR), themes, dimens, colors
│       ├── values-en/          ← strings (EN)
│       ├── values-night/       ← Dark theme colors
│       └── values-sw600dp/     ← Tablet dimens
│
├── super-admin-paneli/         ← Web panel (Node.js)
│   ├── public/
│   │   ├── css/style.css       ← CSS variables + dark theme
│   │   ├── js/
│   │   │   ├── app.js          ← Ana panel logic
│   │   │   ├── i18n.js         ← Çok dilli runtime
│   │   │   ├── theme.js        ← Light/dark/system
│   │   │   └── geo.js          ← IP → ülke lookup
│   │   ├── i18n/{tr,en}.json   ← 200+ key tam parite
│   │   └── index.html          ← data-i18n + SRI hash
│   └── server.js               ← Express + CTI + Alerting webhook
│
├── supabase/                   ← Database
│   ├── schema.sql              ← Tek-dosya kurulum
│   ├── migrations/             ← dbmate versioned migrations
│   └── README.md               ← Production deploy runbook
│
├── .github/                    ← CI/CD
│   ├── workflows/
│   │   ├── android-build.yml   ← Push'ta APK + test
│   │   ├── panel-check.yml     ← Panel syntax + i18n parite
│   │   └── release.yml         ← Tag'de otomatik GitHub Release
│   ├── dependabot.yml          ← Haftalık dep güncelleme
│   └── ISSUE_TEMPLATE/         ← Bug + feature template'leri
│
├── docs/
│   ├── diagrams/               ← Bu dosya
│   └── screenshots/
│
└── README.md
```
