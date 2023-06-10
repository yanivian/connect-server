package com.yanivian.connect.backend.aspect;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.yanivian.connect.backend.aspect.ProfilesAspect.ProfileCache;
import com.yanivian.connect.backend.dao.ConnectionDao;
import com.yanivian.connect.backend.dao.ConnectionDao.ConnectionModel;
import com.yanivian.connect.backend.dao.ContactDao;
import com.yanivian.connect.backend.dao.ContactDao.ContactModel;
import com.yanivian.connect.backend.dao.DatastoreUtil;
import com.yanivian.connect.backend.proto.aspect.ConnectionAddedResult;
import com.yanivian.connect.backend.proto.aspect.ConnectionsSnapshot;
import com.yanivian.connect.backend.proto.aspect.UserInfo;
import com.yanivian.connect.backend.proto.model.Connection.ConnectionState;
import com.yanivian.connect.backend.proto.model.Contact;
import com.yanivian.connect.backend.taskqueue.AsyncTaskQueueAdapter;

/** Aspect that deals with connection management. */
public final class ConnectionsAspect {

  private static final Logger LOGGER = LogManager.getLogger(ConnectionsAspect.class);

  private final ProfilesAspect profilesAspect;
  private final ConnectionDao connectionDao;
  private final ContactDao contactDao;
  private final DatastoreService datastore;
  private final AsyncTaskQueueAdapter asyncTaskQueue;

  @Inject
  ConnectionsAspect(ProfilesAspect profilesAspect, ConnectionDao connectionDao,
      ContactDao contactDao, DatastoreService datastore, AsyncTaskQueueAdapter asyncTaskQueue) {
    this.profilesAspect = profilesAspect;
    this.connectionDao = connectionDao;
    this.contactDao = contactDao;
    this.datastore = datastore;
    this.asyncTaskQueue = asyncTaskQueue;
  }

  public ConnectionAddedResult addConnection(String actorUserID, String targetUserID) {
    ConnectionModel connectionModel = addConnectionInternal(actorUserID, targetUserID);
    boolean isConnected = connectionModel.getState().equals(ConnectionState.CONNECTED);
    UserInfo userInfo = profilesAspect.getProfiles(ImmutableList.of(targetUserID))
        .getUser(targetUserID, isConnected).orElseThrow(IllegalStateException::new);
    return ConnectionAddedResult.newBuilder().setUser(userInfo).setIsConnected(isConnected).build();
  }

  private ConnectionModel addConnectionInternal(String actorUserID, String targetUserID) {
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
          asyncTaskQueue.notifyConnectionAdded(txn, targetUserID, actorUserID);
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

    // Fetch invites (this user's contacts.)
    ImmutableList<Contact> invites = contactDao.listContactsOwnedBy(ownerUserID).stream()
        .map(ContactModel::toProto).collect(ImmutableList.toImmutableList());
    LOGGER.atInfo().log("- Output {} Invites.", invites.size());

    // Fetch inviters (users that have added this user as a contact.)
    ImmutableList<ContactModel> contacts =
        contactDao.listContactsTargetingPhoneNumber(ownerPhoneNumber);
    ImmutableSet<String> inviterUserIDs =
        contacts.stream().map(ContactModel::getOwnerUserID).collect(ImmutableSet.toImmutableSet());
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
    ProfileCache profileCache = profilesAspect.getProfiles(otherUserIDs);

    // Inviters
    ImmutableList<UserInfo> inviters = profileCache.getUsers(inviterUserIDs, false);
    LOGGER.atInfo().log("- Output {} Inviters.", inviters.size());

    // Connections
    ImmutableSet<String> allIncomingUserIDs = incomingConnections.stream()
        .map(ConnectionModel::getOwnerUserID).collect(ImmutableSet.toImmutableSet());
    ImmutableSet<String> allOutgoingUserIDs = outgoingConnections.stream()
        .map(ConnectionModel::getTargetUserID).collect(ImmutableSet.toImmutableSet());
    ImmutableSet<String> connectedUserIDs =
        ImmutableSet.copyOf(Sets.intersection(allIncomingUserIDs, allOutgoingUserIDs));
    ImmutableList<UserInfo> connected = profileCache.getUsers(connectedUserIDs, true);
    LOGGER.atInfo().log("- Output {} Connected.", inviters.size());

    // Incoming
    ImmutableSet<String> incomingUserIDs =
        ImmutableSet.copyOf(Sets.difference(allIncomingUserIDs, connectedUserIDs));
    ImmutableList<UserInfo> incoming = profileCache.getUsers(incomingUserIDs, false);
    LOGGER.atInfo().log("- Output {} Incoming.", incoming.size());

    // Outgoing
    ImmutableSet<String> outgoingUserIDs =
        ImmutableSet.copyOf(Sets.difference(allOutgoingUserIDs, connectedUserIDs));
    ImmutableList<UserInfo> outgoing = profileCache.getUsers(outgoingUserIDs, false);
    LOGGER.atInfo().log("- Output {} Outgoing.", outgoing.size());

    // Consolidate as a snapshot.
    return ConnectionsSnapshot.newBuilder().addAllInvites(invites).addAllInviters(inviters)
        .addAllConnections(connected).addAllIncoming(incoming).addAllOutgoing(outgoing).build();
  }
}
