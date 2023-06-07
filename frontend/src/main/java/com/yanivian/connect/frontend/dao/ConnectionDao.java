package com.yanivian.connect.frontend.dao;

import java.time.Clock;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import javax.inject.Inject;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.CompositeFilterOperator;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.api.datastore.Transaction;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.yanivian.connect.frontend.proto.model.Connection;
import com.yanivian.connect.frontend.proto.model.Connection.ConnectionState;

public final class ConnectionDao {

  private final DatastoreService datastore;
  private final Clock clock;

  @Inject
  ConnectionDao(DatastoreService datastore, Clock clock) {
    this.datastore = datastore;
    this.clock = clock;
  }

  /** Creates or updates a connection. */
  public ConnectionModel createOrUpdateConnection(Transaction txn, String ownerUserID,
      String targetUserID, ConnectionState state) {
    Query.Filter filter = new Query.CompositeFilter(CompositeFilterOperator.AND,
        ImmutableList.of(
            new Query.FilterPredicate(ConnectionModel.PROPERTY_OWNER_USER_ID,
                Query.FilterOperator.EQUAL, ownerUserID),
            new Query.FilterPredicate(ConnectionModel.PROPERTY_TARGET_USER_ID,
                Query.FilterOperator.EQUAL, targetUserID)));
    Entity entity =
        datastore.prepare(txn, new Query(ConnectionModel.KIND).setFilter(filter)).asSingleEntity();
    if (entity != null) {
      // Update the existing connection.
      ConnectionModel connectionModel = new ConnectionModel(entity);
      if (connectionModel.getState().equals(state)) {
        // Since the state is current, an update is redundant and therefore skipped.
        return connectionModel;
      }
      return connectionModel.setState(state).setLastUpdatedTimestampMillis(clock.millis()).save(txn,
          datastore);
    }
    // Create a new connection.
    entity = new Entity(ConnectionModel.KIND, UUID.randomUUID().toString());
    return new ConnectionModel(entity).setOwnerUserID(ownerUserID).setTargetUserID(targetUserID)
        .setState(state).setCreatedTimestampMillis(clock.millis()).save(txn, datastore);
  }

  /** Deletes the given connection. */
  public void deleteConnection(Transaction txn, ConnectionModel connectionModel) {
    datastore.delete(txn, connectionModel.getKey());
  }

  /** Fetches all connections originating from an user. */
  public ImmutableList<ConnectionModel> listConnectionsForOwner(String ownerUserID) {
    Query query = new Query(ConnectionModel.KIND).setFilter(new FilterPredicate(
        ConnectionModel.PROPERTY_OWNER_USER_ID, FilterOperator.EQUAL, ownerUserID));
    return Streams.stream(datastore.prepare(query).asIterable()).map(ConnectionModel::new)
        .collect(ImmutableList.toImmutableList());
  }

  /** Fetches all connections targeting an user. */
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
      return (ConnectionState) entity.getProperty(PROPERTY_STATE);
    }

    private ConnectionModel setState(ConnectionState state) {
      entity.setProperty(PROPERTY_STATE, state);
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
