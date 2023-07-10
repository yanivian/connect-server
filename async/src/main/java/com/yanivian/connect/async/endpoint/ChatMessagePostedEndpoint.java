package com.yanivian.connect.async.endpoint;

import java.io.IOException;
import java.util.Optional;
import javax.inject.Inject;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.common.collect.ImmutableList;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.yanivian.connect.backend.aspect.ChatsAspect;
import com.yanivian.connect.backend.aspect.ProfilesAspect.ProfileCache;
import com.yanivian.connect.backend.dao.ChatDao;
import com.yanivian.connect.backend.dao.ChatDao.ChatModel;
import com.yanivian.connect.backend.dao.ChatMessageDao;
import com.yanivian.connect.backend.dao.ChatMessageDao.ChatMessageModel;
import com.yanivian.connect.backend.dao.ChatParticipantDao;
import com.yanivian.connect.backend.dao.ChatParticipantDao.ChatParticipantModel;
import com.yanivian.connect.backend.dao.DatastoreUtil;
import com.yanivian.connect.backend.proto.aspect.ChatSlice;
import com.yanivian.connect.backend.proto.aspect.UserInfo;
import com.yanivian.connect.common.guice.GuiceEndpoint;
import com.yanivian.connect.common.guice.GuiceEndpoint.AllowPost;
import com.yanivian.connect.common.util.FirebaseMessageBuilder;

@WebServlet(name = "ChatMessagePostedEndpoint", urlPatterns = {"/chat/messageposted"})
@AllowPost
public final class ChatMessagePostedEndpoint extends GuiceEndpoint {

  private static final String PARAM_CHAT_ID = "chatID";
  private static final String PARAM_MESSAGE_ID = "messageID";
  private static final String PARAM_TARGET_USER_ID = "targetUserID";

  @Inject
  private ChatsAspect chatsAspect;
  @Inject
  private ChatDao chatDao;
  @Inject
  private ChatMessageDao chatMessageDao;
  @Inject
  private ChatParticipantDao chatParticipantDao;
  @Inject
  private DatastoreService datastore;
  @Inject
  private FirebaseMessaging firebaseMessaging;

  // Servlets must have public no-arg constructors.
  public ChatMessagePostedEndpoint() {}

  @Override
  protected void process(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String chatID = getRequiredParameter(req, PARAM_CHAT_ID);
    long messageID = Long.parseLong(getRequiredParameter(req, PARAM_MESSAGE_ID));
    String targetUserID = getRequiredParameter(req, PARAM_TARGET_USER_ID);

    boolean result = DatastoreUtil.newTransaction(datastore, txn -> {
      Optional<ChatModel> chat = chatDao.getChat(txn, chatID);
      if (!chat.isPresent()) {
        return false;
      }
      Optional<ChatParticipantModel> participant =
          chatParticipantDao.getChatParticipant(txn, chatID, targetUserID);
      if (!participant.isPresent()) {
        return false;
      }
      Optional<ChatMessageModel> message = chatMessageDao.getChatMessage(txn, chatID, messageID);
      if (!message.isPresent()) {
        return false;
      }
      ProfileCache profileCache = chatsAspect.getProfileCache(txn, null, null);
      Optional<String> deviceToken = profileCache.getDeviceToken(targetUserID);
      if (!deviceToken.isPresent()) {
        return false;
      }
      Optional<UserInfo> poster = profileCache.getUser(message.get().getUserID(), false);
      if (!poster.isPresent()) {
        return false;
      }

      Message firebaseMessage;
      try {
        String title =
            poster.get().hasName() ? String.format("New message from %s", poster.get().getName())
                : "New message";
        Notification.Builder notification = Notification.builder().setTitle(title);
        if (message.get().getText().isPresent()) {
          notification.setBody(message.get().getText().get());
        }
        if (poster.get().hasImage()) {
          notification.setImage(poster.get().getImage().getURL());
        }
        ChatSlice payload = chatsAspect.toSlice(profileCache, chat.get(), participant.get(),
            ImmutableList.of(message.get()));
        firebaseMessage = FirebaseMessageBuilder.newMessage(deviceToken.get())
            .withNotification(notification.build()).withData("ChatMessagePosted", payload).build();
      } catch (IOException ioe) {
        throw new IllegalStateException(ioe);
      }

      try {
        String firebaseMessageID = firebaseMessaging.send(firebaseMessage);
        logger.atInfo().log("Notified chat message posted: {}", firebaseMessageID);
        return true;
      } catch (FirebaseMessagingException fme) {
        logger.atError().withThrowable(fme).log("Failed to notify chat message posted.");
        return false;
      }
    });
    logger.atInfo().log("Chat message posted: chatID={} messageID={} targetUserID={} result={}",
        chatID, messageID, targetUserID, result);
  }
}
