package com.yanivian.connect.frontend.aspect;

import java.util.Optional;
import javax.inject.Inject;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.Transaction;
import com.yanivian.connect.frontend.dao.DatastoreUtil;
import com.yanivian.connect.frontend.dao.ImageDao;
import com.yanivian.connect.frontend.dao.ImageDao.ImageModel;
import com.yanivian.connect.frontend.dao.ProfileDao;
import com.yanivian.connect.frontend.dao.ProfileDao.ProfileModel;
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

  public Optional<Profile> getProfile(String ownerUserID) {
    return DatastoreUtil.newTransaction(datastore, txn -> getProfile(txn, ownerUserID));
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

  public Optional<Profile> updateProfile(String userID, Optional<String> name,
      Optional<String> emailAddress, Optional<String> imageID) {
    return DatastoreUtil.newTransaction(datastore, txn -> {
      Optional<ProfileModel> optionalProfileModel = profileDao.getProfileByUserId(txn, userID);
      if (!optionalProfileModel.isPresent()) {
        return Optional.empty();
      }
      ProfileModel profileModel = optionalProfileModel.get();
      profileModel.setName(name);
      profileModel.setEmailAddress(emailAddress);
      profileModel.setImage(imageID);
      return Optional.of(toProfile(txn, profileModel));
    });
  }
}
