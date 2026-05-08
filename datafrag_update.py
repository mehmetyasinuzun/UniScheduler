import os

def replace_all(filepath, replacements):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    original = content
    for old, new in replacements:
        content = content.replace(old, new)
    if content != original:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"{os.path.basename(filepath)} updated")
    else:
        print(f"{os.path.basename(filepath)} no changes")

base = 'C:/Users/Yasin/Downloads/week10_mobile/UniScheduler/app/src/main/java/com/unischeduler'

# DataFragment.kt
replace_all(f'{base}/ui/admin/DataFragment.kt', [
    ('Toast.makeText(requireContext(), "Dersler başarıyla dışa aktarıldı.", Toast.LENGTH_SHORT).show()', 'Toast.makeText(requireContext(), getString(R.string.data_course_export_success), Toast.LENGTH_SHORT).show()'),
    ('Toast.makeText(requireContext(), "Hocalar başarıyla dışa aktarıldı.", Toast.LENGTH_SHORT).show()', 'Toast.makeText(requireContext(), getString(R.string.data_lecturer_export_success), Toast.LENGTH_SHORT).show()'),
    ('Toast.makeText(requireContext(), "Dışa aktarma başarısız: ${e.message}", Toast.LENGTH_LONG).show()', 'Toast.makeText(requireContext(), getString(R.string.data_export_fail, e.message), Toast.LENGTH_LONG).show()'),
    ('Toast.makeText(requireContext(), "Hoca silindi.", Toast.LENGTH_SHORT).show()', 'Toast.makeText(requireContext(), getString(R.string.data_lecturer_deleted), Toast.LENGTH_SHORT).show()'),
    ('Toast.makeText(requireContext(), "Hoca güncellendi.", Toast.LENGTH_SHORT).show()', 'Toast.makeText(requireContext(), getString(R.string.data_lecturer_updated), Toast.LENGTH_SHORT).show()'),
    ('Toast.makeText(requireContext(), "Ders kaydedildi.", Toast.LENGTH_SHORT).show()', 'Toast.makeText(requireContext(), getString(R.string.data_course_added), Toast.LENGTH_SHORT).show()'),
    ('Toast.makeText(requireContext(), "Açılan ders eklendi.", Toast.LENGTH_SHORT).show()', 'Toast.makeText(requireContext(), getString(R.string.data_offering_added), Toast.LENGTH_SHORT).show()'),
    ('Toast.makeText(requireContext(), "Açılan ders güncellendi.", Toast.LENGTH_SHORT).show()', 'Toast.makeText(requireContext(), getString(R.string.data_offering_updated), Toast.LENGTH_SHORT).show()'),
    ('Toast.makeText(requireContext(), "${state.data} course(s) imported successfully.", Toast.LENGTH_SHORT).show()', 'Toast.makeText(requireContext(), getString(R.string.data_course_import_count, state.data), Toast.LENGTH_SHORT).show()'),
    ('.setTitle("Dersi Düzenle")', '.setTitle(getString(R.string.data_course_edit_title))'),
    ('.setTitle("Dersi Sil")', '.setTitle(getString(R.string.data_course_delete_title))'),
    ('.setTitle("Açılan Dersi Düzenle")', '.setTitle(getString(R.string.data_offering_edit_title))'),
    ('.setTitle("Açılan Dersi Sil")', '.setTitle(getString(R.string.data_offering_delete_title))'),
    ('.setTitle("Hocayı Düzenle")', '.setTitle(getString(R.string.data_lecturer_edit_title))'),
    ('.setTitle("Hocayı Sil")', '.setTitle(getString(R.string.data_lecturer_delete_title))'),
    ('.setTitle("Hoca Eklendi")', '.setTitle(getString(R.string.data_lecturer_added_title))'),
    ('.setTitle("İçe Aktarma Tamamlandı")', '.setTitle(getString(R.string.import_result_title))'),
    ('.setMessage("${course.code} — ${course.name} silinsin mi?\\nİlgili açılan dersler de silinecek.")', '.setMessage(getString(R.string.data_course_delete_message, course.code, course.name))'),
    ('.setMessage("${offering.courseName} ${offering.section} silinsin mi? İlgili program kayıtları da silinecek.")', '.setMessage(getString(R.string.data_offering_delete_message, offering.courseName, offering.section))'),
    ('.setMessage("${lecturer.fullName} silinsin mi? Hocanın giriş hesabı da silinir.")', '.setMessage(getString(R.string.data_lecturer_delete_message, lecturer.fullName))'),
    ('binding.tvLecturerHeader.text = "Hocalar (${filteredLecturers.size})"', 'binding.tvLecturerHeader.text = getString(R.string.data_lecturers_header_count, filteredLecturers.size)'),
    ('binding.tvCourseHeader.text = "Dersler (${filteredCourses.size})"', 'binding.tvCourseHeader.text = getString(R.string.data_courses_header_count, filteredCourses.size)'),
    ('binding.tvOfferingHeader.text = "Ders Açma (${filteredOfferings.size})"', 'binding.tvOfferingHeader.text = getString(R.string.data_offerings_header_count, filteredOfferings.size)'),
    ('text = "Henüz hoca eklenmedi."', 'text = getString(R.string.data_lecturer_empty)'),
    ('val names = listOf("— Atanmadı —") + lecturers.map { it.fullName }', 'val names = listOf(getString(R.string.common_not_assigned)) + lecturers.map { it.fullName }'),
    ('val lecturerNames = listOf("— Atanmadı —") + lecturers.map { it.fullName }', 'val lecturerNames = listOf(getString(R.string.common_not_assigned)) + lecturers.map { it.fullName }'),
    ('val filterNames = listOf("Tüm Bölümler") + names', 'val filterNames = listOf(getString(R.string.common_all_departments)) + names'),
    ('showFieldError(binding.tvLecturerAddError, "No departments yet. Add one in Settings.")', 'showFieldError(binding.tvLecturerAddError, getString(R.string.data_no_dept_error))'),
    ('showFieldError(binding.tvCourseAddError, "No departments yet. Add one in Settings.")', 'showFieldError(binding.tvCourseAddError, getString(R.string.data_no_dept_error))'),
    ('showFieldError(binding.tvOfferingError, "No courses yet. Add a course first.")', 'showFieldError(binding.tvOfferingError, getString(R.string.data_no_courses_error))'),
    ('Toast.makeText(requireContext(), "Önce bölümleri yükleyin.", Toast.LENGTH_LONG).show()', 'Toast.makeText(requireContext(), getString(R.string.import_load_depts_first), Toast.LENGTH_LONG).show()'),
    ('Toast.makeText(requireContext(), "Panoya kopyalandı.", Toast.LENGTH_SHORT).show()', 'Toast.makeText(requireContext(), getString(R.string.common_copied_clipboard), Toast.LENGTH_SHORT).show()'),
    ('Toast.makeText(requireContext(), "Tüm kullanıcılar panoya kopyalandı.", Toast.LENGTH_SHORT).show()', 'Toast.makeText(requireContext(), getString(R.string.data_all_users_copied), Toast.LENGTH_SHORT).show()'),
    ('.setNeutralButton("Panoya Kopyala")', '.setNeutralButton(getString(R.string.common_copy_clipboard))'),
    ('.setNeutralButton("Kullanıcıları Kopyala")', '.setNeutralButton(getString(R.string.data_copy_users))'),
    ('.setPositiveButton("Kaydet")', '.setPositiveButton(getString(R.string.common_save))'),
    ('.setPositiveButton("Sil")', '.setPositiveButton(getString(R.string.common_delete))'),
    ('.setPositiveButton("Tamam", null)', '.setPositiveButton(getString(R.string.common_ok), null)'),
    ('.setNegativeButton("İptal", null)', '.setNegativeButton(getString(R.string.common_cancel), null)'),
])
print("DataFragment.kt done")

# AssignmentFragment.kt
replace_all(f'{base}/ui/admin/AssignmentFragment.kt', [
    ('Toast.makeText(requireContext(), "Atama başarılı.", Toast.LENGTH_SHORT).show()', 'Toast.makeText(requireContext(), getString(R.string.assignment_success), Toast.LENGTH_SHORT).show()'),
    ('binding.tvSaveError.text = "Lütfen geçerli bir ders ve sınıf seçin."', 'binding.tvSaveError.text = getString(R.string.assignment_invalid_selection)'),
    ('.setTitle("Çakışma Tespit Edildi")', '.setTitle(getString(R.string.assignment_conflict_title))'),
    ('.setPositiveButton("Yine de Ata")', '.setPositiveButton(getString(R.string.assignment_force_assign))'),
    ('.setNegativeButton("İptal")', '.setNegativeButton(getString(R.string.common_cancel))'),
    ('.setPositiveButton("Sil")', '.setPositiveButton(getString(R.string.common_delete))'),
    ('.setNegativeButton("İptal", null)', '.setNegativeButton(getString(R.string.common_cancel), null)'),
    ('.setTitleText("Select time")', '.setTitleText(getString(R.string.common_select_time))'),
    ('text = "Sınıf: ${entry.classroomCode}"', 'text = getString(R.string.assignment_classroom_label, entry.classroomCode)'),
])
print("AssignmentFragment.kt done")

# SettingsFragment.kt
replace_all(f'{base}/ui/admin/SettingsFragment.kt', [
    ('Toast.makeText(requireContext(), "Bölüm eklendi.", Toast.LENGTH_SHORT).show()', 'Toast.makeText(requireContext(), getString(R.string.settings_dept_added), Toast.LENGTH_SHORT).show()'),
    ('Toast.makeText(requireContext(), "Bölüm güncellendi.", Toast.LENGTH_SHORT).show()', 'Toast.makeText(requireContext(), getString(R.string.settings_dept_updated), Toast.LENGTH_SHORT).show()'),
    ('Toast.makeText(requireContext(), "Bölüm silindi.", Toast.LENGTH_SHORT).show()', 'Toast.makeText(requireContext(), getString(R.string.settings_dept_deleted), Toast.LENGTH_SHORT).show()'),
    ('.setTitle("Bölümü Düzenle")', '.setTitle(getString(R.string.settings_dept_edit_title))'),
    ('.setTitle("Bölümü Sil")', '.setTitle(getString(R.string.settings_dept_delete_title))'),
    ('.setMessage("\\"${dept.name}\\" silinsin mi? İlgili hoca ve dersler etkilenebilir.")', '.setMessage(getString(R.string.settings_dept_delete_message, dept.name))'),
    ('.setPositiveButton("Kaydet")', '.setPositiveButton(getString(R.string.common_save))'),
    ('.setPositiveButton("Sil")', '.setPositiveButton(getString(R.string.common_delete))'),
    ('.setNegativeButton("İptal", null)', '.setNegativeButton(getString(R.string.common_cancel), null)'),
])
print("SettingsFragment.kt done")

# AvailabilityFragment.kt
replace_all(f'{base}/ui/lecturer/AvailabilityFragment.kt', [
    ('.setTitle("Meşgul Slot Kaldır")', '.setTitle(getString(R.string.availability_remove_title))'),
    ('.setMessage("${slot.day} ${slot.timeRange} meşgul zamanını kaldırmak istiyor musunuz?")', '.setMessage(getString(R.string.availability_remove_message, slot.day, slot.timeRange))'),
    ('.setPositiveButton("Kaldır")', '.setPositiveButton(getString(R.string.common_remove))'),
    ('.setNegativeButton("İptal", null)', '.setNegativeButton(getString(R.string.common_cancel), null)'),
    ('Toast.makeText(requireContext(), "Meşgul zaman eklendi.", Toast.LENGTH_SHORT).show()', 'Toast.makeText(requireContext(), getString(R.string.availability_slot_added), Toast.LENGTH_SHORT).show()'),
    ('.setTitleText("Saat Seç")', '.setTitleText(getString(R.string.availability_time_pick_title))'),
])
print("AvailabilityFragment.kt done")

# LecturerHomeFragment.kt
replace_all(f'{base}/ui/lecturer/LecturerHomeFragment.kt', [
    ('binding.tvWelcome.text      = "Hoş geldiniz, ${d.lecturer.fullName}"', 'binding.tvWelcome.text      = getString(R.string.lecturer_home_welcome, d.lecturer.fullName)'),
])
print("LecturerHomeFragment.kt done")

# AutoScheduleFragment.kt
replace_all(f'{base}/ui/admin/AutoScheduleFragment.kt', [
    ('.setTitle("Programı Onayla")', '.setTitle(getString(R.string.auto_schedule_approve_title))'),
    ('.setMessage("Oluşturulan program kaydedilecek. Devam etmek istiyor musunuz?")', '.setMessage(getString(R.string.auto_schedule_approve_message))'),
    ('.setPositiveButton("Onayla ve Kaydet")', '.setPositiveButton(getString(R.string.auto_schedule_approve_confirm))'),
    ('.setNegativeButton("Vazgeç", null)', '.setNegativeButton(getString(R.string.common_cancel), null)'),
    ('Toast.makeText(requireContext(), "${state.data} ders başarıyla kaydedildi!", Toast.LENGTH_LONG).show()', 'Toast.makeText(requireContext(), getString(R.string.auto_schedule_save_success, state.data), Toast.LENGTH_LONG).show()'),
    ('"Program Hazır" else "Kısmi Program Oluşturuldu"', 'getString(R.string.auto_schedule_result_full) else getString(R.string.auto_schedule_result_partial)'),
    ('binding.tvAssignedCount.text = "Yerleşen: ${result.assigned.size} ders"', 'binding.tvAssignedCount.text = getString(R.string.auto_schedule_assigned_count, result.assigned.size)'),
    ('binding.tvUnassignedCount.text = "Yerleşemeyen: ${result.unassigned.size} ders"', 'binding.tvUnassignedCount.text = getString(R.string.auto_schedule_unassigned_count, result.unassigned.size)'),
    ('binding.tvScore.text = "Başarı: %$pct • Kalite puanı: ${result.score}/100"', 'binding.tvScore.text = getString(R.string.auto_schedule_score_text, pct, result.score)'),
    ('val classInfo = "${entry.offering.classYear}. Sınıf ${entry.offering.section}"', 'val classInfo = getString(R.string.auto_schedule_class_info, entry.offering.classYear, entry.offering.section)'),
    ('items.add("Sabitlemeyi Kaldır")', 'items.add(getString(R.string.auto_schedule_unpin))'),
    ('items.add("Sabitle (Yeniden oluşturmada korunsun)")', 'items.add(getString(R.string.auto_schedule_pin))'),
    ('items.add("Kaldır")', 'items.add(getString(R.string.common_remove))'),
    ('.setNegativeButton("Vazgeç", null)', '.setNegativeButton(getString(R.string.common_cancel), null)'),
])
print("AutoScheduleFragment.kt done")

print("\nAll done!")
