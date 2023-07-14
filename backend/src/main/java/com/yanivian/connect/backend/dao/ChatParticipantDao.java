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
import com.google.appengine.api.datastore.Transaction;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.yanivian.connect.backend.proto.key.ChatParticipantKey;
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

  public ImmutableMap<ChatParticipantKey, ChatParticipantModel> getChatParticipants(Transaction txn,
      ImmutableCollection<ChatParticipantKey> keys) {
    ImmutableList<Key> entityKeys =
        keys.stream().map(key -> toKey(key.getChatID(), key.getUserID())).distinct()
            .collect(ImmutableList.toImmutableList());
    return datastore.get(txn, entityKeys).values().stream().map(ChatParticipantModel::new)
        .collect(ImmutableMap.toImmutableMap(ChatParticipantModel::getChatParticipantKey,
            Function.identity()));
  }

  public ChatParticipantModel newChatParticipant(String chatID, String userID) {
    Entity entity = new Entity(ChatParticipantModel.KIND, userID, ChatDao.toKey(chatID));
    return new ChatParticipantModel(entity).setCreatedTimestampMillis(clock.millis());
  }

  public ChatParticipantModel getOrNewParticipant(Transaction txn, String userID, String chatID) {
    return getChatParticipant(txn, chatID, userID).orElse(newChatParticipant(chatID, userID));
  }

  public static final class ChatParticipantModel
      extends DatastoreModel<ChatParticipant, ChatParticipantModel> {

    private static final String KIND = "ChatParticipant";

    private static final class Columns {
      static final String CreatedTimestampMillis = "CreatedTimestampMillis";
      static final String MostRecentObservedMessageID = "MostRecentObservedMessageID";
      static final String DraftText = "DraftText";
    }

    private ChatParticipantModel(Entity entity) {
      super(entity);
    }

    @Override
    public ChatParticipant toProto() {
      ChatParticipant.Builder chatParticipant = ChatParticipant.newBuilder().setChatID(getChatID())
          .setUserID(getUserID()).setCreatedTimestampMillis(getCreatedTimestampMillis());
      getMostRecentObservedMessageID().ifPresent(chatParticipant::setMostRecentObservedMessageID);
      getDraftText().ifPresent(chatParticipant::setDraftText);
      return chatParticipant.build();
    }

    public String getChatID() {
      return getKey().getParent().getName();
    }

    public String getUserID() {
      return getID();
    }

    public ChatParticipantKey getChatParticipantKey() {
      return toChatParticipantKey(getChatID(), getUserID());
    }

    public ChatParticipantModel setCreatedTimestampMillis(long timestampMillis) {
      entity.setProperty(Columns.CreatedTimestampMillis, timestampMillis);
      return this;
    }

    public long getCreatedTimestampMillis() {
      return (long) entity.getProperty(Columns.CreatedTimestampMillis);
    }

    public ChatParticipantModel setMostRecentObservedMessageID(long value) {
      entity.setUnindexedProperty(Columns.MostRecentObservedMessageID, value);
      return this;
    }

    public Optional<Long> getMostRecentObservedMessageID() {
      return getOptionalProperty(Columns.MostRecentObservedMessageID);
    }

    public ChatParticipantModel setDraftText(Optional<String> value) {
      if (value.isPresent()) {
        entity.setUnindexedProperty(Columns.DraftText, value.get());
      } else {
        entity.removeProperty(Columns.DraftText);
      }
      return this;
    }

    public Optional<String> getDraftText() {
      return getOptionalProperty(Columns.MostRecentObservedMessageID);
    }
  }

  public static ChatParticipantKey toChatParticipantKey(String chatID, String userID) {
    return ChatParticipantKey.newBuilder().setChatID(chatID).setUserID(userID).build();
  }

  static Key toKey(String chatID, String userID) {
    return KeyFactory.createKey(ChatDao.toKey(chatID), ChatParticipantModel.KIND, userID);
  }
}
