package com.user.management.dto;

import lombok.Data;

import java.util.Map;

@Data
public class AnalyzeResponse {
    private Map<String, Object> info;
    private Object search_results; // Use appropriate type if needed

}

