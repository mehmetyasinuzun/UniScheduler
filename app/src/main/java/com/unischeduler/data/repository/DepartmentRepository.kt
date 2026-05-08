// Department repository — read-only list (Admin sets departments via Supabase console)
package com.unischeduler.data.repository

import com.unischeduler.data.model.Department
import com.unischeduler.data.model.DepartmentInsert
import com.unischeduler.data.remote.SupabaseClient.client
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order

class DepartmentRepository {

    suspend fun getAllDepartments(orgId: Int): List<Department> =
        client.postgrest["departments"]
            .select {
                filter { eq("org_id", orgId) }
                order(column = "name", order = Order.ASCENDING)
                limit(1000)
            }
            .decodeList<Department>()

    suspend fun insertDepartment(name: String, orgId: Int) {
        client.postgrest["departments"].insert(
            DepartmentInsert(orgId = orgId, name = name)
        )
    }

    suspend fun updateDepartment(id: Int, name: String, orgId: Int) {
        client.postgrest["departments"]
            .update({ set("name", name) }) {
                filter { eq("id", id); eq("org_id", orgId) }
            }
    }

    suspend fun deleteDepartment(id: Int, orgId: Int) {
        client.postgrest["departments"]
            .delete { filter { eq("id", id); eq("org_id", orgId) } }
    }
}
