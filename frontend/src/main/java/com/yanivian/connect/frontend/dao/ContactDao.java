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
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.yanivian.connect.frontend.proto.model.Contact;

public final class ContactDao {

  private final DatastoreService datastore;
  private final Clock clock;

  @Inject
  ContactDao(DatastoreService datastore, Clock clock) {
    this.datastore = datastore;
    this.clock = clock;
  }

  /** Creates or updates a contact transactionally. */
  public ContactModel createOrUpdateContact(String ownerUserID, String phoneNumber, String name) {
    return DatastoreUtil.newTransaction(datastore, txn -> {
      // Re-use an existing entity if possible.
      Query.Filter filter = new Query.CompositeFilter(CompositeFilterOperator.AND,
          ImmutableList.of(
              new Query.FilterPredicate(ContactModel.PROPERTY_OWNER_USER_ID,
                  Query.FilterOperator.EQUAL, ownerUserID),
              new Query.FilterPredicate(ContactModel.PROPERTY_PHONE_NUMBER,
                  Query.FilterOperator.EQUAL, phoneNumber)));
      Entity entity =
          datastore.prepare(txn, new Query(ContactModel.KIND).setFilter(filter)).asSingleEntity();
      if (entity != null) {
        // Update the existing contact.
        ContactModel contactModel = new ContactModel(entity);
        return contactModel.setName(name).setLastUpdatedTimestampMillis(clock.millis()).save(txn,
            datastore);
      }
      // Create a new contact.
      entity = new Entity(ContactModel.KIND, UUID.randomUUID().toString());
      return new ContactModel(entity).setOwnerUserID(ownerUserID)
          .setCreatedTimestampMillis(clock.millis()).setName(name).setPhoneNumber(phoneNumber)
          .save(txn, datastore);
    });
  }

  /**
   * Transactionally deletes a contact by its unique ID, ensuring that the provided actor is the
   * owner, returning {@code true} on success. Returns {@code false} if a contact with the given ID
   * doesn't exist.
   */
  public boolean deleteContact(String contactID, String actorID) {
    return DatastoreUtil.newTransaction(datastore, txn -> {
      // Fetch the contact.
      Query query = new Query(ContactModel.KIND).setFilter(new Query.FilterPredicate(
          Entity.KEY_RESERVED_PROPERTY, Query.FilterOperator.EQUAL, toKey(contactID)));
      Entity entity = datastore.prepare(query).asSingleEntity();
      if (entity == null) {
        return false;
      }
      ContactModel contactModel = new ContactModel(entity);
      Preconditions.checkState(contactModel.getOwnerUserID().equals(actorID));
      datastore.delete(txn, toKey(contactID));
      return true;
    });
  }

  /** Fetches all contacts owned by a given user. */
  public ImmutableList<ContactModel> listContactsOwnedBy(Transaction txn, String ownerUserID) {
    Query query = new Query(ContactModel.KIND).setFilter(new FilterPredicate(
        ContactModel.PROPERTY_OWNER_USER_ID, FilterOperator.EQUAL, ownerUserID));
    return Streams.stream(datastore.prepare(txn, query).asIterable()).map(ContactModel::new)
        .collect(ImmutableList.toImmutableList());
  }

  /** Fetches all contacts that reference a given phone number. */
  public ImmutableList<ContactModel> listContactsTargetingPhoneNumber(Transaction txn,
      String phoneNumber) {
    Query query = new Query(ContactModel.KIND).setFilter(
        new FilterPredicate(ContactModel.PROPERTY_PHONE_NUMBER, FilterOperator.EQUAL, phoneNumber));
    return Streams.stream(datastore.prepare(txn, query).asIterable()).map(ContactModel::new)
        .collect(ImmutableList.toImmutableList());
  }

  public static class ContactModel extends DatastoreModel<Contact, ContactModel> {
    static final String KIND = "Contact";
    private static final String PROPERTY_OWNER_USER_ID = "OwnerUserID";
    private static final String PROPERTY_CREATED_TIMESTAMP_MILLIS = "CreatedTimestampMillis";
    private static final String PROPERTY_NAME = "Name";
    private static final String PROPERTY_PHONE_NUMBER = "PhoneNumber";
    private static final String PROPERTY_LAST_UPDATED_TIMESTAMP_MILLIS =
        "LastUpdatedTimestampMillis";

    private ContactModel(Entity entity) {
      super(entity);
    }

    @Override
    public Contact toProto() {
      Contact.Builder contact = Contact.newBuilder().setID(getID()).setOwnerUserID(getOwnerUserID())
          .setCreatedTimestampMillis(getCreatedTimestampMillis()).setName(getName())
          .setPhoneNumber((getPhoneNumber()));
      getLastUpdatedTimestampMillis().ifPresent(contact::setLastUpdatedTimestampMillis);
      return contact.build();
    }

    public String getOwnerUserID() {
      return (String) entity.getProperty(PROPERTY_OWNER_USER_ID);
    }

    private ContactModel setOwnerUserID(String ownerUserID) {
      entity.setProperty(PROPERTY_OWNER_USER_ID, ownerUserID);
      return this;
    }

    public long getCreatedTimestampMillis() {
      return (long) entity.getProperty(PROPERTY_CREATED_TIMESTAMP_MILLIS);
    }

    private ContactModel setCreatedTimestampMillis(long timestampMillis) {
      entity.setProperty(PROPERTY_CREATED_TIMESTAMP_MILLIS, timestampMillis);
      return this;
    }

    public String getName() {
      return (String) entity.getProperty(PROPERTY_NAME);
    }

    private ContactModel setName(String name) {
      entity.setProperty(PROPERTY_NAME, name);
      return this;
    }

    public String getPhoneNumber() {
      return (String) entity.getProperty(PROPERTY_PHONE_NUMBER);
    }

    private ContactModel setPhoneNumber(String phoneNumber) {
      entity.setProperty(PROPERTY_PHONE_NUMBER, phoneNumber);
      return this;
    }

    public OptionalLong getLastUpdatedTimestampMillis() {
      Optional<Long> value = getOptionalProperty(PROPERTY_LAST_UPDATED_TIMESTAMP_MILLIS);
      return value.isPresent() ? OptionalLong.of(value.get()) : OptionalLong.empty();
    }

    public ContactModel setLastUpdatedTimestampMillis(long timestampMillis) {
      entity.setProperty(PROPERTY_LAST_UPDATED_TIMESTAMP_MILLIS, timestampMillis);
      return this;
    }
  }

  static Key toKey(String contactID) {
    return KeyFactory.createKey(ContactModel.KIND, contactID);
  }
}
