package com.yanivian.connect.backend.dao;

import java.time.Clock;
import java.util.Optional;
import javax.inject.Inject;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Transaction;
import com.yanivian.connect.backend.proto.model.ChatParticipant;

public final class ChatParticipantDao {

  private final DatastoreService datastore;
  private final Clock clock;

  @Inject
  ChatParticipantDao(DatastoreService datastore, Clock clock) {
    this.datastore = datastore;
    this.clock = clock;
  }

  public Optional<ChatParticipantModel> getChatParticipant(Transaction txn, String chatID,
      String userID) {
    try {
      Entity entity = datastore.get(txn, toKey(chatID, userID));
      return Optional.of(new ChatParticipantModel(entity));
    } catch (EntityNotFoundException enfe) {
      return Optional.empty();
    }
  }

  public ChatParticipantModel createChatParticipant(Transaction txn, String chatID, String userID,
      long mostRecentObservedMessageID) {
    Entity entity = new Entity(ChatParticipantModel.KIND, userID, ChatDao.toKey(chatID));
    return new ChatParticipantModel(entity).setCreatedTimestampMillis(clock.millis())
        .setMostRecentObservedMessageID(mostRecentObservedMessageID).save(txn, datastore);
  }

  public static final class ChatParticipantModel
      extends DatastoreModel<ChatParticipant, ChatParticipantModel> {

    private static final String KIND = "ChatParticipant";

    private static final class Columns {
      static final String CreatedTimestampMillis = "CreatedTimestampMillis";
      static final String MostRecentObservedMessageID = "MostRecentObservedMessageID";
    }

    private ChatParticipantModel(Entity entity) {
      super(entity);
    }

    @Override
    public ChatParticipant toProto() {
      return ChatParticipant.newBuilder().setChatID(getChatID()).setUserID(getUserID())
          .setCreatedTimestampMillis(getCreatedTimestampMillis())
          .setMostRecentObservedMessageID(getMostRecentObservedMessageID()).build();
    }

    public String getChatID() {
      return getKey().getParent().getName();
    }

    public String getUserID() {
      return getID();
    }

    public ChatParticipantModel setCreatedTimestampMillis(long timestampMillis) {
      entity.setProperty(Columns.CreatedTimestampMillis, timestampMillis);
      return this;
    }

    public long getCreatedTimestampMillis() {
      return (long) entity.getProperty(Columns.CreatedTimestampMillis);
    }

    public ChatParticipantModel setMostRecentObservedMessageID(long messageID) {
      entity.setUnindexedProperty(Columns.MostRecentObservedMessageID, messageID);
      return this;
    }

    public long getMostRecentObservedMessageID() {
      return (Long) entity.getProperty(Columns.MostRecentObservedMessageID);
    }
  }

  static Key toKey(String chatID, String userID) {
    return KeyFactory.createKey(ChatDao.toKey(chatID), ChatParticipantModel.KIND, userID);
  }
}
