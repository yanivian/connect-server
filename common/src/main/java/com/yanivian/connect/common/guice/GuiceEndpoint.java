package com.yanivian.connect.common.guice;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Optional;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.inject.Injector;
import com.google.protobuf.Internal;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.util.JsonFormat;
import com.yanivian.connect.common.util.TextProtoUtils;

public abstract class GuiceEndpoint extends HttpServlet {

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  public @interface AllowGet {
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  public @interface AllowPost {
  }

  public static final String GUICE_INJECTOR_ATTRIBUTE_NAME = "guiceServlet";

  protected final Logger logger = LogManager.getLogger(getClass());

  @Override
  public void init() throws ServletException {
    Injector injector = (Injector) getServletContext().getAttribute(GUICE_INJECTOR_ATTRIBUTE_NAME);
    Preconditions.checkNotNull(injector, "Guice injector not found.");
    injector.injectMembers(this);

    super.init();
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws IOException, ServletException {
    Preconditions.checkState(getClass().isAnnotationPresent(AllowGet.class), "GET not supported.");
    process(req, resp);
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws IOException, ServletException {
    Preconditions.checkState(getClass().isAnnotationPresent(AllowPost.class),
        "POST not supported.");
    process(req, resp);
  }

  protected String getRequiredParameter(HttpServletRequest req, String name) {
    String value = req.getParameter(name);
    Preconditions.checkState(!Strings.isNullOrEmpty(value));
    return value;
  }

  protected Optional<String> getOptionalParameter(HttpServletRequest req, String name) {
    String value = req.getParameter(name);
    return Strings.isNullOrEmpty(value) ? Optional.empty() : Optional.of(value);
  }

  /** Writes a protobuf message as a json response. */
  protected void writeJsonResponse(HttpServletResponse response, MessageOrBuilder messageOrBuilder)
      throws IOException {
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    String json = JsonFormat.printer().print(messageOrBuilder);
    PrintWriter writer = response.getWriter();
    writer.print(json);
    writer.flush();
  }

  protected <T extends Message> T parseJson(String json, Class<T> protoClass)
      throws InvalidProtocolBufferException {
    T.Builder builder = Internal.getDefaultInstance(protoClass).toBuilder();
    JsonFormat.parser().merge(json, builder);
    return (T) builder.build();
  }

  /** Writes a protobuf messages as a raw/protobuf response. */
  protected void writeProtoResponse(HttpServletResponse resp, MessageOrBuilder messageOrBuilder)
      throws IOException {
    byte[] protoBytes = TextProtoUtils.encode(messageOrBuilder);
    resp.setHeader("Content-Type", "application/x-protobuf");
    resp.setHeader("Content-Length", String.valueOf(protoBytes.length));
    resp.getOutputStream().write(protoBytes);
  }

  protected abstract void process(HttpServletRequest req, HttpServletResponse resp)
      throws IOException, ServletException;

  private static final long serialVersionUID = 1L;
}
