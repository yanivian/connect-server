package com.yanivian.connect.backend.dao;

import java.time.Clock;
import java.util.Optional;
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
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.yanivian.connect.backend.proto.model.Contact;

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
    Optional<Key> contactKey = findContact(ownerUserID, phoneNumber).map(ContactModel::getKey);
    return DatastoreUtil.newTransaction(datastore, txn -> {
      Optional<ContactModel> contactModel = contactKey.flatMap(key -> getContact(txn, key));
      if (contactModel.isPresent()) {
        // Update the existing contact.
        return contactModel.get().setName(name).save(txn, datastore, clock);
      }
      // Create a new contact.
      Entity entity = new Entity(ContactModel.KIND, UUID.randomUUID().toString());
      return new ContactModel(entity).setOwnerUserID(ownerUserID).setName(name)
          .setPhoneNumber(phoneNumber).save(txn, datastore, clock);
    });
  }

  // Cannot be transactional.
  private Optional<ContactModel> findContact(String ownerUserID, String phoneNumber) {
    Query.Filter filter = new Query.CompositeFilter(CompositeFilterOperator.AND,
        ImmutableList.of(
            new Query.FilterPredicate(ContactModel.PROPERTY_OWNER_USER_ID,
                Query.FilterOperator.EQUAL, ownerUserID),
            new Query.FilterPredicate(ContactModel.PROPERTY_PHONE_NUMBER,
                Query.FilterOperator.EQUAL, phoneNumber)));
    Entity entity =
        datastore.prepare(new Query(ContactModel.KIND).setFilter(filter)).asSingleEntity();
    return (entity == null) ? Optional.empty() : Optional.of(new ContactModel(entity));
  }

  private Optional<ContactModel> getContact(Transaction txn, Key key) {
    try {
      Entity entity = datastore.get(txn, key);
      return Optional.of(new ContactModel(entity));
    } catch (EntityNotFoundException enfe) {
      return Optional.empty();
    }
  }

  /**
   * Transactionally deletes a contact by its unique ID, ensuring that the provided actor is the
   * owner, returning {@code true} on success. Returns {@code false} if a contact with the given ID
   * doesn't exist.
   */
  public boolean deleteContact(String contactID, String actorID) {
    return DatastoreUtil.newTransaction(datastore, txn -> {
      // Fetch the contact.
      Key key = toKey(contactID);
      Optional<ContactModel> optionalContactModel = getContact(txn, key);
      if (!optionalContactModel.isPresent()) {
        return false;
      }
      ContactModel contactModel = optionalContactModel.get();
      Preconditions.checkState(contactModel.getOwnerUserID().equals(actorID));
      datastore.delete(txn, key);
      return true;
    });
  }

  /** Fetches all contacts owned by a given user. */
  // Cannot be transactional.
  public ImmutableList<ContactModel> listContactsOwnedBy(String ownerUserID) {
    Query query = new Query(ContactModel.KIND).setFilter(new FilterPredicate(
        ContactModel.PROPERTY_OWNER_USER_ID, FilterOperator.EQUAL, ownerUserID));
    return Streams.stream(datastore.prepare(query).asIterable()).map(ContactModel::new)
        .collect(ImmutableList.toImmutableList());
  }

  /** Fetches all contacts that reference a given phone number. */
  // Cannot be transactional.
  public ImmutableList<ContactModel> listContactsTargetingPhoneNumber(String phoneNumber) {
    Query query = new Query(ContactModel.KIND).setFilter(
        new FilterPredicate(ContactModel.PROPERTY_PHONE_NUMBER, FilterOperator.EQUAL, phoneNumber));
    return Streams.stream(datastore.prepare(query).asIterable()).map(ContactModel::new)
        .collect(ImmutableList.toImmutableList());
  }

  public static class ContactModel extends DatastoreModel<ContactModel> {
    static final String KIND = "Contact";
    private static final String PROPERTY_OWNER_USER_ID = "OwnerUserID";
    private static final String PROPERTY_NAME = "Name";
    private static final String PROPERTY_PHONE_NUMBER = "PhoneNumber";

    private ContactModel(Entity entity) {
      super(entity);
    }

    /** Returns a protobuf model representing the underlying entity. */
    public Contact toProto() {
      return Contact.newBuilder().setID(getID()).setOwnerUserID(getOwnerUserID())
          .setTimestampMillis(getTimestampMillis()).setName(getName())
          .setPhoneNumber((getPhoneNumber())).build();
    }

    public String getID() {
      return getKey().getName();
    }

    public String getOwnerUserID() {
      return (String) entity.getProperty(PROPERTY_OWNER_USER_ID);
    }

    private ContactModel setOwnerUserID(String ownerUserID) {
      entity.setProperty(PROPERTY_OWNER_USER_ID, ownerUserID);
      return this;
    }

    public String getName() {
      return (String) entity.getProperty(PROPERTY_NAME);
    }

    private ContactModel setName(String name) {
      entity.setUnindexedProperty(PROPERTY_NAME, name);
      return this;
    }

    public String getPhoneNumber() {
      return (String) entity.getProperty(PROPERTY_PHONE_NUMBER);
    }

    private ContactModel setPhoneNumber(String phoneNumber) {
      entity.setProperty(PROPERTY_PHONE_NUMBER, phoneNumber);
      return this;
    }
  }

  static Key toKey(String contactID) {
    return KeyFactory.createKey(ContactModel.KIND, contactID);
  }
}
