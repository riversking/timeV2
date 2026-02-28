package com.rivers.batch.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
@AllArgsConstructor
public class StatusCountVO {

    private String status;

    private Integer count;
}
