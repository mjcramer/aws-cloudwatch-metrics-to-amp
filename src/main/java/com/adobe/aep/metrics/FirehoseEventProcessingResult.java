package com.adobe.aep.metrics;

import java.util.List;

public class FirehoseEventProcessingResult {

    public enum Result {
        Ok,
        Dropped,
        ProcessingFailed
    }

    public static class Record {
        private String recordId;
        private Result result;
        private String errorMessage = null;

        Record(String recordId, Result result) {
            this.recordId = recordId;
        }

        Record(String recordId, Result result, String errorMessage) {
            this(recordId, result);
            this.errorMessage = errorMessage;
        }

        public String getRecordId() {
            return recordId;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

    }

    private List<Record> records;

    public List<Record> getRecords() {
        return records;
    }

    public FirehoseEventProcessingResult(List<Record> records) {
        this.records = records;
    }

    public static FirehoseEventProcessingResult.Record createSuccessResult(String recordId) {
        return new FirehoseEventProcessingResult.Record(recordId, Result.Ok);
    }

    public static FirehoseEventProcessingResult.Record createFailureResult(String recordId, String errorMessage) {
        return new FirehoseEventProcessingResult.Record(recordId, Result.ProcessingFailed, errorMessage);
    }
}


