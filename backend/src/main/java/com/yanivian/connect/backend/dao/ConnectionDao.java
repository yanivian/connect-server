package com.yanivian.connect.backend.dao;

import java.time.Clock;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import javax.inject.Inject;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.CompositeFilterOperator;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.api.datastore.Transaction;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.yanivian.connect.backend.proto.model.Connection;
import com.yanivian.connect.backend.proto.model.Connection.ConnectionState;

public final class ConnectionDao {

  private final DatastoreService datastore;
  private final Clock clock;

  @Inject
  ConnectionDao(DatastoreService datastore, Clock clock) {
    this.datastore = datastore;
    this.clock = clock;
  }

  public ConnectionModel createConnection(Transaction txn, String ownerUserID, String targetUserID,
      ConnectionState state) {
    Entity entity = new Entity(ConnectionModel.KIND, UUID.randomUUID().toString());
    return new ConnectionModel(entity).setOwnerUserID(ownerUserID).setTargetUserID(targetUserID)
        .setState(state).setCreatedTimestampMillis(clock.millis()).save(txn, datastore);
  }

  public ConnectionModel updateConnection(Transaction txn, ConnectionModel connection,
      ConnectionState state) {
    // Fetch the connection transactionally.
    ConnectionModel connectionModel =
        getConnection(txn, connection.getKey()).orElseThrow(IllegalStateException::new);
    // Don't update if already in the desired state.
    if (connectionModel.getState().equals(state)) {
      return connectionModel;
    }
    // Update the connection.
    return connectionModel.setState(state).setLastUpdatedTimestampMillis(clock.millis()).save(txn,
        datastore);
  }

  // Cannot be transactional.
  public Optional<ConnectionModel> findConnection(String ownerUserID, String targetUserID) {
    Query.Filter filter = new Query.CompositeFilter(CompositeFilterOperator.AND,
        ImmutableList.of(
            new Query.FilterPredicate(ConnectionModel.PROPERTY_OWNER_USER_ID,
                Query.FilterOperator.EQUAL, ownerUserID),
            new Query.FilterPredicate(ConnectionModel.PROPERTY_TARGET_USER_ID,
                Query.FilterOperator.EQUAL, targetUserID)));
    Entity entity =
        datastore.prepare(new Query(ConnectionModel.KIND).setFilter(filter)).asSingleEntity();
    return (entity == null) ? Optional.empty() : Optional.of(new ConnectionModel(entity));
  }

  public Optional<ConnectionModel> getConnection(Transaction txn, Key key) {
    try {
      return Optional.of(new ConnectionModel(datastore.get(txn, key)));
    } catch (EntityNotFoundException enfe) {
      return Optional.empty();
    }
  }

  /** Deletes the given connection. */
  public void deleteConnection(Transaction txn, ConnectionModel connectionModel) {
    datastore.delete(txn, connectionModel.getKey());
  }

  /** Fetches all connections originating from an user. */
  // Cannot be transactional.
  public ImmutableList<ConnectionModel> listConnectionsForOwner(String ownerUserID) {
    Query query = new Query(ConnectionModel.KIND).setFilter(new FilterPredicate(
        ConnectionModel.PROPERTY_OWNER_USER_ID, FilterOperator.EQUAL, ownerUserID));
    return Streams.stream(datastore.prepare(query).asIterable()).map(ConnectionModel::new)
        .collect(ImmutableList.toImmutableList());
  }

  /** Fetches all connections targeting an user. */
  // Cannot be transactional.
  public ImmutableList<ConnectionModel> listConnectionsForTarget(String targetUserID) {
    Query query = new Query(ConnectionModel.KIND).setFilter(new FilterPredicate(
        ConnectionModel.PROPERTY_TARGET_USER_ID, FilterOperator.EQUAL, targetUserID));
    return Streams.stream(datastore.prepare(query).asIterable()).map(ConnectionModel::new)
        .collect(ImmutableList.toImmutableList());
  }

  public static class ConnectionModel extends DatastoreModel<Connection, ConnectionModel> {
    static final String KIND = "Connection";
    private static final String PROPERTY_OWNER_USER_ID = "OwnerUserID";
    private static final String PROPERTY_TARGET_USER_ID = "TargetUserID";
    private static final String PROPERTY_CREATED_TIMESTAMP_MILLIS = "CreatedTimestampMillis";
    private static final String PROPERTY_STATE = "State";
    private static final String PROPERTY_LAST_UPDATED_TIMESTAMP_MILLIS =
        "LastUpdatedTimestampMillis";

    private ConnectionModel(Entity entity) {
      super(entity);
    }

    @Override
    public Connection toProto() {
      Connection.Builder connection = Connection.newBuilder().setID(getID())
          .setOwnerUserID(getOwnerUserID()).setTargetUserID(getTargetUserID()).setState(getState())
          .setCreatedTimestampMillis(getCreatedTimestampMillis());
      getLastUpdatedTimestampMillis().ifPresent(connection::setLastUpdatedTimestampMillis);
      return connection.build();
    }

    public String getOwnerUserID() {
      return (String) entity.getProperty(PROPERTY_OWNER_USER_ID);
    }

    private ConnectionModel setOwnerUserID(String ownerUserID) {
      entity.setProperty(PROPERTY_OWNER_USER_ID, ownerUserID);
      return this;
    }

    public String getTargetUserID() {
      return (String) entity.getProperty(PROPERTY_TARGET_USER_ID);
    }

    private ConnectionModel setTargetUserID(String targetUserID) {
      entity.setProperty(PROPERTY_TARGET_USER_ID, targetUserID);
      return this;
    }

    public ConnectionState getState() {
      return ConnectionState.valueOf(entity.getProperty(PROPERTY_STATE).toString());
    }

    private ConnectionModel setState(ConnectionState state) {
      entity.setProperty(PROPERTY_STATE, state.name());
      return this;
    }

    public long getCreatedTimestampMillis() {
      return (long) entity.getProperty(PROPERTY_CREATED_TIMESTAMP_MILLIS);
    }

    private ConnectionModel setCreatedTimestampMillis(long timestampMillis) {
      entity.setProperty(PROPERTY_CREATED_TIMESTAMP_MILLIS, timestampMillis);
      return this;
    }

    public OptionalLong getLastUpdatedTimestampMillis() {
      Optional<Long> value = getOptionalProperty(PROPERTY_LAST_UPDATED_TIMESTAMP_MILLIS);
      return value.isPresent() ? OptionalLong.of(value.get()) : OptionalLong.empty();
    }

    public ConnectionModel setLastUpdatedTimestampMillis(long timestampMillis) {
      entity.setProperty(PROPERTY_LAST_UPDATED_TIMESTAMP_MILLIS, timestampMillis);
      return this;
    }
  }

  static Key toKey(String connectionID) {
    return KeyFactory.createKey(ConnectionModel.KIND, connectionID);
  }
}
