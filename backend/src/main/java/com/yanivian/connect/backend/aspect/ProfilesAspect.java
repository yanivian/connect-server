package com.yanivian.connect.backend.aspect;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.Transaction;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.yanivian.connect.backend.dao.DatastoreUtil;
import com.yanivian.connect.backend.dao.ImageDao;
import com.yanivian.connect.backend.dao.ImageDao.ImageModel;
import com.yanivian.connect.backend.dao.ProfileDao;
import com.yanivian.connect.backend.dao.ProfileDao.ProfileModel;
import com.yanivian.connect.backend.proto.aspect.UserInfo;
import com.yanivian.connect.backend.proto.model.Profile;

/** Aspect that deals with profile management. */
public final class ProfilesAspect {

  private final ProfileDao profileDao;
  private final ImageDao imageDao;
  private final DatastoreService datastore;

  @Inject
  ProfilesAspect(ProfileDao profileDao, ImageDao imageDao, DatastoreService datastore) {
    this.profileDao = profileDao;
    this.imageDao = imageDao;
    this.datastore = datastore;
  }

  public Profile getOrCreateProfile(String userID, String phoneNumber) {
    // User IDs are expected to map 1:1 with phone numbers.
    ImmutableList<ProfileModel> associatedProfiles =
        profileDao.findProfilesByPhoneNumber(phoneNumber);
    Preconditions.checkState(associatedProfiles.size() <= 1
        && (associatedProfiles.isEmpty() || associatedProfiles.get(0).getID().equals(userID)));

    return DatastoreUtil.newTransaction(datastore, txn -> {
      Optional<Profile> profile = getProfile(txn, userID);
      if (profile.isPresent()) {
        return profile.get();
      }
      return profileDao.createProfile(txn, userID, phoneNumber).toProto();
    });
  }

  private Optional<Profile> getProfile(Transaction txn, String ownerUserID) {
    Optional<ProfileModel> profileModel = profileDao.getProfile(txn, ownerUserID);
    if (!profileModel.isPresent()) {
      return Optional.empty();
    }
    return Optional.of(toProfile(txn, profileModel.get()));
  }

  private Profile toProfile(Transaction txn, ProfileModel profileModel) {
    Profile profile = profileModel.toProto();
    Optional<ImageModel> imageModel =
        profileModel.getImage().flatMap(imageID -> imageDao.getImage(txn, imageID));
    if (imageModel.isPresent()) {
      profile = profile.toBuilder().setImage(imageModel.get().toProto()).build();
    }
    return profile;
  }

  public Optional<String> getDeviceToken(String ownerUserID) {
    return DatastoreUtil.newTransaction(datastore, txn -> {
      return profileDao.getProfile(txn, ownerUserID).flatMap(ProfileModel::getDeviceToken);
    });
  }

  public Optional<Profile> getProfile(String userID) {
    return getProfiles(ImmutableList.of(userID)).getProfile(userID);
  }

  /**
   * Updates a profile. Each field is provided as an optional where a missing value indicates the
   * field will be cleared.
   */
  public Optional<Profile> updateProfile(String userID, Optional<String> name,
      Optional<String> emailAddress, Optional<String> imageID) {
    return DatastoreUtil.newTransaction(datastore, txn -> {
      Optional<ProfileModel> optionalProfileModel = profileDao.getProfile(txn, userID);
      if (!optionalProfileModel.isPresent()) {
        return Optional.empty();
      }
      ProfileModel profileModel = optionalProfileModel.get().setName(name)
          .setEmailAddress(emailAddress).setImage(imageID).save(txn, datastore);
      return Optional.of(toProfile(txn, profileModel));
    });
  }

  /**
   * Sets the device token on the actor's profile. Returns {@code false} if the actor's profile
   * couldn't be found. Clears the device token on any other profiles associated with it.
   */
  public boolean setDeviceToken(String actorID, Optional<String> deviceToken) {
    // Determine which users are in scope.
    Set<String> userIDs = new HashSet<>();
    if (deviceToken.isPresent()) {
      profileDao.findProfilesByDeviceToken(deviceToken.get()).stream().map(ProfileModel::getID)
          .forEach(userIDs::add);
    }
    userIDs.add(actorID);

    return DatastoreUtil.newTransaction(datastore, txn -> {
      ImmutableMap<String, ProfileModel> profileMap =
          profileDao.getProfiles(txn, ImmutableSet.copyOf(userIDs));
      // If the actor doesn't exist, bail.
      if (!profileMap.containsKey(actorID)) {
        return false;
      }
      profileMap.values().forEach(profileModel -> {
        if (profileModel.getID().equals(actorID)) {
          profileModel.setDeviceToken(deviceToken);
        } else {
          profileModel.setDeviceToken(Optional.empty());
        }
        profileModel.save(txn, datastore);
      });
      return true;
    });
  }

  public ProfileCache getProfiles(Collection<String> userIDs) {
    return DatastoreUtil.newTransaction(datastore, txn -> {
      ImmutableMap<String, ProfileModel> profileModels =
          profileDao.getProfiles(txn, ImmutableSet.copyOf(userIDs));

      ImmutableSet<String> profileImageIDs =
          profileModels.values().stream().map(ProfileModel::getImage).filter(Optional::isPresent)
              .map(Optional::get).collect(ImmutableSet.toImmutableSet());
      ImmutableMap<String, ImageModel> profileImages = imageDao.getImages(txn, profileImageIDs);

      return new ProfileCache(profileModels, profileImages);
    });
  }

  public static final class ProfileCache {
    private final ImmutableMap<String, ProfileModel> profileModels;
    private final ImmutableMap<String, ImageModel> imageModels;

    private ProfileCache(ImmutableMap<String, ProfileModel> profileModels,
        ImmutableMap<String, ImageModel> imageModels) {
      this.profileModels = profileModels;
      this.imageModels = imageModels;
    }

    public ImmutableList<Profile> getProfiles(Collection<String> userIDs) {
      return userIDs.stream().map(this::getProfile).filter(Optional::isPresent).map(Optional::get)
          .collect(ImmutableList.toImmutableList());
    }

    public Optional<Profile> getProfile(String userID) {
      ProfileModel profileModel = profileModels.get(userID);
      if (profileModel == null) {
        return Optional.empty();
      }

      Profile.Builder profile = profileModel.toProto().toBuilder();
      profileModel.getImage().ifPresent(imageID -> {
        ImageModel imageModel = imageModels.get(imageID);
        if (imageModel != null) {
          profile.setImage(imageModel.toProto());
        }
      });
      return Optional.of(profile.build());
    }

    public Optional<String> getDeviceToken(String userID) {
      ProfileModel profileModel = profileModels.get(userID);
      if (profileModel == null) {
        return Optional.empty();
      }
      return profileModel.getDeviceToken();
    }

    public ImmutableList<UserInfo> getUsers(Collection<String> userIDs, boolean isConnected) {
      return userIDs.stream().map(user -> getUser(user, isConnected)).filter(Optional::isPresent)
          .map(Optional::get).collect(ImmutableList.toImmutableList());
    }

    public Optional<UserInfo> getUser(String userID, boolean isConnected) {
      ProfileModel profileModel = profileModels.get(userID);
      if (profileModel == null) {
        return Optional.empty();
      }

      UserInfo.Builder user = UserInfo.newBuilder().setUserID(userID);
      if (isConnected) {
        user.setPhoneNumber(profileModel.getPhoneNumber());
      }
      profileModel.getName().ifPresent(user::setName);
      profileModel.getImage().ifPresent(imageID -> {
        ImageModel imageModel = imageModels.get(imageID);
        if (imageModel != null) {
          user.setImage(imageModel.toProto());
        }
      });
      return Optional.of(user.build());
    }
  }
}
