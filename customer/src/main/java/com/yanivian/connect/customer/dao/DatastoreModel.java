package com.yanivian.connect.customer.dao;

import java.util.Optional;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.Entity;
import com.google.protobuf.Message;

public abstract class DatastoreModel<M extends Message, DM extends DatastoreModel<M, DM>> {

  protected final Entity entity;

  private final DatastoreService datastore;

  protected DatastoreModel(Entity entity, DatastoreService datastore) {
    this.entity = entity;
    this.datastore = datastore;
  }

  /** Saves any changes made to the entity to Datastore. */
  @SuppressWarnings("unchecked")
  public final DM save() {
    datastore.put(entity);
    return (DM) this;
  }

  /** Returns the name associated with the entity key. */
  public final String getID() {
    return entity.getKey().getName();
  }

  /** Returns a protobuf model representing the underlying entity. */
  public abstract M toProto();

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
