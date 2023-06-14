package com.yanivian.connect.common.util;

import java.io.IOException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;

public final class FirebaseMessageBuilder {

  public static FirebaseMessageBuilder newMessage(String deviceToken) {
    return new FirebaseMessageBuilder(Message.builder().setToken(deviceToken));
  }

  private final Message.Builder message;

  private FirebaseMessageBuilder(Message.Builder message) {
    this.message = message;
  }

  public Message build() {
    return message.build();
  }

  public FirebaseMessageBuilder withNotification(Notification notification) {
    message.setNotification(notification);
    return this;
  }

  public <T extends MessageOrBuilder> FirebaseMessageBuilder withData(String key, T payload)
      throws IOException {
    message.putData(key, encodePayload(payload));
    return this;
  }

  private static <T extends MessageOrBuilder> String encodePayload(T payload) throws IOException {
    return JsonFormat.printer().print(payload);
  }
}
