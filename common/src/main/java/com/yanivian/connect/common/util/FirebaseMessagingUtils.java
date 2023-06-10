package com.yanivian.connect.common.util;

import java.io.IOException;
import com.google.firebase.messaging.Message;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;

public final class FirebaseMessagingUtils {

  public static <T extends MessageOrBuilder> Message createMessage(String deviceToken, String key,
      T payload) throws IOException {
    return Message.builder().setToken(deviceToken).putData(key, JsonFormat.printer().print(payload))
        .build();
  }

  // Not instantiable.
  private FirebaseMessagingUtils() {}
}
