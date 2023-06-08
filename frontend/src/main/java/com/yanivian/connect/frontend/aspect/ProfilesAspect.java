package com.yanivian.connect.frontend.aspect;

import java.util.Collection;
import java.util.Optional;
import javax.inject.Inject;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.Transaction;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.yanivian.connect.frontend.dao.DatastoreUtil;
import com.yanivian.connect.frontend.dao.ImageDao;
import com.yanivian.connect.frontend.dao.ImageDao.ImageModel;
import com.yanivian.connect.frontend.dao.ProfileDao;
import com.yanivian.connect.frontend.dao.ProfileDao.ProfileModel;
import com.yanivian.connect.frontend.proto.aspect.UserInfo;
import com.yanivian.connect.frontend.proto.model.Profile;

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
    return DatastoreUtil.newTransaction(datastore, txn -> {
      Optional<Profile> profile = getProfile(txn, userID);
      if (profile.isPresent()) {
        return profile.get();
      }
      return profileDao.createProfile(txn, userID, phoneNumber).toProto();
    });
  }

  private Optional<Profile> getProfile(Transaction txn, String ownerUserID) {
    Optional<ProfileModel> profileModel = profileDao.getProfileByUserId(txn, ownerUserID);
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

  public Optional<Profile> getProfile(String userID) {
    return getProfiles(ImmutableList.of(userID)).getProfile(userID);
  }

  public Optional<Profile> updateProfile(String userID, Optional<String> name,
      Optional<String> emailAddress, Optional<String> imageID) {
    return DatastoreUtil.newTransaction(datastore, txn -> {
      Optional<ProfileModel> optionalProfileModel = profileDao.getProfileByUserId(txn, userID);
      if (!optionalProfileModel.isPresent()) {
        return Optional.empty();
      }
      ProfileModel profileModel = optionalProfileModel.get().setName(name)
          .setEmailAddress(emailAddress).setImage(imageID).save(txn, datastore);
      return Optional.of(toProfile(txn, profileModel));
    });
  }

  public ProfileCache getProfiles(Collection<String> userIDs) {
    return DatastoreUtil.newTransaction(datastore, txn -> {
      ImmutableMap<String, ProfileModel> profileModels =
          profileDao.getProfilesByUserId(txn, ImmutableSet.copyOf(userIDs));

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
