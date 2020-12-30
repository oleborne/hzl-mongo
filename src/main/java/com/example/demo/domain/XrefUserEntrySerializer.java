package com.example.demo.domain;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.StreamSerializer;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class XrefUserEntrySerializer implements StreamSerializer<XrefUserEntry> {
  @Override
  public void write(ObjectDataOutput out, XrefUserEntry object) throws IOException {
    out.writeUTF(object.getPhoenixId());
    out.writeUTF(object.getUOpenId());
    out.writeUTF(object.getEdcId());
  }

  @Override
  public XrefUserEntry read(ObjectDataInput in) throws IOException {
    return XrefUserEntry.builder()
            .phoenixId(in.readUTF())
            .uOpenId(in.readUTF())
            .edcId(in.readUTF())
            .build();
  }

  @Override
  public int getTypeId() {
    return 3;
  }
}
