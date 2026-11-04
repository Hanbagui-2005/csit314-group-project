package com.csit314.platform.dto;

// Anthony's review - customersImported/designersImported are real counts of what
// actually got created this run (an account whose email already existed is skipped,
// not counted), not just an echo of the requested count
public record DemoDataImportResult(int customersImported, int designersImported, int designersApproved) {
}
