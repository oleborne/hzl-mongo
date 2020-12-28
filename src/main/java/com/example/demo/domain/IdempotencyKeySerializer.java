package com.example.demo.domain;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.StreamSerializer;

import java.io.IOException;

public class IdempotencyKeySerializer implements StreamSerializer<IdempotencyKey> {
    @Override
    public void write(ObjectDataOutput out, IdempotencyKey object) throws IOException {
        out.writeUTF(object.getPaymentIntegratorAccountId());
        out.writeUTF(object.getRequestId());
    }

    @Override
    public IdempotencyKey read(ObjectDataInput in) throws IOException {
        return IdempotencyKey.builder()
                .paymentIntegratorAccountId(in.readUTF())
                .requestId(in.readUTF())
                .build();
    }

    @Override
    public int getTypeId() {
        return 1;
    }
}
