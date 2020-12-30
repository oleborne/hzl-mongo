package com.example.demo.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class XrefUserEntry {
    String phoenixId;
    String uOpenId;
    String edcId;
}
