package com.sportify.identity.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/** Tracks JSON presence so omitted properties preserve their stored values. */
@Getter
public class UpdateProfileRequest {
    @JsonIgnore
    private final Set<String> suppliedFields = new HashSet<>();

    @Size(max = 100, message = "Full name must be at most 100 characters")
    @Pattern(regexp = "(?s).*[^\\p{javaWhitespace}].*", message = "Full name is required")
    private String fullName;

    @Pattern(regexp = "^\\+?\\d{8,15}$", message = "Phone must be 8-15 digits with optional leading +")
    private String phone;

    @Past(message = "Date of birth must be before today")
    private LocalDate dateOfBirth;

    @Size(max = 255, message = "Address must be at most 255 characters")
    private String address;

    @Pattern(regexp = "IMPROVE_FITNESS|BUILD_SKILLS|COMPETITION", message = "Training goal is invalid")
    private String mainGoal;

    @Pattern(regexp = "BEGINNER|INTERMEDIATE|ADVANCED", message = "Experience level is invalid")
    private String currentLevel;

    public boolean has(String field) {
        return suppliedFields.contains(field);
    }

    public void setFullName(String value) {
        suppliedFields.add("fullName");
        fullName = value == null ? null : value.trim();
    }

    public void setPhone(String value) {
        suppliedFields.add("phone");
        phone = optional(value);
    }

    public void setDateOfBirth(LocalDate value) {
        suppliedFields.add("dateOfBirth");
        dateOfBirth = value;
    }

    public void setAddress(String value) {
        suppliedFields.add("address");
        address = optional(value);
    }

    public void setMainGoal(String value) {
        suppliedFields.add("mainGoal");
        mainGoal = optional(value);
    }

    public void setCurrentLevel(String value) {
        suppliedFields.add("currentLevel");
        currentLevel = optional(value);
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
