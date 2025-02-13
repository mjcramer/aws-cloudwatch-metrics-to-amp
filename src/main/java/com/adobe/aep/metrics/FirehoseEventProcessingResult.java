package com.adobe.aep.metrics;

import java.util.List;


public class FirehoseEventProcessingResult {

    private List<FirehoseEventProcessingResult.Record> records;

    public List<Record> getRecords() {
        return records;
    }

    public void setRecords(List<Record> records) {
        this.records = records;
    }

    public static class Record {
        private String recordId;
        private Result result;

        public String getRecordId() {
            return recordId;
        }

        public void setRecordId(String recordId) {
            this.recordId = recordId;
        }

        public Result getResult() {
            return result;
        }

        public void setResult(Result result) {
            this.result = result;
        }
    }

    public enum Result {
        Ok, Dropped, ProcessingFailed
    }

    public static Record createSuccessResult(String recordId) {
        Record response = new Record();
        response.setRecordId(recordId);
        response.setResult(FirehoseEventProcessingResult.Result.Ok);
        return response;
    }

    public static Record createFailureResult(String recordId) {
        Record response = new Record();
        response.setRecordId(recordId);
        response.setResult(Result.ProcessingFailed);
        return response;
    }
}


