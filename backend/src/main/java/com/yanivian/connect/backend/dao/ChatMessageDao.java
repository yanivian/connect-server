package com.yanivian.connect.backend.dao;

import java.time.Clock;
import java.util.Optional;
import java.util.function.Function;
import javax.inject.Inject;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.SortDirection;
import com.google.appengine.api.datastore.Transaction;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import com.yanivian.connect.backend.proto.key.ChatMessageKey;
import com.yanivian.connect.backend.proto.model.ChatMessage;

public final class ChatMessageDao {

  private final DatastoreService datastore;
  private final Clock clock;

  @Inject
  ChatMessageDao(DatastoreService datastore, Clock clock) {
    this.datastore = datastore;
    this.clock = clock;
  }

  public Optional<ChatMessageModel> getChatMessage(Transaction txn, String chatID, long messageID) {
    try {
      Entity entity = datastore.get(txn, toKey(chatID, messageID));
      return Optional.of(new ChatMessageModel(entity));
    } catch (EntityNotFoundException enfe) {
      return Optional.empty();
    }
  }

  public ImmutableMap<ChatMessageKey, ChatMessageModel> getChatMessages(Transaction txn,
      ImmutableCollection<ChatMessageKey> keys) {
    ImmutableList<Key> entityKeys =
        keys.stream().map(key -> toKey(key.getChatID(), key.getMessageID())).distinct()
            .collect(ImmutableList.toImmutableList());
    return datastore.get(txn, entityKeys).values().stream().map(ChatMessageModel::new).collect(
        ImmutableMap.toImmutableMap(ChatMessageModel::getChatMessageKey, Function.identity()));
  }

  public ChatMessageModel createChatMessage(Transaction txn, String chatID, long messageID,
      String userID, Optional<String> text) {
    Entity entity = new Entity(ChatMessageModel.KIND, messageID, ChatDao.toKey(chatID));
    return new ChatMessageModel(entity).setUserID(userID).setText(text).save(txn, datastore, clock);
  }

  /** Lists messages in a given chat in decreasing order of message ID. */
  public ImmutableList<ChatMessageModel> listChatMessages(Transaction txn, String chatID) {
    Query query = new Query(ChatMessageModel.KIND).setAncestor(ChatDao.toKey(chatID))
        .addSort("__key__", SortDirection.DESCENDING);
    return Streams.stream(datastore.prepare(txn, query).asIterable()).map(ChatMessageModel::new)
        .collect(ImmutableList.toImmutableList());
  }

  public static final class ChatMessageModel extends DatastoreModel<ChatMessageModel> {

    private static final String KIND = "ChatMessage";

    private static final class Columns {
      static final String UserID = "UserID";
      static final String Text = "Text";
    }

    private ChatMessageModel(Entity entity) {
      super(entity);
    }

    /** Returns a protobuf model representing the underlying entity. */
    public ChatMessage toProto() {
      ChatMessage.Builder chatMessage =
          ChatMessage.newBuilder().setChatID(getChatID()).setMessageID(getMessageID())
              .setTimestampMillis(getTimestampMillis()).setUserID(getUserID());
      getText().ifPresent(chatMessage::setText);
      return chatMessage.build();
    }

    public String getChatID() {
      return getKey().getParent().getName();
    }

    public long getMessageID() {
      return getKey().getId();
    }

    public ChatMessageKey getChatMessageKey() {
      return toChatMessageKey(getChatID(), getMessageID());
    }

    public ChatMessageModel setUserID(String userID) {
      entity.setProperty(Columns.UserID, userID);
      return this;
    }

    public String getUserID() {
      return (String) entity.getProperty(Columns.UserID);
    }

    public ChatMessageModel setText(Optional<String> text) {
      if (text.isPresent()) {
        entity.setUnindexedProperty(Columns.Text, text.get());
      } else {
        entity.removeProperty(Columns.Text);
      }
      return this;
    }

    public Optional<String> getText() {
      return getOptionalProperty(Columns.Text);
    }
  }

  public static ChatMessageKey toChatMessageKey(String chatID, long messageID) {
    return ChatMessageKey.newBuilder().setChatID(chatID).setMessageID(messageID).build();
  }

  static Key toKey(String chatID, long messageID) {
    return KeyFactory.createKey(ChatDao.toKey(chatID), ChatMessageModel.KIND, messageID);
  }
}
