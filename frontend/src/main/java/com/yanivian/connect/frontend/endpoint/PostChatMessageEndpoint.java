package com.yanivian.connect.frontend.endpoint;

import java.io.IOException;
import java.util.Optional;
import javax.inject.Inject;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.common.base.Preconditions;
import com.yanivian.connect.backend.aspect.ChatsAspect;
import com.yanivian.connect.backend.dao.DatastoreUtil;
import com.yanivian.connect.backend.proto.aspect.ChatSlice;
import com.yanivian.connect.common.guice.GuiceEndpoint;
import com.yanivian.connect.common.guice.GuiceEndpoint.AllowPost;

@WebServlet(name = "PostChatMessageEndpoint", urlPatterns = {"/chat/postmessage"})
@AllowPost
public final class PostChatMessageEndpoint extends GuiceEndpoint {

  private static final String PARAM_CHAT_ID = "chatID";
  private static final String PARAM_TARGET_USER_ID = "targetUserID";
  private static final String PARAM_TEXT = "text";

  @Inject
  private AuthHelper authHelper;
  @Inject
  private ChatsAspect chatsAspect;
  @Inject
  private DatastoreService datastore;

  // Servlets must have public no-arg constructors.
  public PostChatMessageEndpoint() {}

  @Override
  protected void process(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    Optional<String> userID = authHelper.getVerifiedUserID(req, resp);
    if (!userID.isPresent()) {
      return;
    }

    Optional<String> chatID = getOptionalParameter(req, PARAM_CHAT_ID);
    Optional<String> targetUserID = getOptionalParameter(req, PARAM_TARGET_USER_ID);
    Preconditions.checkState(chatID.isPresent() ^ targetUserID.isPresent());

    Optional<String> text = getOptionalParameter(req, PARAM_TEXT);

    ChatSlice result = DatastoreUtil.newTransaction(datastore, txn -> {
      if (targetUserID.isPresent()) {
        return chatsAspect.postMessageToUser(userID.get(), targetUserID.get(), text);
      }
      if (chatID.isPresent()) {
        return chatsAspect.postMessageToChat(userID.get(), chatID.get(), text);
      }
      throw new IllegalStateException();
    });

    writeJsonResponse(resp, result);
  }
}
