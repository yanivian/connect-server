package com.yanivian.connect.frontend.aspect;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.yanivian.connect.frontend.dao.ConnectionDao;
import com.yanivian.connect.frontend.dao.ConnectionDao.ConnectionModel;
import com.yanivian.connect.frontend.dao.ContactDao;
import com.yanivian.connect.frontend.dao.ContactDao.ContactModel;
import com.yanivian.connect.frontend.dao.DatastoreUtil;
import com.yanivian.connect.frontend.dao.ProfileDao;
import com.yanivian.connect.frontend.dao.ProfileDao.ProfileModel;
import com.yanivian.connect.frontend.proto.aspect.ConnectionsSnapshot;
import com.yanivian.connect.frontend.proto.aspect.UserInfo;

/** Aspect that deals with connection management. */
public final class ConnectionsAspect {

  private static final Logger LOGGER = LogManager.getLogger(ConnectionsAspect.class);

  private final ConnectionDao connectionDao;
  private final ContactDao contactDao;
  private final ProfileDao profileDao;
  private final DatastoreService datastore;

  @Inject
  ConnectionsAspect(ConnectionDao connectionDao, ContactDao contactDao, ProfileDao profileDao,
      DatastoreService datastore) {
    this.connectionDao = connectionDao;
    this.contactDao = contactDao;
    this.profileDao = profileDao;
    this.datastore = datastore;
  }

  public ConnectionsSnapshot getSnapshot(String ownerUserID, String ownerPhoneNumber) {
    LOGGER.atInfo().log("Fetching ConnectionSnapshot: ID={}, PhoneNumber={}", ownerUserID,
        ownerPhoneNumber);
    return DatastoreUtil.newTransaction(datastore, txn -> {
      // Fetch inviters (users that have added this user as a contact.)
      ImmutableList<ContactModel> contacts =
          contactDao.listContactsTargetingPhoneNumber(ownerPhoneNumber);
      ImmutableList<String> inviterUserIDs = contacts.stream().map(ContactModel::getOwnerUserID)
          .collect(ImmutableList.toImmutableList());
      LOGGER.atInfo().log("- Found {} Contacts.", inviterUserIDs.size());

      // Fetch incoming connections.
      ImmutableList<ConnectionModel> incoming = connectionDao.listConnectionsForTarget(ownerUserID);
      LOGGER.atInfo().log("- Found {} Incoming Connections.", incoming.size());

      // Fetch outgoing connections.
      ImmutableList<ConnectionModel> outgoing = connectionDao.listConnectionsForOwner(ownerUserID);
      LOGGER.atInfo().log("- Found {} Outgoing Connections.", outgoing.size());

      // Fetch relevant profiles.
      Set<String> otherUserIDs = new HashSet<>(inviterUserIDs);
      otherUserIDs.addAll(
          incoming.stream().map(ConnectionModel::getOwnerUserID).collect(Collectors.toList()));
      otherUserIDs.addAll(
          outgoing.stream().map(ConnectionModel::getTargetUserID).collect(Collectors.toList()));
      LOGGER.atInfo().log("- Bulk-Fetching {} Profiles.", otherUserIDs.size());
      ImmutableMap<String, ProfileModel> profileModels =
          profileDao.getProfilesByUserId(txn, ImmutableSet.copyOf(otherUserIDs));
      LOGGER.atInfo().log("- Bulk-Fetched {} Profiles.", profileModels.size());

      ImmutableList<UserInfo> inviters =
          inviterUserIDs.stream().filter(profileModels::containsKey).map(profileModels::get)
              .map(ConnectionsAspect::toUserInfo).collect(ImmutableList.toImmutableList());
      LOGGER.atInfo().log("- Output {} Inviters.", inviters.size());

      return ConnectionsSnapshot.newBuilder().addAllInviters(inviters).build();
    });
  }

  private static UserInfo toUserInfo(ProfileModel profileModel) {
    UserInfo.Builder user = UserInfo.newBuilder().setPhoneNumber(profileModel.getPhoneNumber())
        .setUserID(profileModel.getID());
    profileModel.getName().ifPresent(user::setName);
    profileModel.getImage().ifPresent(user::setProfileImageURL);
    return user.build();
  }
}
