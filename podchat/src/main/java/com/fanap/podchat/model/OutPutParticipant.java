package com.fanap.podchat.model;

public class OutPutParticipant {
    private boolean hasError;
    private String errorMessage;
    private long errorCode;

    private ResultParticipant result;

    public boolean isHasError() {
        return hasError;
    }

    public void setHasError(boolean hasError) {
        this.hasError = hasError;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public long getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(long errorCode) {
        this.errorCode = errorCode;
    }




    public ResultParticipant getResult() {
        return result;
    }

    public void setResult(ResultParticipant result) {
        this.result = result;
    }


}
