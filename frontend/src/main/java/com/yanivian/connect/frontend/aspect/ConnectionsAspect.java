package com.yanivian.connect.frontend.aspect;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.yanivian.connect.frontend.dao.ConnectionDao;
import com.yanivian.connect.frontend.dao.ConnectionDao.ConnectionModel;
import com.yanivian.connect.frontend.dao.ContactDao;
import com.yanivian.connect.frontend.dao.ContactDao.ContactModel;
import com.yanivian.connect.frontend.dao.DatastoreUtil;
import com.yanivian.connect.frontend.dao.ImageDao;
import com.yanivian.connect.frontend.dao.ImageDao.ImageModel;
import com.yanivian.connect.frontend.dao.ProfileDao;
import com.yanivian.connect.frontend.dao.ProfileDao.ProfileModel;
import com.yanivian.connect.frontend.proto.aspect.ConnectionsSnapshot;
import com.yanivian.connect.frontend.proto.aspect.UserInfo;
import com.yanivian.connect.frontend.proto.model.Connection.ConnectionState;
import com.yanivian.connect.frontend.proto.model.Contact;

/** Aspect that deals with connection management. */
public final class ConnectionsAspect {

  private static final Logger LOGGER = LogManager.getLogger(ConnectionsAspect.class);

  private final ConnectionDao connectionDao;
  private final ContactDao contactDao;
  private final ProfileDao profileDao;
  private final ImageDao imageDao;
  private final DatastoreService datastore;

  @Inject
  ConnectionsAspect(ConnectionDao connectionDao, ContactDao contactDao, ProfileDao profileDao,
      ImageDao imageDao, DatastoreService datastore) {
    this.connectionDao = connectionDao;
    this.contactDao = contactDao;
    this.profileDao = profileDao;
    this.imageDao = imageDao;
    this.datastore = datastore;
  }

  public ConnectionModel connect(String actorUserID, String targetUserID) {
    LOGGER.atInfo().log("Adding Connection: Actor={}, Target={}", actorUserID, targetUserID);

    Optional<ConnectionModel> outgoingConnection =
        connectionDao.findConnection(actorUserID, targetUserID);
    ConnectionState outgoingConnectionState =
        outgoingConnection.map(ConnectionModel::getState).orElse(ConnectionState.UNDEFINED);
    LOGGER.atInfo().log("- Outgoing connection state: {}", outgoingConnectionState);

    Optional<ConnectionModel> incomingConnection =
        connectionDao.findConnection(targetUserID, actorUserID);
    ConnectionState incomingConnectionState =
        incomingConnection.map(ConnectionModel::getState).orElse(ConnectionState.UNDEFINED);
    LOGGER.atInfo().log("- Incoming connection state: {}", incomingConnectionState);

    switch (incomingConnectionState) {
      case UNDEFINED:
        // Create a new outgoing connection.
        Preconditions.checkState(outgoingConnectionState.equals(ConnectionState.UNDEFINED)
            || outgoingConnectionState.equals(ConnectionState.ASK_TO_CONNECT));
        return connectionDao.createOrUpdateConnection(actorUserID, targetUserID,
            ConnectionState.ASK_TO_CONNECT);
      case ASK_TO_CONNECT:
        // The target user has asked to connect with the actor.
        Preconditions.checkState(outgoingConnectionState.equals(ConnectionState.UNDEFINED));
        return DatastoreUtil.newTransaction(datastore, txn -> {
          connectionDao.updateConnection(txn, incomingConnection.get(), ConnectionState.CONNECTED);
          return connectionDao.createConnection(txn, actorUserID, targetUserID,
              ConnectionState.CONNECTED);
        });
      case CONNECTED:
        // Already connected.
        Preconditions.checkState(outgoingConnectionState.equals(incomingConnectionState));
        return outgoingConnection.get();
      default:
        throw new IllegalStateException("Could not connect");
    }
  }

  public ConnectionsSnapshot getSnapshot(String ownerUserID, String ownerPhoneNumber) {
    LOGGER.atInfo().log("Fetching ConnectionSnapshot: ID={}, PhoneNumber={}", ownerUserID,
        ownerPhoneNumber);
    return DatastoreUtil.newTransaction(datastore, txn -> {
      // Fetch invites (this user's contacts.)
      ImmutableList<Contact> invites = contactDao.listContactsOwnedBy(ownerUserID).stream()
          .map(ContactModel::toProto).collect(ImmutableList.toImmutableList());
      LOGGER.atInfo().log("- Output {} Invites.", invites.size());

      // Fetch inviters (users that have added this user as a contact.)
      ImmutableList<ContactModel> contacts =
          contactDao.listContactsTargetingPhoneNumber(ownerPhoneNumber);
      ImmutableSet<String> inviterUserIDs = contacts.stream().map(ContactModel::getOwnerUserID)
          .collect(ImmutableSet.toImmutableSet());
      LOGGER.atInfo().log("- Found {} Inviters.", inviterUserIDs.size());

      // Fetch incoming connections.
      ImmutableList<ConnectionModel> incomingConnections =
          connectionDao.listConnectionsForTarget(ownerUserID);
      LOGGER.atInfo().log("- Found {} Incoming Connections.", incomingConnections.size());

      // Fetch outgoing connections.
      ImmutableList<ConnectionModel> outgoingConnections =
          connectionDao.listConnectionsForOwner(ownerUserID);
      LOGGER.atInfo().log("- Found {} Outgoing Connections.", outgoingConnections.size());

      // Fetch relevant profiles.
      Set<String> otherUserIDs = new HashSet<>(inviterUserIDs);
      otherUserIDs.addAll(incomingConnections.stream().map(ConnectionModel::getOwnerUserID)
          .collect(Collectors.toList()));
      otherUserIDs.addAll(outgoingConnections.stream().map(ConnectionModel::getTargetUserID)
          .collect(Collectors.toList()));
      LOGGER.atInfo().log("- Bulk-Fetching {} Profiles.", otherUserIDs.size());
      ImmutableMap<String, ProfileModel> profileModels =
          profileDao.getProfilesByUserId(txn, ImmutableSet.copyOf(otherUserIDs));
      LOGGER.atInfo().log("- Bulk-Fetched {} Profiles.", profileModels.size());

      // Fetch profile images.
      ImmutableSet<String> profileImageIDs =
          profileModels.values().stream().map(ProfileModel::getImage).filter(Optional::isPresent)
              .map(Optional::get).collect(ImmutableSet.toImmutableSet());
      LOGGER.atInfo().log("- Bulk-Fetching {} Profile Images.", profileImageIDs.size());
      ImmutableMap<String, ImageModel> profileImages = imageDao.getImages(txn, profileImageIDs);
      LOGGER.atInfo().log("- Bulk-Fetched {} Profile Images.", profileImages.size());

      // Inviters
      ImmutableList<UserInfo> inviters =
          toUserInfos(inviterUserIDs, false, profileModels, profileImages);
      LOGGER.atInfo().log("- Output {} Inviters.", inviters.size());

      // Connections
      ImmutableSet<String> allIncomingUserIDs = incomingConnections.stream()
          .map(ConnectionModel::getOwnerUserID).collect(ImmutableSet.toImmutableSet());
      ImmutableSet<String> allOutgoingUserIDs = outgoingConnections.stream()
          .map(ConnectionModel::getTargetUserID).collect(ImmutableSet.toImmutableSet());
      ImmutableSet<String> connectedUserIDs =
          ImmutableSet.copyOf(Sets.intersection(allIncomingUserIDs, allOutgoingUserIDs));
      ImmutableList<UserInfo> connected =
          toUserInfos(connectedUserIDs, true, profileModels, profileImages);
      LOGGER.atInfo().log("- Output {} Connected.", inviters.size());

      // Incoming
      ImmutableSet<String> incomingUserIDs =
          ImmutableSet.copyOf(Sets.difference(allIncomingUserIDs, connectedUserIDs));
      ImmutableList<UserInfo> incoming =
          toUserInfos(incomingUserIDs, false, profileModels, profileImages);
      LOGGER.atInfo().log("- Output {} Incoming.", incoming.size());

      // Outgoing
      ImmutableSet<String> outgoingUserIDs =
          ImmutableSet.copyOf(Sets.difference(allOutgoingUserIDs, connectedUserIDs));
      ImmutableList<UserInfo> outgoing =
          toUserInfos(outgoingUserIDs, false, profileModels, profileImages);
      LOGGER.atInfo().log("- Output {} Outgoing.", outgoing.size());

      // Consolidate as a snapshot.
      return ConnectionsSnapshot.newBuilder().addAllInvites(invites).addAllInviters(inviters)
          .addAllConnections(connected).addAllIncoming(incoming).addAllOutgoing(outgoing).build();
    });
  }

  private static ImmutableList<UserInfo> toUserInfos(ImmutableSet<String> userIDs,
      boolean isConnected, ImmutableMap<String, ProfileModel> profileModels,
      ImmutableMap<String, ImageModel> profileImages) {
    ImmutableList<UserInfo> connected = userIDs.stream().filter(profileModels::containsKey)
        .map(profileModels::get).map(profileModel -> {
          Optional<ImageModel> imageModel = profileModel.getImage()
              .flatMap(imageID -> Optional.ofNullable(profileImages.get(imageID)));
          return toUserInfo(profileModel, isConnected, imageModel);
        }).collect(ImmutableList.toImmutableList());
    return connected;
  }

  private static UserInfo toUserInfo(ProfileModel profileModel, boolean isConnected,
      Optional<ImageModel> imageModel) {
    UserInfo.Builder user = UserInfo.newBuilder().setUserID(profileModel.getID());
    if (isConnected) {
      user.setPhoneNumber(profileModel.getPhoneNumber());
    }
    profileModel.getName().ifPresent(user::setName);
    if (imageModel.isPresent()) {
      user.setImage(imageModel.get().toProto());
    }
    return user.build();
  }
}
