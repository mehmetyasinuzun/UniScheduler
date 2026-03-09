-- ============================================================
-- UniScheduler — 05: Realtime Konfigürasyonu
-- 04_storage.sql ÇALIŞTIRILMADAN ÖNCE BU DOSYA ÇALIŞTIRILMAMALIDIR
-- ============================================================

-- Uygulama schedule_assignments tablosundaki değişiklikleri
-- Supabase Realtime ile dinliyor. Bu tablonun Realtime publication'a
-- eklenmesi gerekiyor.

-- schedule_assignments → Ders programı değişikliklerinde anlık bildirim
ALTER PUBLICATION supabase_realtime ADD TABLE public.schedule_assignments;

-- schedule_drafts → Draft status değişikliklerinde bildirim
ALTER PUBLICATION supabase_realtime ADD TABLE public.schedule_drafts;

-- change_requests → İstek onay/red bildirimlerinde
ALTER PUBLICATION supabase_realtime ADD TABLE public.change_requests;

-- availability_slots → Müsaitlik güncellemelerinde
ALTER PUBLICATION supabase_realtime ADD TABLE public.availability_slots;
