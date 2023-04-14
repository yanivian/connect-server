package com.yanivian.connect.common.util;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.TextFormat;

public class TextProtoUtils {

  private static final Charset CHARSET = StandardCharsets.UTF_8;

  // Not instantiable.
  private TextProtoUtils() {}

  /** Unencrypted string encoding of the given proto payload, refered to as a textproto. */
  public static <T extends MessageOrBuilder> String encodeToString(MessageOrBuilder data) {
    return TextFormat.printer().printToString(data);
  }

  /** UTF-8 encoding of the string encoding of the given proto payload. */
  public static <T extends MessageOrBuilder> byte[] encode(MessageOrBuilder data) {
    return encodeToString(data).getBytes(CHARSET);
  }

  /** Decodes the given byte array and returns the original proto payload. */
  public static <T extends Message> T decode(byte[] code, Class<T> protoClass) throws IOException {
    return TextFormat.parse(new String(code, CHARSET), protoClass);
  }
}
