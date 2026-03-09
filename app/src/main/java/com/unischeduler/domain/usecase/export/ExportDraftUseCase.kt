package com.unischeduler.domain.usecase.export

import android.content.Context
import com.unischeduler.domain.model.ScheduleAssignment
import com.unischeduler.domain.model.ScheduleConfig
import com.unischeduler.domain.model.ScheduleDraft
import com.unischeduler.util.ExcelExporter
import javax.inject.Inject

class ExportDraftUseCase @Inject constructor(
    private val excelExporter: ExcelExporter
) {
    operator fun invoke(
        context: Context,
        draft: ScheduleDraft,
        currentAssignments: List<ScheduleAssignment>,
        config: ScheduleConfig
    ): Result<String> = runCatching {
        excelExporter.exportDraft(
            context = context,
            draft = draft,
            currentAssignments = currentAssignments,
            config = config,
            fileName = "draft_${draft.title.replace(" ", "_")}.xlsx"
        )
    }
}
