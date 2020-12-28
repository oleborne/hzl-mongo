package com.example.demo.domain;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.StreamSerializer;
import com.hazelcast.spring.context.SpringAware;

import java.io.IOException;

@SpringAware
public class CachedRequestSerializer implements StreamSerializer<CachedRequest> {

    @Override
    public void write(ObjectDataOutput out, CachedRequest object) throws IOException {
        out.writeUTF(object.getRequestHash());
        out.writeObject(object.getCachedResponse());
    }

    @Override
    public CachedRequest read(ObjectDataInput in) throws IOException {
        return CachedRequest.builder()
                .requestHash(in.readUTF())
                .cachedResponse(in.readObject())
                .build();
    }

    @Override
    public int getTypeId() {
        return 2;
    }
}
