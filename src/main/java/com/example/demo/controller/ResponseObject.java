package com.example.demo.controller;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.Instant;

@Data
@Builder
public class ResponseObject implements Serializable {
    String fullMessage;
    Instant created;
}
