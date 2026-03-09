package com.unischeduler.domain.usecase.export

import android.content.Context
import com.unischeduler.domain.model.Lecturer
import com.unischeduler.util.ExcelExporter
import javax.inject.Inject

class ExportCredentialsUseCase @Inject constructor(
    private val excelExporter: ExcelExporter
) {
    operator fun invoke(
        context: Context,
        lecturers: List<Lecturer>,
        departmentCode: String
    ): Result<String> = runCatching {
        excelExporter.exportCredentials(
            context = context,
            lecturers = lecturers,
            fileName = "credentials_${departmentCode}.xlsx"
        )
    }
}
