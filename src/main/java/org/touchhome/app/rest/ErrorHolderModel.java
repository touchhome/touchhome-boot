package org.touchhome.app.rest;

import lombok.Getter;

import static org.touchhome.app.rest.RestResponseEntityExceptionHandler.getErrorMessage;

@Getter
public class ErrorHolderModel {
    private String title;
    private String message;
    private String cause;
    private String errorType;

    public ErrorHolderModel(String title, String message, Exception ex) {
        this.title = title;
        this.message = message;
        this.cause = getErrorMessage(ex);
        this.errorType = ex.getClass().getSimpleName();
    }
}
