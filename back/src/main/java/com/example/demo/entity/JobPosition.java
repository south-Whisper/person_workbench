package com.example.demo.entity;

import lombok.Data;

@Data
public class JobPosition {

    private Long id;

    private String name;

    private Integer minSalary;

    private Integer maxSalary;

}
