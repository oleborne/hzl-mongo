package com.example.demo.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CachedRequest {
    String requestHash;
    Object cachedResponse;
}
