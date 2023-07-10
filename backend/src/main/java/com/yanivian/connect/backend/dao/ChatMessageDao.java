package com.yanivian.connect.backend.dao;

import java.time.Clock;
import java.util.Optional;
import javax.inject.Inject;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.SortDirection;
import com.google.appengine.api.datastore.Transaction;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
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

  public ChatMessageModel createChatMessage(Transaction txn, String chatID, long messageID,
      String userID, Optional<String> text) {
    Entity entity = new Entity(ChatMessageModel.KIND, messageID, ChatDao.toKey(chatID));
    return new ChatMessageModel(entity).setCreatedTimestampMillis(clock.millis()).setUserID(userID)
        .save(txn, datastore);
  }

  /** Lists messages in a given chat in decreasing order of message ID. */
  public ImmutableList<ChatMessageModel> listChatMessages(Transaction txn, String chatID) {
    Query query = new Query(ChatMessageModel.KIND).setAncestor(ChatDao.toKey(chatID))
        .addSort("__key__", SortDirection.DESCENDING);
    return Streams.stream(datastore.prepare(txn, query).asIterable()).map(ChatMessageModel::new)
        .collect(ImmutableList.toImmutableList());
  }

  public static final class ChatMessageModel extends DatastoreModel<ChatMessage, ChatMessageModel> {

    private static final String KIND = "ChatMessage";

    private static final class Columns {
      static final String CreatedTimestampMillis = "CreatedTimestampMillis";
      static final String UserID = "UserID";
      static final String Text = "Text";
    }

    private ChatMessageModel(Entity entity) {
      super(entity);
    }

    @Override
    public ChatMessage toProto() {
      ChatMessage.Builder chatMessage =
          ChatMessage.newBuilder().setChatID(getChatID()).setMessageID(getMessageID())
              .setCreatedTimestampMillis(getCreatedTimestampMillis()).setUserID(getUserID());
      getText().ifPresent(chatMessage::setText);
      return chatMessage.build();
    }

    public String getChatID() {
      return getKey().getParent().getName();
    }

    public long getMessageID() {
      return getKey().getId();
    }

    public ChatMessageModel setCreatedTimestampMillis(long timestampMillis) {
      entity.setProperty(Columns.CreatedTimestampMillis, timestampMillis);
      return this;
    }

    public long getCreatedTimestampMillis() {
      return (long) entity.getProperty(Columns.CreatedTimestampMillis);
    }

    public ChatMessageModel setUserID(String userID) {
      entity.setUnindexedProperty(Columns.UserID, userID);
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

  static Key toKey(String chatID, long messageID) {
    return KeyFactory.createKey(ChatDao.toKey(chatID), ChatMessageModel.KIND, messageID);
  }
}
