package com.yanivian.connect.customer.dao;

import java.time.Clock;
import java.util.Optional;
import javax.inject.Inject;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.yanivian.connect.customer.proto.model.Profile;

public final class ProfileDao {

  private final DatastoreService datastore;
  private final Clock clock;


  @Inject
  ProfileDao(DatastoreService datastore, Clock clock) {
    this.datastore = datastore;
    this.clock = clock;
  }

  public Optional<ProfileModel> getProfileByUserId(String userId) {
    Query query = new Query(ProfileModel.KIND).setFilter(
        new FilterPredicate(Entity.KEY_RESERVED_PROPERTY, FilterOperator.EQUAL, toKey(userId)));
    Entity entity = datastore.prepare(query).asSingleEntity();
    return entity == null ? Optional.empty() : Optional.of(new ProfileModel(entity));
  }

  public Optional<ProfileModel> getProfileByPhoneNumber(String phoneNumber) {
    Query query = new Query(ProfileModel.KIND).setFilter(
        new FilterPredicate(ProfileModel.PROPERTY_PHONE_NUMBER, FilterOperator.EQUAL, phoneNumber));
    Entity entity = datastore.prepare(query).asSingleEntity();
    return entity == null ? Optional.empty() : Optional.of(new ProfileModel(entity));
  }

  /**
   * Creates, saves and returns a new profile.
   * 
   * @param profile Creation and last update timestamps are ignored
   * @return the saved profile model
   */
  public ProfileModel createProfile(Profile profile) {
    Entity entity = new Entity(ProfileModel.KIND, profile.getUserID());
    ProfileModel model = new ProfileModel(entity).setPhoneNumber(profile.getPhoneNumber())
        .setCreatedTimestampMillis(clock.millis());
    if (profile.hasName()) {
      model.setName(profile.getName());
    }
    if (profile.hasEmailAddress()) {
      model.setEmailAddress(profile.getEmailAddress());
    }
    return model.save();
  }

  public static Key toKey(String userId) {
    return KeyFactory.createKey(ProfileModel.KIND, userId);
  }

  public final class ProfileModel {

    public static final String KIND = "Profile";
    private static final String PROPERTY_PHONE_NUMBER = "PhoneNumber";
    private static final String PROPERTY_CREATED_TIMESTAMP_MILLIS = "CreatedTimestampMillis";
    private static final String PROPERTY_NAME = "Name";
    private static final String PROPERTY_EMAIL_ADDRESS = "EmailAddress";
    private static final String PROPERTY_LAST_UPDATED_TIMESTAMP_MILLIS =
        "LastUpdatedTimestampMillis";

    private final Entity entity;

    private ProfileModel(Entity entity) {
      this.entity = entity;
    }

    public Key getKey() {
      return entity.getKey();
    }

    public ProfileModel save() {
      datastore.put(entity);
      return this;
    }

    public Profile toProto() {
      Profile.Builder profile = Profile.newBuilder().setUserID(getUserID())
          .setPhoneNumber(getPhoneNumber()).setCreatedTimestampMillis(getCreatedTimestampMillis());
      getName().ifPresent(profile::setName);
      getEmailAddress().ifPresent(profile::setEmailAddress);
      getLastUpdatedTimestampMillis().ifPresent(profile::setLastUpdatedTimestampMillis);
      return profile.build();
    }

    public String getUserID() {
      return getKey().getName();
    }

    public ProfileModel setPhoneNumber(String phoneNumber) {
      entity.setIndexedProperty(PROPERTY_PHONE_NUMBER, phoneNumber);
      return this;
    }

    public String getPhoneNumber() {
      return (String) entity.getProperty(PROPERTY_PHONE_NUMBER);
    }

    public ProfileModel setCreatedTimestampMillis(long timestampMillis) {
      entity.setProperty(PROPERTY_CREATED_TIMESTAMP_MILLIS, timestampMillis);
      return this;
    }

    public long getCreatedTimestampMillis() {
      return (long) entity.getProperty(PROPERTY_CREATED_TIMESTAMP_MILLIS);
    }

    public ProfileModel setName(String name) {
      entity.setProperty(PROPERTY_NAME, name);
      return this;
    }

    public Optional<String> getName() {
      return getOptionalProperty(PROPERTY_NAME);
    }

    public ProfileModel setEmailAddress(String emailAddress) {
      entity.setProperty(PROPERTY_EMAIL_ADDRESS, emailAddress);
      return this;
    }

    public Optional<String> getEmailAddress() {
      return getOptionalProperty(PROPERTY_EMAIL_ADDRESS);
    }

    public ProfileModel setLastUpdatedTimestampMillis(long timestampMillis) {
      entity.setProperty(PROPERTY_LAST_UPDATED_TIMESTAMP_MILLIS, timestampMillis);
      return this;
    }

    public Optional<Long> getLastUpdatedTimestampMillis() {
      return getOptionalProperty(PROPERTY_LAST_UPDATED_TIMESTAMP_MILLIS);
    }

    @SuppressWarnings("unchecked")
    private <T> Optional<T> getOptionalProperty(String propertyName) {
      return entity.hasProperty(propertyName) ? Optional.of((T) entity.getProperty(propertyName))
          : Optional.empty();
    }
  }
}
