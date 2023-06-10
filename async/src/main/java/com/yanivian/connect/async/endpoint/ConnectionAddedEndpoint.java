package com.yanivian.connect.async.endpoint;

import java.io.IOException;
import java.util.Optional;
import javax.inject.Inject;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.protobuf.MessageOrBuilder;
import com.yanivian.connect.backend.aspect.ProfilesAspect;
import com.yanivian.connect.backend.aspect.ProfilesAspect.ProfileCache;
import com.yanivian.connect.backend.dao.ConnectionDao;
import com.yanivian.connect.backend.proto.aspect.AddConnectionResult;
import com.yanivian.connect.backend.proto.aspect.UserInfo;
import com.yanivian.connect.backend.proto.model.Connection.ConnectionState;
import com.yanivian.connect.common.guice.GuiceEndpoint;
import com.yanivian.connect.common.guice.GuiceEndpoint.AllowPost;
import com.yanivian.connect.common.util.TextProtoUtils;

@WebServlet(name = "ConnectionAddedEndpoint", urlPatterns = {"/connection/added"})
@AllowPost
public final class ConnectionAddedEndpoint extends GuiceEndpoint {

  private static final String PARAM_OWNER_USER_ID = "ownerUserID";
  private static final String PARAM_TARGET_USER_ID = "targetUserID";

  @Inject
  private ProfilesAspect profilesAspect;
  @Inject
  private ConnectionDao connectionDao;
  @Inject
  private FirebaseMessaging firebaseMessaging;

  // Servlets must have public no-arg constructors.
  public ConnectionAddedEndpoint() {}

  @Override
  protected void process(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String ownerUserID = getRequiredParameter(req, PARAM_OWNER_USER_ID);
    String targetUserID = getRequiredParameter(req, PARAM_TARGET_USER_ID);
    Preconditions.checkState(!ownerUserID.equals(targetUserID));

    boolean isConnected = connectionDao.findConnection(ownerUserID, targetUserID)
        .map(c -> c.getState().equals(ConnectionState.CONNECTED)).orElse(false);

    ProfileCache profileCache =
        profilesAspect.getProfiles(ImmutableList.of(ownerUserID, targetUserID));
    Optional<String> deviceToken = profileCache.getDeviceToken(ownerUserID);
    Optional<UserInfo> targetUser = profileCache.getUser(targetUserID, isConnected);
    if (deviceToken.isPresent() && targetUser.isPresent()) {
      AddConnectionResult payload =
          AddConnectionResult.newBuilder().setUser(targetUser.get()).setIsConnected(true).build();
      try {
        String messageID = firebaseMessaging.send(toMessage(deviceToken.get(), payload));
        logger.atInfo().log("Notified connection added: {}", messageID);
      } catch (FirebaseMessagingException fme) {
        logger.atError().withThrowable(fme).log("Failed to notify connection added.");
      }
    }
  }

  private <T extends MessageOrBuilder> Message toMessage(String deviceToken, T payload)
      throws IOException {
    return Message.builder().setToken(deviceToken)
        .putData("ConnectionAdded", TextProtoUtils.encodeToString(payload)).build();
  }
}
