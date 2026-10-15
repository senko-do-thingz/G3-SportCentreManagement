package com.sportify.core.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

    String message() default "Password must be 8 to 64 characters long, contain at least one letter and one digit, and not exceed 72 bytes";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
