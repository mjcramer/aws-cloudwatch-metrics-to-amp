package com.adobe.aep.metrics.records;


public record Value(
    double min,
    double max,
    double sum,
    double count
) {}
