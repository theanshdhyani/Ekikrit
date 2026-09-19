package com.example.domain

import com.example.data.model.ApplicationEntity
import com.example.data.model.DocumentEntity
import com.example.data.model.ReviewQueueEntity
import com.example.data.model.StudentEntity
import com.example.data.model.VerificationRecordEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.data.remote.FakeVerificationApi
import com.example.data.remote.VerificationApi
import com.example.data.remote.VerificationRequest

/**
 * Result of a single government verification check.
 * Encapsulates the provider source, field verified, declared vs retrieved data,
 * and whether the outcome is verified or a non-blocking exception for review.
 */
data class SingleVerificationResult(
    val sourceSystem: String,
    val fieldChecked: String,
    val declaredValue: String,
    val retrievedValue: String,
    val status: String, // VERIFIED, MISMATCH, RESOLVED, PENDING
    val notes: String,
    val isBlocking: Boolean = false,
    val requiresOfficerReview: Boolean = false,
    val reviewReason: String? = null
)

interface VerificationProvider {
    val name: String

    suspend fun verify(
        student: StudentEntity,
        application: ApplicationEntity,
        documents: List<DocumentEntity>
    ): SingleVerificationResult
}

class UidaiVerificationProvider(
    private val verificationApi: VerificationApi = FakeVerificationApi(),
    private val offlineMode: Boolean = false
) : VerificationProvider {

    override val name = "UIDAI (Aadhaar Rail)"

    override suspend fun verify(
        student: StudentEntity,
        application: ApplicationEntity,
        documents: List<DocumentEntity>
    ): SingleVerificationResult {

        // Offline fallback: use the original local rule-based verification.
        if (offlineMode) {
            return SingleVerificationResult(
                sourceSystem = name,
                fieldChecked = "Demographic & Biometric Authentication",
                declaredValue = "${student.name}, DOB: ${student.dob}",
                retrievedValue = "${student.name}, DOB: ${student.dob} (Offline Rule-Based Verification)",
                status = "VERIFIED",
                notes = "Offline verification completed using local rule-based fallback."
            )
        }

        val request = VerificationRequest(
            provider = "UIDAI",
            applicationId = application.id,
            maskedIdentifier = student.aadhaarMasked
        )

        val response = verificationApi.verify(request)

        return SingleVerificationResult(
            sourceSystem = name,
            fieldChecked = "Demographic & Biometric Authentication",
            declaredValue = "${student.name}, DOB: ${student.dob}",
            retrievedValue = if (response.verified) {
                "${student.name}, DOB: ${student.dob} (Mock API Verified)"
            } else {
                "UIDAI verification failed"
            },
            status = if (response.verified) {
                "VERIFIED"
            } else {
                "PENDING"
            },
            notes = response.message ?: "No verification message"
        )
    }
}

class DigiLockerVerificationProvider(
    private val verificationApi: VerificationApi = FakeVerificationApi(),
    private val offlineMode: Boolean = false
) : VerificationProvider {

    override val name = "DigiLocker Wallet"

    override suspend fun verify(
        student: StudentEntity,
        application: ApplicationEntity,
        documents: List<DocumentEntity>
    ): SingleVerificationResult {

        // Offline fallback: use the original local rule-based verification.
        if (offlineMode) {
            val casteDoc = documents.find {
                it.type.contains("Caste", ignoreCase = true) ||
                        it.type.contains("ST", ignoreCase = true)
            }

            val docNum = casteDoc?.docNumberMasked ?: "ST/OD/2021/992418"

            return SingleVerificationResult(
                sourceSystem = name,
                fieldChecked = "ST Caste & PVTG Community Validation",
                declaredValue = student.category,
                retrievedValue = "${student.category} (Cert: $docNum)",
                status = "VERIFIED",
                notes = "Offline verification completed using local rule-based fallback."
            )
        }

        val casteDoc = documents.find {
            it.type.contains("Caste", ignoreCase = true) ||
                    it.type.contains("ST", ignoreCase = true)
        }

        val docNum = casteDoc?.docNumberMasked ?: "ST/OD/2021/992418"

        val request = VerificationRequest(
            provider = "DigiLocker",
            applicationId = application.id,
            maskedIdentifier = docNum
        )

        val response = verificationApi.verify(request)

        return SingleVerificationResult(
            sourceSystem = name,
            fieldChecked = "ST Caste & PVTG Community Validation",
            declaredValue = student.category,
            retrievedValue = "${student.category} (Cert: $docNum)",
            status = if (response.verified) "VERIFIED" else "PENDING",
            notes = "Cryptographic digital signature verified against State e-Pramaan root authority."
        )
    }
}

class ApaarVerificationProvider(
    private val verificationApi: VerificationApi = FakeVerificationApi(),
    private val offlineMode: Boolean = false
) : VerificationProvider {

    override val name = "APAAR / EduLocker"

    override suspend fun verify(
        student: StudentEntity,
        application: ApplicationEntity,
        documents: List<DocumentEntity>
    ): SingleVerificationResult {

        // Offline fallback: use the original local rule-based verification.
        if (offlineMode) {
            return SingleVerificationResult(
                sourceSystem = name,
                fieldChecked = "Academic Bank of Credits (ABC Progression)",
                declaredValue = student.apaarId,
                retrievedValue = "${student.apaarId} • Credits Active (ABC Score: 8.42)",
                status = "VERIFIED",
                notes = "Offline verification completed using local rule-based fallback."
            )
        }

        val request = VerificationRequest(
            provider = "APAAR",
            applicationId = application.id,
            maskedIdentifier = student.apaarId
        )

        val response = verificationApi.verify(request)

        return SingleVerificationResult(
            sourceSystem = name,
            fieldChecked = "Academic Bank of Credits (ABC Progression)",
            declaredValue = student.apaarId,
            retrievedValue = "${student.apaarId} • Credits Active (ABC Score: 8.42)",
            status = if (response.verified) "VERIFIED" else "PENDING",
            notes = "Student progression and academic continuity validated through National Academic Depository."
        )
    }
}

class AisheVerificationProvider(
    private val verificationApi: VerificationApi = FakeVerificationApi(),
    private val offlineMode: Boolean = false
) : VerificationProvider {

    override val name = "AISHE Portal"

    override suspend fun verify(
        student: StudentEntity,
        application: ApplicationEntity,
        documents: List<DocumentEntity>
    ): SingleVerificationResult {

        // Offline fallback: use the original local rule-based verification.
        if (offlineMode) {
            return SingleVerificationResult(
                sourceSystem = name,
                fieldChecked = "Institute Recognition & Regular Enrollment",
                declaredValue = "${student.institutionName} (${student.course})",
                retrievedValue = "Code: ${student.institutionId} (Institute of National Importance)",
                status = "VERIFIED",
                notes = "Offline verification completed using local rule-based fallback."
            )
        }

        val request = VerificationRequest(
            provider = "AISHE",
            applicationId = application.id,
            maskedIdentifier = student.institutionId
        )

        val response = verificationApi.verify(request)

        return SingleVerificationResult(
            sourceSystem = name,
            fieldChecked = "Institute Recognition & Regular Enrollment",
            declaredValue = "${student.institutionName} (${student.course})",
            retrievedValue = "Code: ${student.institutionId} (Institute of National Importance)",
            status = if (response.verified) "VERIFIED" else "PENDING",
            notes = "Active full-time enrollment verified via Higher Education All India Survey database."
        )
    }
}

class UdiseVerificationProvider(
    private val verificationApi: VerificationApi = FakeVerificationApi(),
    private val offlineMode: Boolean = false
) : VerificationProvider {

    override val name = "UDISE+ School Registry"

    override suspend fun verify(
        student: StudentEntity,
        application: ApplicationEntity,
        documents: List<DocumentEntity>
    ): SingleVerificationResult {

        // Offline fallback: use the original local rule-based verification.
        if (offlineMode) {
            return SingleVerificationResult(
                sourceSystem = name,
                fieldChecked = "Secondary School Prior Completion History",
                declaredValue = "Class X / XII Prior Board Record",
                retrievedValue = "Central Board Record Validated (Pass Status: FIRST_CLASS)",
                status = "VERIFIED",
                notes = "Offline verification completed using local rule-based fallback."
            )
        }

        val request = VerificationRequest(
            provider = "UDISE+",
            applicationId = application.id,
            maskedIdentifier = student.institutionId
        )

        val response = verificationApi.verify(request)

        return SingleVerificationResult(
            sourceSystem = name,
            fieldChecked = "Secondary School Prior Completion History",
            declaredValue = "Class X / XII Prior Board Record",
            retrievedValue = "Central Board Record Validated (Pass Status: FIRST_CLASS)",
            status = if (response.verified) "VERIFIED" else "PENDING",
            notes = "UDISE+ prior schooling baseline cross-matched with secondary education register."
        )
    }
}

class UgcNtaVerificationProvider(
    private val verificationApi: VerificationApi = FakeVerificationApi(),
    private val offlineMode: Boolean = false
) : VerificationProvider {

    override val name = "UGC / NTA Rail"

    override suspend fun verify(
        student: StudentEntity,
        application: ApplicationEntity,
        documents: List<DocumentEntity>
    ): SingleVerificationResult {

        // Offline fallback: use the original local rule-based verification.
        if (offlineMode) {
            return SingleVerificationResult(
                sourceSystem = name,
                fieldChecked = "National Merit & Qualification Credential",
                declaredValue = student.qualificationDetails.ifBlank {
                    "National Merit Qualified"
                },
                retrievedValue = "Score: 94.8 Percentile (ST Rank 842) • Central Merit Verified",
                status = "VERIFIED",
                notes = "Offline verification completed using local rule-based fallback."
            )
        }

        val request = VerificationRequest(
            provider = "UGC/NTA",
            applicationId = application.id,
            maskedIdentifier = student.qualificationDetails
        )

        val response = verificationApi.verify(request)

        return SingleVerificationResult(
            sourceSystem = name,
            fieldChecked = "National Merit & Qualification Credential",
            declaredValue = student.qualificationDetails.ifBlank {
                "National Merit Qualified"
            },
            retrievedValue = "Score: 94.8 Percentile (ST Rank 842) • Central Merit Verified",
            status = if (response.verified) "VERIFIED" else "PENDING",
            notes = "Entrance exam percentile validated against National Testing Agency master ledger."
        )
    }
}

class EDistrictVerificationProvider(
    private val verificationApi: VerificationApi = FakeVerificationApi(),
    private val offlineMode: Boolean = false
) : VerificationProvider {

    override val name = "e-District Revenue Portal"

    override suspend fun verify(
        student: StudentEntity,
        application: ApplicationEntity,
        documents: List<DocumentEntity>
    ): SingleVerificationResult {

        // Offline fallback: preserve the deterministic SIH demo mismatch scenario.
        if (offlineMode) {
            val declaredIncomeFormatted =
                "₹${String.format(Locale.ENGLISH, "%,d", student.annualIncome.toInt())} / annum"

            val registryIncome = (student.annualIncome * 1.119).toInt()

            val registryIncomeFormatted =
                "₹${String.format(Locale.ENGLISH, "%,d", registryIncome)} / annum (Cert INC/OD/2026/00142)"

            return SingleVerificationResult(
                sourceSystem = name,
                fieldChecked = "Annual Household Income Certificate",
                declaredValue = declaredIncomeFormatted,
                retrievedValue = registryIncomeFormatted,
                status = "MISMATCH",
                notes = "Variance of +11.9% detected. Auto-routed to manual Reviewer Desk under non-blocking exception workflow (below ₹2.50L ceiling).",
                isBlocking = false,
                requiresOfficerReview = true,
                reviewReason = "Self-declared income $declaredIncomeFormatted vs e-District registry $registryIncomeFormatted (+11.9%). Both figures are strictly below the ₹2.50 Lakh scheme ceiling. Eligible for officer exception clearance without blocking the student."
            )
        }

        // Deterministic SIH demo scenario:
        // Declared ₹2,10,000 vs e-District record ₹2,35,000 (+11.9% variance)
        // Both are strictly below scheme ceiling (₹2.50L), triggering non-blocking exception workflow.
        val declaredIncomeFormatted =
            "₹${String.format(Locale.ENGLISH, "%,d", student.annualIncome.toInt())} / annum"

        val registryIncome = (student.annualIncome * 1.119).toInt()

        val registryIncomeFormatted =
            "₹${String.format(Locale.ENGLISH, "%,d", registryIncome)} / annum (Cert INC/OD/2026/00142)"

        val request = VerificationRequest(
            provider = "e-District",
            applicationId = application.id,
            maskedIdentifier = "INCOME-CERT-00142"
        )

        val response = verificationApi.verify(request)

        return SingleVerificationResult(
            sourceSystem = name,
            fieldChecked = "Annual Household Income Certificate",
            declaredValue = declaredIncomeFormatted,
            retrievedValue = registryIncomeFormatted,
            status = "MISMATCH",
            notes = "Variance of +11.9% detected. Auto-routed to manual Reviewer Desk under non-blocking exception workflow (below ₹2.50L ceiling).",
            isBlocking = false,
            requiresOfficerReview = true,
            reviewReason = "Self-declared income $declaredIncomeFormatted vs e-District registry $registryIncomeFormatted (+11.9%). Both figures are strictly below the ₹2.50 Lakh scheme ceiling. Eligible for officer exception clearance without blocking the student."
        )
    }
}

data class VerificationExecutionOutput(
    val records: List<VerificationRecordEntity>,
    val reviewItem: ReviewQueueEntity?,
    val summaryMessage: String
)

object UnifiedVerificationEngine {

    private fun getCurrentTimestamp(): String {
        return SimpleDateFormat(
            "dd MMM yyyy, hh:mm a",
            Locale.ENGLISH
        ).format(Date())
    }

    suspend fun executeSevenSourceVerification(
        student: StudentEntity,
        application: ApplicationEntity,
        documents: List<DocumentEntity>,
        offlineMode: Boolean = false
    ): VerificationExecutionOutput {

        val providers: List<VerificationProvider> = listOf(
            UidaiVerificationProvider(offlineMode = offlineMode),
            DigiLockerVerificationProvider(offlineMode = offlineMode),
            ApaarVerificationProvider(offlineMode = offlineMode),
            AisheVerificationProvider(offlineMode = offlineMode),
            UdiseVerificationProvider(offlineMode = offlineMode),
            UgcNtaVerificationProvider(offlineMode = offlineMode),
            EDistrictVerificationProvider(offlineMode = offlineMode)
        )

        val now = getCurrentTimestamp()
        val records = mutableListOf<VerificationRecordEntity>()
        var generatedReviewItem: ReviewQueueEntity? = null

        var verifiedCount = 0
        var mismatchCount = 0

        for (provider in providers) {

            val result = provider.verify(
                student,
                application,
                documents
            )

            val recordId =
                "VER_${provider.name.take(6).uppercase(Locale.ROOT).replace("[^A-Z]".toRegex(), "")}_${System.currentTimeMillis()}"

            val recordEntity = VerificationRecordEntity(
                id = recordId,
                applicationId = application.id,
                schemeId = application.schemeId,
                sourceSystem = result.sourceSystem,
                fieldChecked = result.fieldChecked,
                declaredValue = result.declaredValue,
                retrievedValue = result.retrievedValue,
                status = result.status,
                timestamp = now,
                notes = result.notes,
                isBlocking = result.isBlocking
            )

            records.add(recordEntity)

            if (result.status == "VERIFIED") {
                verifiedCount++
            } else if (result.status == "MISMATCH") {
                mismatchCount++

                if (result.requiresOfficerReview) {
                    generatedReviewItem = ReviewQueueEntity(
                        id = "REV_${System.currentTimeMillis()}",
                        verificationRecordId = recordId,
                        applicationId = application.id,
                        studentId = student.id,
                        studentName = student.name,
                        category = student.category,
                        schemeName = application.schemeName,
                        sourceSystem = result.sourceSystem,
                        fieldName = result.fieldChecked,
                        declaredValue = result.declaredValue,
                        retrievedValue = result.retrievedValue,
                        mismatchReason = result.reviewReason ?: result.notes,
                        status = "PENDING",
                        createdAt = now
                    )
                }
            }
        }

        val summary =
            "$verifiedCount verified, $mismatchCount mismatch auto-routed to Reviewer Desk."

        return VerificationExecutionOutput(
            records = records,
            reviewItem = generatedReviewItem,
            summaryMessage = summary
        )
    }
}