package com.unischeduler.domain.usecase.export

import android.content.Context
import com.unischeduler.domain.model.Course
import com.unischeduler.domain.model.Lecturer
import com.unischeduler.domain.model.ScheduleAssignment
import com.unischeduler.domain.model.ScheduleConfig
import com.unischeduler.util.ExcelExporter
import javax.inject.Inject

class ExportScheduleUseCase @Inject constructor(
    private val excelExporter: ExcelExporter
) {
    operator fun invoke(
        context: Context,
        assignments: List<ScheduleAssignment>,
        config: ScheduleConfig,
        courses: List<Course>,
        lecturers: List<Lecturer>,
        departmentCode: String,
        semester: String
    ): Result<String> = runCatching {
        excelExporter.exportSchedule(
            context = context,
            assignments = assignments,
            config = config,
            courses = courses,
            lecturers = lecturers,
            fileName = "schedule_export_${departmentCode}_${semester}.xlsx"
        )
    }
}
