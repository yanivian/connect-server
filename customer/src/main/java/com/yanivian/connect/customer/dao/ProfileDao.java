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

  public Optional<ProfileModel> getProfileByUserId(String userID) {
    Query query = new Query(ProfileModel.KIND).setFilter(
        new FilterPredicate(Entity.KEY_RESERVED_PROPERTY, FilterOperator.EQUAL, toKey(userID)));
    Entity entity = datastore.prepare(query).asSingleEntity();
    return entity == null ? Optional.empty() : Optional.of(new ProfileModel(entity, datastore));
  }

  public Optional<ProfileModel> getProfileByPhoneNumber(String phoneNumber) {
    Query query = new Query(ProfileModel.KIND).setFilter(
        new FilterPredicate(ProfileModel.PROPERTY_PHONE_NUMBER, FilterOperator.EQUAL, phoneNumber));
    Entity entity = datastore.prepare(query).asSingleEntity();
    return entity == null ? Optional.empty() : Optional.of(new ProfileModel(entity, datastore));
  }

  /** Creates, saves and returns a new profile. */
  public ProfileModel createProfile(String userID, String phoneNumber) {
    Entity entity = new Entity(ProfileModel.KIND, userID);
    return new ProfileModel(entity, datastore).setPhoneNumber(phoneNumber)
        .setCreatedTimestampMillis(clock.millis()).save();
  }

  public final class ProfileModel extends DatastoreModel<Profile, ProfileModel> {

    static final String KIND = "Profile";
    private static final String PROPERTY_PHONE_NUMBER = "PhoneNumber";
    private static final String PROPERTY_CREATED_TIMESTAMP_MILLIS = "CreatedTimestampMillis";
    private static final String PROPERTY_NAME = "Name";
    private static final String PROPERTY_EMAIL_ADDRESS = "EmailAddress";
    private static final String PROPERTY_LAST_UPDATED_TIMESTAMP_MILLIS =
        "LastUpdatedTimestampMillis";
    private static final String PROPERTY_IMAGE = "Image";

    private ProfileModel(Entity entity, DatastoreService datastore) {
      super(entity, datastore);
    }

    @Override
    public Profile toProto() {
      Profile.Builder profile = Profile.newBuilder().setUserID(getID())
          .setPhoneNumber(getPhoneNumber()).setCreatedTimestampMillis(getCreatedTimestampMillis());
      getName().ifPresent(profile::setName);
      getEmailAddress().ifPresent(profile::setEmailAddress);
      getImage().ifPresent(imageID -> profile.getImageBuilder().setID(imageID));
      getLastUpdatedTimestampMillis().ifPresent(profile::setLastUpdatedTimestampMillis);
      return profile.build();
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

    public ProfileModel setName(Optional<String> name) {
      if (name.isPresent()) {
        entity.setProperty(PROPERTY_NAME, name.get());
      } else {
        entity.removeProperty(PROPERTY_NAME);
      }
      return this;
    }

    public Optional<String> getName() {
      return getOptionalProperty(PROPERTY_NAME);
    }

    public ProfileModel setEmailAddress(Optional<String> emailAddress) {
      if (emailAddress.isPresent()) {
        entity.setProperty(PROPERTY_EMAIL_ADDRESS, emailAddress.get());
      } else {
        entity.removeProperty(PROPERTY_EMAIL_ADDRESS);
      }
      return this;
    }

    public Optional<String> getEmailAddress() {
      return getOptionalProperty(PROPERTY_EMAIL_ADDRESS);
    }

    public ProfileModel setImage(Optional<String> image) {
      if (image.isPresent()) {
        entity.setProperty(PROPERTY_IMAGE, image);
      } else {
        entity.removeProperty(PROPERTY_IMAGE);
      }
      return this;
    }

    public Optional<String> getImage() {
      return getOptionalProperty(PROPERTY_IMAGE);
    }

    public ProfileModel setLastUpdatedTimestampMillis(long timestampMillis) {
      entity.setProperty(PROPERTY_LAST_UPDATED_TIMESTAMP_MILLIS, timestampMillis);
      return this;
    }

    public Optional<Long> getLastUpdatedTimestampMillis() {
      return getOptionalProperty(PROPERTY_LAST_UPDATED_TIMESTAMP_MILLIS);
    }
  }

  static Key toKey(String userId) {
    return KeyFactory.createKey(ProfileModel.KIND, userId);
  }
}
