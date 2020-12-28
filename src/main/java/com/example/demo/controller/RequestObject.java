package com.example.demo.controller;

import lombok.Data;

import java.time.Instant;

@Data
public class RequestObject {
    Instant timeHeader;
    String name;
    String keyId;
}
