-- UniScheduler Temizlik ve Güncelleme Sorguları (DİKKAT: Mevcut sisteme zarar vermez, sadece temizler ve ekler)

-- 1. ADIM: Eski (Kullanılmayan) tabloları, sütunları ve rolleri temizle
-- Departments tablosundan dept_head_permission sütununu sil
ALTER TABLE departments DROP COLUMN IF EXISTS dept_head_permission;

-- Change_requests tablosundan department head onayına dair olan gereksiz sütunları sil
ALTER TABLE change_requests DROP COLUMN IF EXISTS dept_head_status;
ALTER TABLE change_requests DROP COLUMN IF EXISTS dept_head_reviewed_by;
ALTER TABLE change_requests DROP COLUMN IF EXISTS dept_head_note;
ALTER TABLE change_requests DROP COLUMN IF EXISTS dept_head_reviewed_at;
ALTER TABLE change_requests DROP COLUMN IF EXISTS approval_mode;

-- Kullanıcı rollerinden STUDENT ve DEPT_HEAD yetkilerini düşür
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('ADMIN', 'LECTURER'));


-- 2. ADIM: Hocaların Müsaitlik (Availability) Tablosunu Oluştur (Yeni Özelliğimiz)
CREATE TABLE IF NOT EXISTS lecturer_availability (
    id SERIAL PRIMARY KEY,
    lecturer_id INTEGER NOT NULL REFERENCES lecturers(id) ON DELETE CASCADE,
    day_index INTEGER NOT NULL CHECK (day_index >= 0 AND day_index <= 4), -- 0: Pzt, 4: Cum
    hour_index INTEGER NOT NULL CHECK (hour_index >= 0 AND hour_index <= 8), -- 0: 09:00, 8: 17:00
    is_available BOOLEAN DEFAULT TRUE,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()),
    UNIQUE(lecturer_id, day_index, hour_index) -- Aynı hocanın aynı gün ve saatine 2 kayıt girilmemesi için
);

-- Ek güvenlik ve performans indeksleri
CREATE INDEX IF NOT EXISTS idx_availability_lecturer ON lecturer_availability(lecturer_id);
