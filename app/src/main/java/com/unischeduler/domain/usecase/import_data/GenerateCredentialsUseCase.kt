package com.unischeduler.domain.usecase.import_data

import com.unischeduler.util.Credentials
import com.unischeduler.util.CredentialGenerator
import javax.inject.Inject

class GenerateCredentialsUseCase @Inject constructor(
    private val credentialGenerator: CredentialGenerator
) {
    operator fun invoke(fullName: String): Credentials {
        return credentialGenerator.generate(fullName)
    }
}
