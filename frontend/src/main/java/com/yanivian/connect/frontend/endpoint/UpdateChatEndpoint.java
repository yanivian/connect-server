package com.yanivian.connect.frontend.endpoint;

import java.io.IOException;
import java.util.Optional;
import javax.inject.Inject;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.yanivian.connect.backend.aspect.ChatsAspect;
import com.yanivian.connect.backend.proto.aspect.ChatSlice;
import com.yanivian.connect.common.guice.GuiceEndpoint;
import com.yanivian.connect.common.guice.GuiceEndpoint.AllowPost;

@WebServlet(name = "UpdateChatEndpoint", urlPatterns = {"/chat/update"})
@AllowPost
public final class UpdateChatEndpoint extends GuiceEndpoint {

  private static final String PARAM_CHAT_ID = "chatID";
  private static final String PARAM_LAST_SEEN_MESSAGE_ID = "lastSeenMessageID";
  private static final String PARAM_CLEAR_DRAFT_TEXT = "clearDraftText";
  private static final String PARAM_SET_DRAFT_TEXT = "setDraftText";

  @Inject
  private AuthHelper authHelper;
  @Inject
  private ChatsAspect chatsAspect;

  // Servlets must have public no-arg constructors.
  public UpdateChatEndpoint() {}

  @Override
  protected void process(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    Optional<String> userID = authHelper.getVerifiedUserID(req, resp);
    if (!userID.isPresent()) {
      return;
    }

    String chatID = getRequiredParameter(req, PARAM_CHAT_ID);

    Optional<Long> lastSeenMessageID =
        getOptionalParameter(req, PARAM_LAST_SEEN_MESSAGE_ID).map(Long::parseLong);

    Optional<String> clearDraftText = getOptionalParameter(req, PARAM_CLEAR_DRAFT_TEXT);
    Optional<String> setDraftText = getOptionalParameter(req, PARAM_SET_DRAFT_TEXT);
    Optional<String> draftText = clearDraftText.isPresent() ? Optional.of("") : setDraftText;

    ChatSlice result = chatsAspect.updateChat(userID.get(), chatID, lastSeenMessageID, draftText);

    writeJsonResponse(resp, result);
  }
}
