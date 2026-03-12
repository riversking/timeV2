package com.rivers.batch.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * @author xx
 */
@Data
@AllArgsConstructor
public class JobCountVO implements Serializable {

    @Serial
    private static final long serialVersionUID = -4150525509201602026L;

    private Long count;

    private String jobName;

    private String status;

}
