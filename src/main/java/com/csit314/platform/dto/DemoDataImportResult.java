package com.csit314.platform.dto;

public record DemoDataImportResult(boolean alreadyImported, int customersImported,
                                   int designersImported, int designersApproved) {

    public static DemoDataImportResult skipped() {
        return new DemoDataImportResult(true, 0, 0, 0);
    }
}
