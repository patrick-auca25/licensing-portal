package rw.bnr.licensing.enums;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum ApplicationStatus {
    DRAFT("DRF", "Draft"),
    SUBMITTED("SUB", "Submitted"),
    UNDER_REVIEW("URV", "Under Review"),
    ADDITIONAL_INFO_REQUESTED("AIR", "Additional Info Requested"),
    REVIEWED("REV", "Reviewed"),
    APPROVED("APV", "Approved"),
    REJECTED("REJ", "Rejected");

    private static final Map<String, ApplicationStatus> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toMap(ApplicationStatus::getCode, Function.identity()));

    private final String code;
    private final String description;

    ApplicationStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static ApplicationStatus fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        ApplicationStatus status = BY_CODE.get(code.toUpperCase());
        if (status == null) {
            throw new IllegalArgumentException("Unknown application status code: " + code);
        }
        return status;
    }
}
