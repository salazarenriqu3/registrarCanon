package com.iuims.registrar.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "program_fee_settings")
public class ProgramFeeSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fee_setting_id")
    private Integer feeSettingId;

    @Column(name = "program_id", nullable = false)
    private Integer programId;

    @Column(name = "term_id")
    private Integer termId;

    @Column(name = "year_level")
    private Integer yearLevel;

    @Column(name = "semester_number")
    private Integer semesterNumber;

    // Core fees
    @Column(name = "fee_tuition_per_unit", precision = 10, scale = 2)
    private BigDecimal feeTuitionPerUnit = BigDecimal.ZERO;

    @Column(name = "fee_lec_per_unit", precision = 10, scale = 2)
    private BigDecimal feeLecPerUnit = BigDecimal.ZERO;

    @Column(name = "fee_lab_per_unit", precision = 10, scale = 2)
    private BigDecimal feeLabPerUnit = BigDecimal.ZERO;

    @Column(name = "fee_comp_per_unit", precision = 10, scale = 2)
    private BigDecimal feeCompPerUnit = BigDecimal.ZERO;

    @Column(name = "fee_rle_per_unit", precision = 10, scale = 2)
    private BigDecimal feeRlePerUnit = BigDecimal.ZERO;

    // Misc Fees
    @Column(name = "fee_misc_registration", precision = 10, scale = 2)
    private BigDecimal feeMiscRegistration = BigDecimal.ZERO;

    @Column(name = "fee_misc_library", precision = 10, scale = 2)
    private BigDecimal feeMiscLibrary = BigDecimal.ZERO;

    @Column(name = "fee_misc_medical", precision = 10, scale = 2)
    private BigDecimal feeMiscMedical = BigDecimal.ZERO;

    @Column(name = "fee_misc_id", precision = 10, scale = 2)
    private BigDecimal feeMiscId = BigDecimal.ZERO;

    @Column(name = "fee_misc_athletic", precision = 10, scale = 2)
    private BigDecimal feeMiscAthletic = BigDecimal.ZERO;

    @Column(name = "fee_misc_guidance", precision = 10, scale = 2)
    private BigDecimal feeMiscGuidance = BigDecimal.ZERO;

    @Column(name = "fee_misc_lms", precision = 10, scale = 2)
    private BigDecimal feeMiscLms = BigDecimal.ZERO;

    @Column(name = "fee_misc_insurance", precision = 10, scale = 2)
    private BigDecimal feeMiscInsurance = BigDecimal.ZERO;

    @Column(name = "fee_misc_cultural", precision = 10, scale = 2)
    private BigDecimal feeMiscCultural = BigDecimal.ZERO;

    @Column(name = "fee_misc_av", precision = 10, scale = 2)
    private BigDecimal feeMiscAv = BigDecimal.ZERO;

    @Column(name = "fee_misc_energy", precision = 10, scale = 2)
    private BigDecimal feeMiscEnergy = BigDecimal.ZERO;

    // Other Fees
    @Column(name = "fee_other_late_enrollment", precision = 10, scale = 2)
    private BigDecimal feeOtherLateEnrollment = BigDecimal.ZERO;

    @Column(name = "fee_other_add_drop", precision = 10, scale = 2)
    private BigDecimal feeOtherAddDrop = BigDecimal.ZERO;

    @Column(name = "fee_other_installment", precision = 10, scale = 2)
    private BigDecimal feeOtherInstallment = BigDecimal.ZERO;

    @Column(name = "fee_other_id", precision = 10, scale = 2)
    private BigDecimal feeOtherId = BigDecimal.ZERO;

    @Column(name = "fee_other_insurance", precision = 10, scale = 2)
    private BigDecimal feeOtherInsurance = BigDecimal.ZERO;

    @Column(name = "fee_other_comp", precision = 10, scale = 2)
    private BigDecimal feeOtherComp = BigDecimal.ZERO;

    @Column(name = "fee_other_dev", precision = 10, scale = 2)
    private BigDecimal feeOtherDev = BigDecimal.ZERO;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    // Getters and Setters
    public Integer getFeeSettingId() { return feeSettingId; }
    public void setFeeSettingId(Integer feeSettingId) { this.feeSettingId = feeSettingId; }

    public Integer getProgramId() { return programId; }
    public void setProgramId(Integer programId) { this.programId = programId; }

    public Integer getTermId() { return termId; }
    public void setTermId(Integer termId) { this.termId = termId; }

    public Integer getYearLevel() { return yearLevel; }
    public void setYearLevel(Integer yearLevel) { this.yearLevel = yearLevel; }

    public Integer getSemesterNumber() { return semesterNumber; }
    public void setSemesterNumber(Integer semesterNumber) { this.semesterNumber = semesterNumber; }

    public BigDecimal getFeeTuitionPerUnit() { return feeTuitionPerUnit; }
    public void setFeeTuitionPerUnit(BigDecimal feeTuitionPerUnit) { this.feeTuitionPerUnit = feeTuitionPerUnit; }

    public BigDecimal getFeeLecPerUnit() { return feeLecPerUnit; }
    public void setFeeLecPerUnit(BigDecimal feeLecPerUnit) { this.feeLecPerUnit = feeLecPerUnit; }

    public BigDecimal getFeeLabPerUnit() { return feeLabPerUnit; }
    public void setFeeLabPerUnit(BigDecimal feeLabPerUnit) { this.feeLabPerUnit = feeLabPerUnit; }

    public BigDecimal getFeeCompPerUnit() { return feeCompPerUnit; }
    public void setFeeCompPerUnit(BigDecimal feeCompPerUnit) { this.feeCompPerUnit = feeCompPerUnit; }

    public BigDecimal getFeeRlePerUnit() { return feeRlePerUnit; }
    public void setFeeRlePerUnit(BigDecimal feeRlePerUnit) { this.feeRlePerUnit = feeRlePerUnit; }

    public BigDecimal getFeeMiscRegistration() { return feeMiscRegistration; }
    public void setFeeMiscRegistration(BigDecimal feeMiscRegistration) { this.feeMiscRegistration = feeMiscRegistration; }

    public BigDecimal getFeeMiscLibrary() { return feeMiscLibrary; }
    public void setFeeMiscLibrary(BigDecimal feeMiscLibrary) { this.feeMiscLibrary = feeMiscLibrary; }

    public BigDecimal getFeeMiscMedical() { return feeMiscMedical; }
    public void setFeeMiscMedical(BigDecimal feeMiscMedical) { this.feeMiscMedical = feeMiscMedical; }

    public BigDecimal getFeeMiscId() { return feeMiscId; }
    public void setFeeMiscId(BigDecimal feeMiscId) { this.feeMiscId = feeMiscId; }

    public BigDecimal getFeeMiscAthletic() { return feeMiscAthletic; }
    public void setFeeMiscAthletic(BigDecimal feeMiscAthletic) { this.feeMiscAthletic = feeMiscAthletic; }

    public BigDecimal getFeeMiscGuidance() { return feeMiscGuidance; }
    public void setFeeMiscGuidance(BigDecimal feeMiscGuidance) { this.feeMiscGuidance = feeMiscGuidance; }

    public BigDecimal getFeeMiscLms() { return feeMiscLms; }
    public void setFeeMiscLms(BigDecimal feeMiscLms) { this.feeMiscLms = feeMiscLms; }

    public BigDecimal getFeeMiscInsurance() { return feeMiscInsurance; }
    public void setFeeMiscInsurance(BigDecimal feeMiscInsurance) { this.feeMiscInsurance = feeMiscInsurance; }

    public BigDecimal getFeeMiscCultural() { return feeMiscCultural; }
    public void setFeeMiscCultural(BigDecimal feeMiscCultural) { this.feeMiscCultural = feeMiscCultural; }

    public BigDecimal getFeeMiscAv() { return feeMiscAv; }
    public void setFeeMiscAv(BigDecimal feeMiscAv) { this.feeMiscAv = feeMiscAv; }

    public BigDecimal getFeeMiscEnergy() { return feeMiscEnergy; }
    public void setFeeMiscEnergy(BigDecimal feeMiscEnergy) { this.feeMiscEnergy = feeMiscEnergy; }

    public BigDecimal getFeeOtherLateEnrollment() { return feeOtherLateEnrollment; }
    public void setFeeOtherLateEnrollment(BigDecimal feeOtherLateEnrollment) { this.feeOtherLateEnrollment = feeOtherLateEnrollment; }

    public BigDecimal getFeeOtherAddDrop() { return feeOtherAddDrop; }
    public void setFeeOtherAddDrop(BigDecimal feeOtherAddDrop) { this.feeOtherAddDrop = feeOtherAddDrop; }

    public BigDecimal getFeeOtherInstallment() { return feeOtherInstallment; }
    public void setFeeOtherInstallment(BigDecimal feeOtherInstallment) { this.feeOtherInstallment = feeOtherInstallment; }

    public BigDecimal getFeeOtherId() { return feeOtherId; }
    public void setFeeOtherId(BigDecimal feeOtherId) { this.feeOtherId = feeOtherId; }

    public BigDecimal getFeeOtherInsurance() { return feeOtherInsurance; }
    public void setFeeOtherInsurance(BigDecimal feeOtherInsurance) { this.feeOtherInsurance = feeOtherInsurance; }

    public BigDecimal getFeeOtherComp() { return feeOtherComp; }
    public void setFeeOtherComp(BigDecimal feeOtherComp) { this.feeOtherComp = feeOtherComp; }

    public BigDecimal getFeeOtherDev() { return feeOtherDev; }
    public void setFeeOtherDev(BigDecimal feeOtherDev) { this.feeOtherDev = feeOtherDev; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public double getFee(String feeCode) {
        if (feeCode == null) return 0.0;
        BigDecimal val = switch(feeCode.toUpperCase()) {
            case "TUITION_PER_UNIT" -> feeTuitionPerUnit;
            case "LEC_FEE_PER_UNIT" -> feeLecPerUnit;
            case "LAB_FEE_PER_UNIT" -> feeLabPerUnit;
            case "COMP_FEE_PER_UNIT" -> feeCompPerUnit;
            case "RLE_RATE_PER_HOUR", "RLE_FEE_PER_UNIT" -> feeRlePerUnit;
            case "MISC_REGISTRATION" -> feeMiscRegistration;
            case "MISC_LIBRARY" -> feeMiscLibrary;
            case "MISC_MEDICAL" -> feeMiscMedical;
            case "MISC_ID" -> feeMiscId;
            case "MISC_ATHLETIC" -> feeMiscAthletic;
            case "MISC_GUIDANCE" -> feeMiscGuidance;
            case "MISC_LMS" -> feeMiscLms;
            case "MISC_INS" -> feeMiscInsurance;
            case "MISC_CULT" -> feeMiscCultural;
            case "MISC_AV" -> feeMiscAv;
            case "MISC_ENERGY" -> feeMiscEnergy;
            case "OTHER_LATE_ENROLLMENT" -> feeOtherLateEnrollment;
            case "OTHER_ADD_DROP" -> feeOtherAddDrop;
            case "OTHER_INSTALLMENT" -> feeOtherInstallment;
            case "OTHER_ID" -> feeOtherId;
            case "OTHER_INS" -> feeOtherInsurance;
            case "OTHER_COMP" -> feeOtherComp;
            case "OTHER_DEV" -> feeOtherDev;
            default -> BigDecimal.ZERO;
        };
        return val != null ? val.doubleValue() : 0.0;
    }

    public void setFee(String feeCode, double amount) {
        if (feeCode == null) return;
        BigDecimal val = BigDecimal.valueOf(amount);
        switch(feeCode.toUpperCase()) {
            case "TUITION_PER_UNIT" -> this.feeTuitionPerUnit = val;
            case "LEC_FEE_PER_UNIT" -> this.feeLecPerUnit = val;
            case "LAB_FEE_PER_UNIT" -> this.feeLabPerUnit = val;
            case "COMP_FEE_PER_UNIT" -> this.feeCompPerUnit = val;
            case "RLE_RATE_PER_HOUR", "RLE_FEE_PER_UNIT" -> this.feeRlePerUnit = val;
            case "MISC_REGISTRATION" -> this.feeMiscRegistration = val;
            case "MISC_LIBRARY" -> this.feeMiscLibrary = val;
            case "MISC_MEDICAL" -> this.feeMiscMedical = val;
            case "MISC_ID" -> this.feeMiscId = val;
            case "MISC_ATHLETIC" -> this.feeMiscAthletic = val;
            case "MISC_GUIDANCE" -> this.feeMiscGuidance = val;
            case "MISC_LMS" -> this.feeMiscLms = val;
            case "MISC_INS" -> this.feeMiscInsurance = val;
            case "MISC_CULT" -> this.feeMiscCultural = val;
            case "MISC_AV" -> this.feeMiscAv = val;
            case "MISC_ENERGY" -> this.feeMiscEnergy = val;
            case "OTHER_LATE_ENROLLMENT" -> this.feeOtherLateEnrollment = val;
            case "OTHER_ADD_DROP" -> this.feeOtherAddDrop = val;
            case "OTHER_INSTALLMENT" -> this.feeOtherInstallment = val;
            case "OTHER_ID" -> this.feeOtherId = val;
            case "OTHER_INS" -> this.feeOtherInsurance = val;
            case "OTHER_COMP" -> this.feeOtherComp = val;
            case "OTHER_DEV" -> this.feeOtherDev = val;
        }
    }
}
