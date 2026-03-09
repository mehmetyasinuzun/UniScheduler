package com.unischeduler.domain.usecase.export

import android.content.Context
import com.unischeduler.domain.model.AvailabilitySlot
import com.unischeduler.domain.model.ScheduleConfig
import com.unischeduler.util.ExcelExporter
import javax.inject.Inject

class ExportAvailabilityUseCase @Inject constructor(
    private val excelExporter: ExcelExporter
) {
    operator fun invoke(
        context: Context,
        slots: List<AvailabilitySlot>,
        config: ScheduleConfig,
        lecturerName: String
    ): Result<String> = runCatching {
        excelExporter.exportAvailability(
            context = context,
            slots = slots,
            config = config,
            fileName = "availability_${lecturerName.replace(" ", "_")}.xlsx"
        )
    }
}
