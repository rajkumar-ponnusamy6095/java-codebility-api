package com.example.codebility_api.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Objects;

public class FieldsMatchValidator implements ConstraintValidator<FieldsMatch, Object> {

    private String field;
    private String confirmField;

    @Override
    public void initialize(FieldsMatch annotation) {
        this.field = annotation.field();
        this.confirmField = annotation.confirmField();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        Object primary = read(value, field);
        if (primary == null || primary.toString().isEmpty()) {
            return true;
        }
        if (Objects.equals(primary, read(value, confirmField))) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode(confirmField)
                .addConstraintViolation();
        return false;
    }

    private static Object read(Object target, String accessor) {
        try {
            return target.getClass().getMethod(accessor).invoke(target);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(
                    "No accessor '" + accessor + "' on " + target.getClass().getName(), ex);
        }
    }
}
