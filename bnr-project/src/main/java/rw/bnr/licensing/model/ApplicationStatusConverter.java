package rw.bnr.licensing.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import rw.bnr.licensing.enums.ApplicationStatus;

@Converter(autoApply = false)
public class ApplicationStatusConverter implements AttributeConverter<ApplicationStatus, String> {

    @Override
    public String convertToDatabaseColumn(ApplicationStatus attribute) {
        return attribute != null ? attribute.getCode() : null;
    }

    @Override
    public ApplicationStatus convertToEntityAttribute(String dbData) {
        return ApplicationStatus.fromCode(dbData);
    }
}
