package com.yanivian.connect.frontend.endpoint;

import java.io.IOException;
import java.util.Optional;
import javax.inject.Inject;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.google.appengine.api.datastore.DatastoreService;
import com.yanivian.connect.backend.aspect.ChatsAspect;
import com.yanivian.connect.backend.dao.DatastoreUtil;
import com.yanivian.connect.backend.proto.aspect.ChatSlice;
import com.yanivian.connect.common.guice.GuiceEndpoint;
import com.yanivian.connect.common.guice.GuiceEndpoint.AllowPost;

@WebServlet(name = "ListChatMessagesEndpoint", urlPatterns = {"/chat/listmessages"})
@AllowPost
public final class ListChatMessagesEndpoint extends GuiceEndpoint {

  private static final String PARAM_CHAT_ID = "chatID";

  @Inject
  private AuthHelper authHelper;
  @Inject
  private ChatsAspect chatsAspect;
  @Inject
  private DatastoreService datastore;

  // Servlets must have public no-arg constructors.
  public ListChatMessagesEndpoint() {}

  @Override
  protected void process(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    Optional<String> userID = authHelper.getVerifiedUserID(req, resp);
    if (!userID.isPresent()) {
      return;
    }

    String chatID = getRequiredParameter(req, PARAM_CHAT_ID);

    ChatSlice result = DatastoreUtil.newTransaction(datastore, txn -> {
      return chatsAspect.listChatMessages(chatID, userID.get());
    });

    writeJsonResponse(resp, result);
  }
}
