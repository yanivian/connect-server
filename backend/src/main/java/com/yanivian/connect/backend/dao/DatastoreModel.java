package com.yanivian.connect.backend.dao;

import java.time.Clock;
import java.util.Optional;
import java.util.OptionalLong;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.Transaction;

/**
 * Base datastore model that abstracts created and last modified timestamp management, and provides
 * helper methods to access required and optional properties to implementing classes.
 */
public abstract class DatastoreModel<DM extends DatastoreModel<DM>> {

  protected static final class CommonColumns {
    static final String CreatedTimestampMillis = "CreatedTimestampMillis";
    static final String LastUpdatedTimestampMillis = "LastUpdatedTimestampMillis";
  }

  protected final Entity entity;

  protected DatastoreModel(Entity entity) {
    this.entity = entity;
  }

  /** Saves any changes made to the entity to Datastore. */
  @SuppressWarnings("unchecked")
  public final DM save(Transaction txn, DatastoreService datastore, Clock clock) {
    long timestampMillis = clock.millis();
    if (entity.hasProperty(CommonColumns.CreatedTimestampMillis)) {
      setLastUpdatedTimestampMillis(timestampMillis);
    } else {
      setCreatedTimestampMillis(timestampMillis);
    }
    datastore.put(txn, entity);
    return (DM) this;
  }

  /** Returns the entity key. */
  final Key getKey() {
    return entity.getKey();
  }

  @SuppressWarnings("unchecked")
  private DM setCreatedTimestampMillis(long timestampMillis) {
    entity.setProperty(CommonColumns.CreatedTimestampMillis, timestampMillis);
    return (DM) this;
  }

  @SuppressWarnings("unchecked")
  private DM setLastUpdatedTimestampMillis(long timestampMillis) {
    entity.setProperty(CommonColumns.LastUpdatedTimestampMillis, timestampMillis);
    return (DM) this;
  }

  private long getCreatedTimestampMillis() {
    return (long) entity.getProperty(CommonColumns.CreatedTimestampMillis);
  }

  private OptionalLong getLastUpdatedTimestampMillis() {
    Optional<Long> value = getOptionalProperty(CommonColumns.LastUpdatedTimestampMillis);
    return value.isPresent() ? OptionalLong.of(value.get()) : OptionalLong.empty();
  }

  public long getTimestampMillis() {
    return getLastUpdatedTimestampMillis().orElseGet(this::getCreatedTimestampMillis);
  }

  @SuppressWarnings("unchecked")
  protected <T> Optional<T> getOptionalProperty(String propertyName) {
    T property = (T) entity.getProperty(propertyName);
    return entity.hasProperty(propertyName) ? Optional.of(property) : Optional.empty();
  }

  @SuppressWarnings("unchecked")
  protected <T> DM setOptionalProperty(String propertyName, Optional<T> value) {
    if (value.isPresent()) {
      entity.setProperty(propertyName, value.get());
    } else {
      entity.removeProperty(propertyName);
    }
    return (DM) this;
  }
}
