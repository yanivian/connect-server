package com.yanivian.connect.backend.aspect;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.Transaction;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.yanivian.connect.backend.aspect.ProfilesAspect.ProfileCache;
import com.yanivian.connect.backend.dao.ChatDao;
import com.yanivian.connect.backend.dao.ChatDao.ChatModel;
import com.yanivian.connect.backend.dao.ChatMessageDao;
import com.yanivian.connect.backend.dao.ChatMessageDao.ChatMessageModel;
import com.yanivian.connect.backend.dao.ChatParticipantDao;
import com.yanivian.connect.backend.dao.ChatParticipantDao.ChatParticipantModel;
import com.yanivian.connect.backend.dao.DatastoreUtil;
import com.yanivian.connect.backend.proto.aspect.ChatGistInfo;
import com.yanivian.connect.backend.proto.aspect.ChatMessageInfo;
import com.yanivian.connect.backend.proto.aspect.ChatSlice;
import com.yanivian.connect.backend.taskqueue.AsyncTaskQueueAdapter;

/** Aspect that deals with chat. */
public final class ChatsAspect {

  private final ChatDao chatDao;
  private final ChatMessageDao chatMessageDao;
  private final ChatParticipantDao chatParticipantDao;
  private final ProfilesAspect profilesAspect;
  private final DatastoreService datastore;
  private final AsyncTaskQueueAdapter asyncTaskQueue;

  @Inject
  ChatsAspect(ChatDao chatDao, ChatMessageDao chatMessageDao, ChatParticipantDao chatParticipantDao,
      ProfilesAspect profilesAspect, DatastoreService datastore,
      AsyncTaskQueueAdapter asyncTaskQueue) {
    this.chatDao = chatDao;
    this.chatMessageDao = chatMessageDao;
    this.chatParticipantDao = chatParticipantDao;
    this.profilesAspect = profilesAspect;
    this.datastore = datastore;
    this.asyncTaskQueue = asyncTaskQueue;
  }

  public ChatSlice postMessageToUser(String userID, String targetUserID, Optional<String> text) {
    ImmutableSet<String> participantUserIDs = ImmutableSet.of(userID, targetUserID);
    Optional<String> optionalChatID =
        chatDao.findChatByParticipants(participantUserIDs).map(ChatModel::getID);
    return DatastoreUtil.newTransaction(datastore, txn -> {
      // Create or update chat entity.
      ChatModel chat;
      if (optionalChatID.isPresent()) {
        chat = chatDao.getChat(txn, optionalChatID.get()).orElseThrow(IllegalStateException::new);
        chat.setMostRecentMessageID(chat.getMostRecentMessageID() + 1).save(txn, datastore);
      } else {
        chat = chatDao.createChat(txn, participantUserIDs);
      }

      // Post message within the chat.
      return postMessage(txn, userID, chat, text);
    });
  }

  public ChatSlice postMessageToChat(String userID, String chatID, Optional<String> text) {
    return DatastoreUtil.newTransaction(datastore, txn -> {
      // Update chat entity.
      ChatModel chat = chatDao.getChat(txn, chatID).orElseThrow(IllegalStateException::new);
      chat.setMostRecentMessageID(chat.getMostRecentMessageID() + 1).save(txn, datastore);

      // Post message within the chat.
      return postMessage(txn, userID, chat, text);
    });
  }

  private ChatSlice postMessage(Transaction txn, String userID, ChatModel chat,
      Optional<String> text) {
    // Fail if message already exists.
    String chatID = chat.getID();
    long messageID = chat.getMostRecentMessageID();
    Preconditions.checkState(!chatMessageDao.getChatMessage(txn, chatID, messageID).isPresent());

    // Create message entity.
    ChatMessageModel message =
        chatMessageDao.createChatMessage(txn, chatID, messageID, userID, text);

    // Notify other participants.
    for (String participantUserID : chat.getParticipantUserIDs()) {
      if (!participantUserID.equals(userID)) {
        asyncTaskQueue.notifyChatMessagePosted(txn, chatID, messageID, participantUserID);
      }
    }

    // Create or update participant entity.
    Optional<ChatParticipantModel> existingParticipant =
        chatParticipantDao.getChatParticipant(txn, chatID, userID);
    ChatParticipantModel participant = existingParticipant.isPresent()
        ? existingParticipant.get().setMostRecentObservedMessageID(messageID).save(txn, datastore)
        : chatParticipantDao.createChatParticipant(txn, chatID, userID, messageID);

    return toSlice(txn, chat, participant, ImmutableList.of(message));
  }

  public ChatSlice toSlice(Transaction txn, ChatModel chat, ChatParticipantModel participant,
      ImmutableList<ChatMessageModel> messages) {
    return toSlice(getProfileCache(txn, chat, messages), chat, participant, messages);
  }

  public ProfileCache getProfileCache(Transaction txn, ChatModel chat,
      ImmutableList<ChatMessageModel> messages) {
    Preconditions.checkState(!messages.isEmpty());

    // Fetch all relevant profiles.
    Set<String> allUserIDs = new HashSet<>(chat.getParticipantUserIDs());
    allUserIDs.addAll(chat.getTypingUserIDs());
    messages.stream().map(ChatMessageModel::getUserID).forEach(allUserIDs::add);
    return profilesAspect.getProfiles(txn, allUserIDs);
  }

  public ChatSlice toSlice(ProfileCache profileCache, ChatModel chat,
      ChatParticipantModel participant, ImmutableList<ChatMessageModel> messages) {
    // Create messages with poster details.
    ImmutableList<ChatMessageInfo> messageInfos = messages.stream().map(message -> {
      ChatMessageInfo.Builder messageInfo =
          ChatMessageInfo.newBuilder().setMessageID(message.getMessageID())
              .setCreatedTimestampMillis(message.getCreatedTimestampMillis());
      profileCache.getUser(message.getUserID(), false).ifPresent(messageInfo::setPoster);
      message.getText().ifPresent(messageInfo::setText);
      return messageInfo.build();
    }).collect(ImmutableList.toImmutableList());

    // Create gist with latest message.
    ChatMessageInfo latestMessageInfo = messageInfos.get(0);
    ChatGistInfo.Builder gistInfo =
        ChatGistInfo.newBuilder().setChatID(chat.getID()).setLatestMessage(latestMessageInfo)
            .setLastSeenMessageID(participant.getMostRecentObservedMessageID());
    chat.getParticipantUserIDs().forEach(participantUserID -> {
      profileCache.getUser(participantUserID, false).ifPresent(gistInfo::addParticipants);
    });

    // Create slice.
    return ChatSlice.newBuilder().setGist(gistInfo).addAllMessages(messageInfos).build();
  }
}
