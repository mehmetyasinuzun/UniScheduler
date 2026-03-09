package com.unischeduler.domain.algorithm

import com.unischeduler.domain.model.AvailabilitySlot
import com.unischeduler.domain.model.Course
import com.unischeduler.domain.model.Lecturer
import com.unischeduler.domain.model.ScheduleAssignment
import com.unischeduler.domain.model.ScheduleConfig
import com.unischeduler.domain.model.ScheduleSolution
import javax.inject.Inject

/**
 * CSP Solver: Backtracking + Forward Checking + MRV Heuristic.
 * Generates up to [alternativeCount] schedule solutions, ranked by soft score.
 */
class CSPSolver @Inject constructor() {

    private val constraintChecker = ConstraintChecker()
    private val softScorer = SoftScorer()
    private val mrvHeuristic = MRVHeuristic()

    fun solve(
        courses: List<Course>,
        lecturers: List<Lecturer>,
        courseLecturerMap: Map<Int, Int>,
        availability: Map<Int, List<AvailabilitySlot>>,
        config: ScheduleConfig,
        lockedAssignments: List<ScheduleAssignment>,
        existingCrossDeptAssignments: List<ScheduleAssignment> = emptyList(),
        alternativeCount: Int = 3
    ): List<ScheduleSolution> {
        val solutions = mutableListOf<ScheduleSolution>()

        // Filter out courses that are already locked
        val lockedCourseIds = lockedAssignments.map { it.courseId }.toSet()
        val coursesToSchedule = courses.filter { it.id !in lockedCourseIds }

        // Create variables with MRV heuristic
        val variables = mrvHeuristic.createVariables(
            coursesToSchedule, courseLecturerMap, config, availability
        )

        // Try different orderings to get diverse solutions
        val seeds = (0 until alternativeCount * 2).toList()

        for (seed in seeds) {
            if (solutions.size >= alternativeCount) break

            val shuffledVars = if (seed == 0) {
                mrvHeuristic.orderByMRV(variables)
            } else {
                variables.shuffled(kotlin.random.Random(seed.toLong()))
                    .let { mrvHeuristic.orderByMRV(it) }
            }

            val result = backtrack(
                variables = shuffledVars,
                index = 0,
                currentAssignments = lockedAssignments.toMutableList(),
                availability = availability,
                crossDeptAssignments = existingCrossDeptAssignments
            )

            if (result != null) {
                val allAssignments = lockedAssignments + result
                val score = softScorer.score(allAssignments, availability)

                // Avoid duplicate solutions
                val isDuplicate = solutions.any { existing ->
                    existing.assignments.map { Triple(it.courseId, it.dayOfWeek, it.slotIndex) }.toSet() ==
                        allAssignments.map { Triple(it.courseId, it.dayOfWeek, it.slotIndex) }.toSet()
                }

                if (!isDuplicate) {
                    solutions.add(
                        ScheduleSolution(
                            assignments = allAssignments,
                            softScore = score,
                            totalAssigned = allAssignments.size,
                            totalUnassigned = coursesToSchedule.size - result.size,
                            conflictCount = 0
                        )
                    )
                }
            }
        }

        return solutions.sortedByDescending { it.softScore }.take(alternativeCount)
    }

    private fun backtrack(
        variables: List<MRVHeuristic.Variable>,
        index: Int,
        currentAssignments: MutableList<ScheduleAssignment>,
        availability: Map<Int, List<AvailabilitySlot>>,
        crossDeptAssignments: List<ScheduleAssignment>
    ): List<ScheduleAssignment>? {
        if (index >= variables.size) {
            // All variables assigned — solution found
            return currentAssignments.filter { a -> variables.any { it.course.id == a.courseId } }
        }

        val variable = variables[index]

        // Forward checking: filter domain based on current state
        val validSlots = variable.domain.filter { slot ->
            val candidateAssignment = ScheduleAssignment(
                courseId = variable.course.id,
                courseCode = variable.course.code,
                courseName = variable.course.name,
                lecturerId = variable.lecturerId,
                dayOfWeek = slot.dayOfWeek,
                slotIndex = slot.slotIndex
            )

            constraintChecker.isFeasible(candidateAssignment, currentAssignments, availability) &&
                constraintChecker.checkCrossDepartmentConflict(candidateAssignment, crossDeptAssignments)
        }

        // Try each valid slot
        for (slot in validSlots) {
            val assignment = ScheduleAssignment(
                courseId = variable.course.id,
                courseCode = variable.course.code,
                courseName = variable.course.name,
                lecturerId = variable.lecturerId,
                dayOfWeek = slot.dayOfWeek,
                slotIndex = slot.slotIndex
            )

            currentAssignments.add(assignment)

            val result = backtrack(variables, index + 1, currentAssignments, availability, crossDeptAssignments)
            if (result != null) return result

            currentAssignments.removeAt(currentAssignments.lastIndex)
        }

        return null // No valid assignment found — backtrack
    }
}
