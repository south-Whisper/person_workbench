package com.example.demo.service;

import java.util.LinkedHashMap;

final class StructuredColumns {
    private StructuredColumns() {}
    static final LinkedHashMap<String,String> POSITION=new LinkedHashMap<>();
    static final LinkedHashMap<String,String> RECORD=new LinkedHashMap<>();
    static final LinkedHashMap<String,String> ONBOARDING=new LinkedHashMap<>();
    static final LinkedHashMap<String,String> OFFER_RESPONSE=new LinkedHashMap<>();
    static {
        put(POSITION,"name","name VARCHAR(120)");put(POSITION,"minSalary","min_salary DECIMAL(12,2)");put(POSITION,"maxSalary","max_salary DECIMAL(12,2)");
        put(POSITION,"companyId","company_id BIGINT");put(POSITION,"company","company VARCHAR(200)");put(POSITION,"recruitmentCode","recruitment_code VARCHAR(40)");put(POSITION,"department","department VARCHAR(160)");put(POSITION,"location","location VARCHAR(160)");put(POSITION,"baseLocation","base_location VARCHAR(160)");put(POSITION,"description","description LONGTEXT");
        put(POSITION,"status","status VARCHAR(60)");put(POSITION,"versionNote","version_note LONGTEXT");put(POSITION,"headcount","headcount INT");
        put(POSITION,"employmentType","employment_type VARCHAR(80)");put(POSITION,"requirements","requirements LONGTEXT");put(POSITION,"createdAt","created_at VARCHAR(40)");put(POSITION,"updatedAt","updated_at VARCHAR(40)");

        put(RECORD,"type","event_type VARCHAR(80)");put(RECORD,"title","title VARCHAR(500)");put(RECORD,"summary","summary LONGTEXT");put(RECORD,"status","status VARCHAR(80)");
        put(RECORD,"result","result VARCHAR(160)");put(RECORD,"reason","reason LONGTEXT");put(RECORD,"remark","remark LONGTEXT");put(RECORD,"feedback","feedback LONGTEXT");put(RECORD,"description","description LONGTEXT");
        put(RECORD,"jobId","job_id BIGINT");put(RECORD,"jobName","job_name VARCHAR(240)");put(RECORD,"jobVersion","job_version BIGINT");put(RECORD,"company","company VARCHAR(200)");put(RECORD,"jobCode","job_code VARCHAR(40)");put(RECORD,"baseLocation","base_location VARCHAR(160)");put(RECORD,"applicationId","application_id BIGINT");put(RECORD,"opportunityId","opportunity_id BIGINT");
        put(RECORD,"actor","actor VARCHAR(120)");put(RECORD,"channel","channel VARCHAR(80)");put(RECORD,"companyIntent","company_intent VARCHAR(40)");put(RECORD,"talentIntent","talent_intent VARCHAR(40)");
        put(RECORD,"nextStep","next_step VARCHAR(600)");put(RECORD,"nextContactAt","next_contact_at VARCHAR(40)");put(RECORD,"startedAt","started_at VARCHAR(40)");put(RECORD,"occurredAt","occurred_at VARCHAR(40)");
        put(RECORD,"scheduledAt","scheduled_at VARCHAR(40)");put(RECORD,"expectedStartDate","expected_start_date VARCHAR(40)");put(RECORD,"expiresAt","expires_at VARCHAR(40)");put(RECORD,"startDate","start_date VARCHAR(40)");
        put(RECORD,"endDate","end_date VARCHAR(40)");put(RECORD,"dueAt","due_at VARCHAR(40)");put(RECORD,"completedAt","completed_at VARCHAR(40)");put(RECORD,"createdAt","created_at VARCHAR(40)");put(RECORD,"updatedAt","updated_at VARCHAR(40)");
        put(RECORD,"round","interview_round VARCHAR(40)");put(RECORD,"method","method VARCHAR(80)");put(RECORD,"score","score DECIMAL(8,2)");
        put(RECORD,"salaryMin","salary_min DECIMAL(12,2)");put(RECORD,"salaryMax","salary_max DECIMAL(12,2)");put(RECORD,"salaryMode","salary_mode VARCHAR(20)");put(RECORD,"actualSalary","actual_salary DECIMAL(12,2)");put(RECORD,"annualSalary","annual_salary DECIMAL(12,2)");put(RECORD,"salaryMonths","salary_months INT");
        put(RECORD,"validityDays","validity_days INT");put(RECORD,"probationMonths","probation_months INT");put(RECORD,"socialInsurance","social_insurance VARCHAR(80)");put(RECORD,"recipientEmail","recipient_email VARCHAR(240)");put(RECORD,"currentRecord","current_record BOOLEAN");put(RECORD,"sentAt","sent_at VARCHAR(40)");put(RECORD,"currency","currency VARCHAR(20)");put(RECORD,"period","period VARCHAR(30)");put(RECORD,"source","source VARCHAR(240)");
        put(RECORD,"organization","organization VARCHAR(300)");put(RECORD,"projectName","project_name VARCHAR(300)");put(RECORD,"name","record_name VARCHAR(300)");put(RECORD,"role","role_name VARCHAR(160)");put(RECORD,"amount","amount DECIMAL(16,2)");
        put(RECORD,"external","external BOOLEAN");put(RECORD,"url","url VARCHAR(1000)");put(RECORD,"downloadUrl","download_url VARCHAR(1000)");put(RECORD,"storageName","storage_name VARCHAR(300)");put(RECORD,"size","file_size BIGINT");
        put(RECORD,"importBatch","import_batch VARCHAR(80)");put(RECORD,"importRow","import_row BIGINT");
        put(RECORD,"mimeType","mime_type VARCHAR(160)");put(RECORD,"sha256","sha256 VARCHAR(80)");put(RECORD,"scanStatus","scan_status VARCHAR(80)");put(RECORD,"createdBy","created_by VARCHAR(120)");put(RECORD,"version","business_version BIGINT");
        put(RECORD,"employmentCategory","employment_category VARCHAR(40)");put(RECORD,"hireChannel","hire_channel VARCHAR(40)");put(RECORD,"preEmploymentStatus","pre_employment_status VARCHAR(40)");
        put(RECORD,"employeeName","employee_name VARCHAR(120)");put(RECORD,"employeePhone","employee_phone VARCHAR(80)");put(RECORD,"employeeEmail","employee_email VARCHAR(240)");put(RECORD,"identityNumber","identity_number VARCHAR(40)");
        put(RECORD,"bankName","bank_name VARCHAR(160)");put(RECORD,"bankAccount","bank_account VARCHAR(80)");put(RECORD,"address","residential_address VARCHAR(500)");put(RECORD,"householdRegistration","household_registration VARCHAR(500)");put(RECORD,"emergencyContact","emergency_contact VARCHAR(120)");put(RECORD,"emergencyPhone","emergency_phone VARCHAR(80)");put(RECORD,"onboardedAt","onboarded_at VARCHAR(40)");put(RECORD,"familyRelations","family_relations LONGTEXT");
        put(RECORD,"identityFrontAssetId","identity_front_asset_id BIGINT");put(RECORD,"identityFrontFileName","identity_front_file_name VARCHAR(300)");put(RECORD,"identityBackAssetId","identity_back_asset_id BIGINT");put(RECORD,"identityBackFileName","identity_back_file_name VARCHAR(300)");
        put(RECORD,"graduationCertificateAssetId","graduation_certificate_asset_id BIGINT");put(RECORD,"graduationCertificateFileName","graduation_certificate_file_name VARCHAR(300)");put(RECORD,"degreeCertificateAssetId","degree_certificate_asset_id BIGINT");put(RECORD,"degreeCertificateFileName","degree_certificate_file_name VARCHAR(300)");
        put(RECORD,"educationVerificationAssetId","education_verification_asset_id BIGINT");put(RECORD,"educationVerificationFileName","education_verification_file_name VARCHAR(300)");put(RECORD,"departureCertificateAssetId","departure_certificate_asset_id BIGINT");put(RECORD,"departureCertificateFileName","departure_certificate_file_name VARCHAR(300)");

        put(ONBOARDING,"employmentCategory","employment_category VARCHAR(40)");put(ONBOARDING,"hireChannel","hire_channel VARCHAR(40)");put(ONBOARDING,"preEmploymentStatus","pre_employment_status VARCHAR(40)");
        put(ONBOARDING,"employeeName","employee_name VARCHAR(120)");put(ONBOARDING,"employeePhone","employee_phone VARCHAR(80)");put(ONBOARDING,"employeeEmail","employee_email VARCHAR(240)");put(ONBOARDING,"identityNumber","identity_number VARCHAR(40)");
        put(ONBOARDING,"startDate","start_date VARCHAR(40)");put(ONBOARDING,"bankName","bank_name VARCHAR(160)");put(ONBOARDING,"bankAccount","bank_account VARCHAR(80)");put(ONBOARDING,"address","residential_address VARCHAR(500)");put(ONBOARDING,"householdRegistration","household_registration VARCHAR(500)");
        put(ONBOARDING,"emergencyContact","emergency_contact VARCHAR(120)");put(ONBOARDING,"emergencyPhone","emergency_phone VARCHAR(80)");put(ONBOARDING,"onboardedAt","onboarded_at VARCHAR(40)");
        put(ONBOARDING,"identityFrontAssetId","identity_front_asset_id BIGINT");put(ONBOARDING,"identityFrontFileName","identity_front_file_name VARCHAR(300)");put(ONBOARDING,"identityBackAssetId","identity_back_asset_id BIGINT");put(ONBOARDING,"identityBackFileName","identity_back_file_name VARCHAR(300)");
        put(ONBOARDING,"graduationCertificateAssetId","graduation_certificate_asset_id BIGINT");put(ONBOARDING,"graduationCertificateFileName","graduation_certificate_file_name VARCHAR(300)");put(ONBOARDING,"degreeCertificateAssetId","degree_certificate_asset_id BIGINT");put(ONBOARDING,"degreeCertificateFileName","degree_certificate_file_name VARCHAR(300)");
        put(ONBOARDING,"educationVerificationAssetId","education_verification_asset_id BIGINT");put(ONBOARDING,"educationVerificationFileName","education_verification_file_name VARCHAR(300)");put(ONBOARDING,"departureCertificateAssetId","departure_certificate_asset_id BIGINT");put(ONBOARDING,"departureCertificateFileName","departure_certificate_file_name VARCHAR(300)");

        put(OFFER_RESPONSE,"responseTokenHash","response_token_hash CHAR(64)");put(OFFER_RESPONSE,"responseStatus","response_status VARCHAR(40)");
        put(OFFER_RESPONSE,"respondedAt","responded_at VARCHAR(40)");put(OFFER_RESPONSE,"responseReason","response_reason LONGTEXT");
    }
    private static void put(LinkedHashMap<String,String> map,String key,String definition){map.put(key,definition);}
    static String column(String definition){return definition.substring(0,definition.indexOf(' '));}
}
