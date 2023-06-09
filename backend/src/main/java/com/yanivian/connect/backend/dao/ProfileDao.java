package com.yanivian.connect.backend.dao;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.yanivian.connect.backend.proto.model.Profile;

public final class ProfileDao {

  private final DatastoreService datastore;
  private final Clock clock;

  @Inject
  ProfileDao(DatastoreService datastore, Clock clock) {
    this.datastore = datastore;
    this.clock = clock;
  }

  public Optional<ProfileModel> getProfile(Transaction txn, String userID) {
    try {
      Entity entity = datastore.get(txn, toKey(userID));
      return Optional.of(new ProfileModel(entity));
    } catch (EntityNotFoundException enfe) {
      return Optional.empty();
    }
  }

  public ImmutableMap<String, ProfileModel> getProfiles(Transaction txn,
      ImmutableSet<String> userIDs) {
    Map<Key, Entity> entityMap =
        datastore.get(txn, Iterables.transform(userIDs, ProfileDao::toKey));
    return entityMap.entrySet().stream().collect(ImmutableMap.toImmutableMap(
        entry -> entry.getKey().getName(), entry -> new ProfileModel(entry.getValue())));
  }

  /** Find profiles associated with a given phone number. */
  // Cannot be transactional.
  public ImmutableList<ProfileModel> findProfilesByPhoneNumber(String phoneNumber) {
    Query query = new Query(ProfileModel.KIND).setFilter(
        new FilterPredicate(ProfileModel.PROPERTY_PHONE_NUMBER, FilterOperator.EQUAL, phoneNumber));
    return Streams.stream(datastore.prepare(query).asIterable()).map(ProfileModel::new)
        .collect(ImmutableList.toImmutableList());
  }

  /** Find profiles associated with a given device token. */
  // Cannot be transactional.
  public ImmutableList<ProfileModel> findProfilesByDeviceToken(String deviceToken) {
    Query query = new Query(ProfileModel.KIND).setFilter(
        new FilterPredicate(ProfileModel.PROPERTY_DEVICE_TOKEN, FilterOperator.EQUAL, deviceToken));
    return Streams.stream(datastore.prepare(query).asIterable()).map(ProfileModel::new)
        .collect(ImmutableList.toImmutableList());
  }

  /** Creates, saves and returns a new profile. */
  public ProfileModel createProfile(Transaction txn, String userID, String phoneNumber) {
    Entity entity = new Entity(ProfileModel.KIND, userID);
    return new ProfileModel(entity).setPhoneNumber(phoneNumber)
        .setCreatedTimestampMillis(clock.millis()).save(txn, datastore);
  }

  public static final class ProfileModel extends DatastoreModel<Profile, ProfileModel> {

    static final String KIND = "Profile";
    private static final String PROPERTY_PHONE_NUMBER = "PhoneNumber";
    private static final String PROPERTY_CREATED_TIMESTAMP_MILLIS = "CreatedTimestampMillis";
    private static final String PROPERTY_NAME = "Name";
    private static final String PROPERTY_EMAIL_ADDRESS = "EmailAddress";
    private static final String PROPERTY_LAST_UPDATED_TIMESTAMP_MILLIS =
        "LastUpdatedTimestampMillis";
    private static final String PROPERTY_IMAGE = "Image";
    private static final String PROPERTY_DEVICE_TOKEN = "DeviceToken";

    private ProfileModel(Entity entity) {
      super(entity);
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
        entity.setProperty(PROPERTY_IMAGE, image.get());
      } else {
        entity.removeProperty(PROPERTY_IMAGE);
      }
      return this;
    }

    public Optional<String> getImage() {
      return getOptionalProperty(PROPERTY_IMAGE);
    }

    public ProfileModel setDeviceToken(Optional<String> deviceToken) {
      if (deviceToken.isPresent()) {
        entity.setProperty(PROPERTY_DEVICE_TOKEN, deviceToken.get());
      } else {
        entity.removeProperty(PROPERTY_DEVICE_TOKEN);
      }
      return this;
    }

    public Optional<String> getDeviceToken() {
      return getOptionalProperty(PROPERTY_DEVICE_TOKEN);
    }

    public ProfileModel setLastUpdatedTimestampMillis(long timestampMillis) {
      entity.setProperty(PROPERTY_LAST_UPDATED_TIMESTAMP_MILLIS, timestampMillis);
      return this;
    }

    public OptionalLong getLastUpdatedTimestampMillis() {
      Optional<Long> value = getOptionalProperty(PROPERTY_LAST_UPDATED_TIMESTAMP_MILLIS);
      return value.isPresent() ? OptionalLong.of(value.get()) : OptionalLong.empty();
    }
  }

  static Key toKey(String userId) {
    return KeyFactory.createKey(ProfileModel.KIND, userId);
  }
}
