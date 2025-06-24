package com.user.management.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class AnalyzeRequest {
    @NotBlank
    private String company;
    Map<String, Object> extraction_schema;// Optional
    private String user_notes; // Optional

}

