package com.rivers.batch.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@ToString
@AllArgsConstructor
public class StatusCountVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String status;

    private Integer count;

    private LocalDateTime createTime;
}
