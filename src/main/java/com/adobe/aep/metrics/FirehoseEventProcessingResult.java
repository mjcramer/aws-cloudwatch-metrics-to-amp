package com.adobe.aep.metrics;

import java.util.Base64;
import java.util.List;


public class FirehoseEventProcessingResult {

    public enum Result {
        Ok,
        Dropped,
        ProcessingFailed
    }

    public static class Record {
        private final String recordId;
        private final Result result;
        private String data = "";

        Record(String recordId, Result result) {
            this.recordId = recordId;
            this.result = result;
        }

        Record(String recordId, Result result, String data) {
            this(recordId, result);
            this.setData(data);
        }

        public String getRecordId() {
            return recordId;
        }
        public Result getResult() { return result; }
        public String getData() { return data; }

        public void setData(String errorMessage) {
            this.data = Base64.getEncoder().encodeToString(errorMessage.getBytes());
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

    public static FirehoseEventProcessingResult.Record createDroppedResult(String recordId) {
        return new FirehoseEventProcessingResult.Record(recordId, Result.Dropped);
    }

    public static FirehoseEventProcessingResult.Record createFailureResult(String recordId, String errorMessage) {
        return new FirehoseEventProcessingResult.Record(recordId, Result.ProcessingFailed, errorMessage);
    }
}


