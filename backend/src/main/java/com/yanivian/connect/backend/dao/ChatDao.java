package com.yanivian.connect.backend.dao;

import java.time.Clock;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.inject.Inject;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.api.datastore.Transaction;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.yanivian.connect.backend.proto.model.Chat;

public final class ChatDao {

  private final DatastoreService datastore;
  private final Clock clock;

  @Inject
  ChatDao(DatastoreService datastore, Clock clock) {
    this.datastore = datastore;
    this.clock = clock;
  }

  public Optional<ChatModel> getChat(Transaction txn, String chatID) {
    try {
      Entity entity = datastore.get(txn, toKey(chatID));
      return Optional.of(new ChatModel(entity));
    } catch (EntityNotFoundException enfe) {
      return Optional.empty();
    }
  }

  public ImmutableMap<String, ChatModel> getChats(Transaction txn,
      ImmutableCollection<String> chatIDs) {
    ImmutableList<Key> entityKeys =
        chatIDs.stream().map(ChatDao::toKey).distinct().collect(ImmutableList.toImmutableList());
    return datastore.get(txn, entityKeys).values().stream().map(ChatModel::new)
        .collect(ImmutableMap.toImmutableMap(ChatModel::getID, Function.identity()));
  }

  public ChatModel createChat(Transaction txn, ImmutableSet<String> participantUserIDs) {
    Entity entity = new Entity(ChatModel.KIND, UUID.randomUUID().toString());
    return new ChatModel(entity).setCreatedTimestampMillis(clock.millis())
        .setParticipantUserIDs(participantUserIDs)
        .setUniqueParticipantsSearchKey(toUniqueParticipantsSearchKey(participantUserIDs))
        .setMostRecentMessageID(1L).save(txn, datastore);
  }

  /** Find the chat associated with the given participants. */
  // Cannot be transactional.
  public Optional<ChatModel> findChatByParticipants(ImmutableSet<String> participantUserIDs) {
    Query query = new Query(ChatModel.KIND)
        .setFilter(new FilterPredicate(ChatModel.Columns.UniqueParticipantsSearchKey,
            FilterOperator.EQUAL, toUniqueParticipantsSearchKey(participantUserIDs)));
    return Optional.ofNullable(datastore.prepare(query).asSingleEntity()).map(ChatModel::new);
  }

  /** Lists chats with the given participant. */
  // Cannot be transactional.
  public ImmutableList<ChatModel> listChatsByParticipant(String userID) {
    Query query = new Query(ChatModel.KIND).setFilter(
        new FilterPredicate(ChatModel.Columns.ParticipantUserIDs, FilterOperator.EQUAL, userID));
    return Streams.stream(datastore.prepare(query).asIterable()).map(ChatModel::new)
        .collect(ImmutableList.toImmutableList());
  }

  public static final class ChatModel extends DatastoreModel<Chat, ChatModel> {

    private static final String KIND = "Chat";

    private static final class Columns {
      static final String CreatedTimestampMillis = "CreatedTimestampMillis";
      static final String ParticipantUserIDs = "ParticipantUserIDs";
      static final String UniqueParticipantsSearchKey = "UniqueParticipantsSearchKey";
      static final String MostRecentMessageID = "MostRecentMessageID";
      static final String TypingUserIDs = "TypingUserIDs";
    }

    private ChatModel(Entity entity) {
      super(entity);
    }

    @Override
    public Chat toProto() {
      return Chat.newBuilder().setID(getID()).addAllParticipantUserIDs(getParticipantUserIDs())
          .setUniqueParticipantsSearchKey(getUniqueParticipantsSearchKey())
          .setMostRecentMessageID(getMostRecentMessageID()).addAllTypingUserIDs(getTypingUserIDs())
          .build();
    }

    private ChatModel setCreatedTimestampMillis(long timestampMillis) {
      entity.setProperty(Columns.CreatedTimestampMillis, timestampMillis);
      return this;
    }

    public long getCreatedTimestampMillis() {
      return (long) entity.getProperty(Columns.CreatedTimestampMillis);
    }

    private ChatModel setParticipantUserIDs(Collection<String> participantUserIDs) {
      Preconditions.checkState(!participantUserIDs.isEmpty());
      entity.setIndexedProperty(Columns.ParticipantUserIDs, participantUserIDs);
      return this;
    }

    @SuppressWarnings("unchecked")
    public Collection<String> getParticipantUserIDs() {
      return (Collection<String>) entity.getProperty(Columns.ParticipantUserIDs);
    }

    private ChatModel setUniqueParticipantsSearchKey(String key) {
      entity.setIndexedProperty(Columns.UniqueParticipantsSearchKey, key);
      return this;
    }

    String getUniqueParticipantsSearchKey() {
      return (String) entity.getProperty(Columns.UniqueParticipantsSearchKey);
    }

    public ChatModel setMostRecentMessageID(long messageID) {
      entity.setIndexedProperty(Columns.MostRecentMessageID, messageID);
      return this;
    }

    public long getMostRecentMessageID() {
      return (Long) entity.getProperty(Columns.MostRecentMessageID);
    }

    public ChatModel addTypingUserID(String userID) {
      Set<String> typingUserIDs = new HashSet<>(getTypingUserIDs());
      typingUserIDs.add(userID);
      return setTypingUserIDs(typingUserIDs);
    }

    public ChatModel removeTypingUserID(String userID) {
      Set<String> typingUserIDs = new HashSet<>(getTypingUserIDs());
      typingUserIDs.remove(userID);
      return setTypingUserIDs(typingUserIDs);
    }

    private ChatModel setTypingUserIDs(Collection<String> typingUserIDs) {
      if (typingUserIDs.isEmpty()) {
        entity.removeProperty(Columns.TypingUserIDs);
      } else {
        entity.setUnindexedProperty(Columns.TypingUserIDs, typingUserIDs);
      }
      return this;
    }

    @SuppressWarnings("unchecked")
    public Collection<String> getTypingUserIDs() {
      return (Collection<String>) getOptionalProperty(Columns.TypingUserIDs)
          .orElse(Collections.EMPTY_LIST);
    }
  }

  static Key toKey(String chatID) {
    return KeyFactory.createKey(ChatModel.KIND, chatID);
  }

  static String toUniqueParticipantsSearchKey(ImmutableSet<String> participantUserIDs) {
    Preconditions.checkState(!participantUserIDs.isEmpty());
    return participantUserIDs.stream().sorted().collect(Collectors.joining("#"));
  }
}
