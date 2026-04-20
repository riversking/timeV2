package com.rivers.batch.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class JobRunTimeVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 8077908160543901513L;

    private String jobName;

    private String status;

    private LocalDateTime lastUpdated;
}
