package com.yanivian.connect.backend.aspect;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.Transaction;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
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
import com.yanivian.connect.backend.proto.aspect.ChatsSnapshot;
import com.yanivian.connect.backend.proto.key.ChatMessageKey;
import com.yanivian.connect.backend.proto.key.ChatParticipantKey;
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

  public ChatsSnapshot getSnapshot(String userID) {
    // List all applicable chats, which cannot be within a transaction.
    ImmutableList<ChatModel> chats = chatDao.listChatsByParticipant(userID);
    ImmutableList<String> chatIDs =
        chats.stream().map(ChatModel::getID).collect(ImmutableList.toImmutableList());

    return DatastoreUtil.newTransaction(datastore, txn -> {
      // Fetch chats, most recent messages and participants transactionally.
      ImmutableMap<String, ChatModel> chatsMap = chatDao.getChats(txn, chatIDs);
      ImmutableMap<String, ChatMessageKey> messageKeysMap = chats.stream()
          .map(chat -> ChatMessageDao.toChatMessageKey(chat.getID(), chat.getMostRecentMessageID()))
          .collect(ImmutableMap.toImmutableMap(ChatMessageKey::getChatID, Function.identity()));
      ImmutableMap<ChatMessageKey, ChatMessageModel> messagesMap =
          chatMessageDao.getChatMessages(txn, messageKeysMap.values());
      ImmutableMap<String, ChatParticipantKey> participantKeysMap = chats.stream()
          .map(chat -> ChatParticipantDao.toChatParticipantKey(chat.getID(), userID))
          .collect(ImmutableMap.toImmutableMap(ChatParticipantKey::getChatID, Function.identity()));
      ImmutableMap<ChatParticipantKey, ChatParticipantModel> participantsMap =
          chatParticipantDao.getChatParticipants(txn, participantKeysMap.values());

      // Fetch profiles transactionally.
      Set<String> allUserIDs = new HashSet<>();
      chatsMap.values().forEach(chat -> allUserIDs.addAll(chat.getParticipantUserIDs()));
      messagesMap.values().forEach(message -> allUserIDs.add(message.getUserID()));
      allUserIDs.add(userID);
      ProfileCache profileCache = profilesAspect.getProfiles(txn, allUserIDs);

      // Populate result.
      ChatsSnapshot.Builder snapshot = ChatsSnapshot.newBuilder();
      for (String chatID : chatIDs) {
        ChatModel chat = chatsMap.get(chatID);
        ChatMessageModel message = messagesMap.get(messageKeysMap.get(chatID));
        ChatParticipantModel participant = participantsMap.get(participantKeysMap.get(chatID));
        if (chat == null || message == null) {
          continue;
        }
        snapshot.addChats(toSlice(profileCache, chat, ImmutableList.of(message),
            Optional.ofNullable(participant)));
      }
      return snapshot.build();
    });
  }

  public ChatSlice listChatMessages(String chatID, String userID) {
    return DatastoreUtil.newTransaction(datastore, txn -> {
      ChatModel chat = chatDao.getChat(txn, chatID).orElseThrow(IllegalStateException::new);
      ImmutableList<ChatMessageModel> messages = chatMessageDao.listChatMessages(txn, chatID);
      // A chat must have at least one message.
      long mostRecentMessageID = messages.stream().mapToLong(ChatMessageModel::getMessageID).max()
          .orElseThrow(IllegalStateException::new);
      ChatParticipantModel participant =
          createOrUpdateParticipant(txn, userID, chatID, mostRecentMessageID);
      return toSlice(txn, chat, messages, Optional.of(participant));
    });
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
        Preconditions.checkState(chat.getParticipantUserIDs().contains(userID));
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
      Preconditions.checkState(chat.getParticipantUserIDs().contains(userID));
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
    ChatParticipantModel participant = createOrUpdateParticipant(txn, userID, chatID, messageID);

    return toSlice(txn, chat, ImmutableList.of(message), Optional.of(participant));
  }

  private ChatParticipantModel createOrUpdateParticipant(Transaction txn, String userID,
      String chatID, long mostRecentMessageID) {
    Optional<ChatParticipantModel> existingParticipant =
        chatParticipantDao.getChatParticipant(txn, chatID, userID);
    ChatParticipantModel participant = existingParticipant.isPresent()
        ? existingParticipant.get().setMostRecentObservedMessageID(mostRecentMessageID).save(txn,
            datastore)
        : chatParticipantDao.createChatParticipant(txn, chatID, userID, mostRecentMessageID);
    return participant;
  }

  public ChatSlice toSlice(Transaction txn, ChatModel chat,
      ImmutableList<ChatMessageModel> messages, Optional<ChatParticipantModel> participant) {
    // Fetch all relevant profiles.
    Set<String> allUserIDs = new HashSet<>(chat.getParticipantUserIDs());
    messages.stream().map(ChatMessageModel::getUserID).forEach(allUserIDs::add);

    ProfileCache profileCache = profilesAspect.getProfiles(txn, allUserIDs);
    return toSlice(profileCache, chat, messages, participant);
  }

  public ChatSlice toSlice(ProfileCache profileCache, ChatModel chat,
      ImmutableList<ChatMessageModel> messages, Optional<ChatParticipantModel> participant) {
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
        ChatGistInfo.newBuilder().setChatID(chat.getID()).setLatestMessage(latestMessageInfo);
    if (participant.isPresent()) {
      gistInfo.setLastSeenMessageID(participant.get().getMostRecentObservedMessageID());
    }
    chat.getParticipantUserIDs().forEach(participantUserID -> {
      profileCache.getUser(participantUserID, false).ifPresent(gistInfo::addParticipants);
    });

    // Create slice.
    return ChatSlice.newBuilder().setGist(gistInfo).addAllMessages(messageInfos).build();
  }
}
