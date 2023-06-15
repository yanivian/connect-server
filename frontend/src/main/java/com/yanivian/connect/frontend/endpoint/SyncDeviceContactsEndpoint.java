package com.yanivian.connect.frontend.endpoint;

import java.io.IOException;
import java.util.Optional;
import javax.inject.Inject;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.google.common.collect.ImmutableSet;
import com.yanivian.connect.backend.aspect.ProfilesAspect;
import com.yanivian.connect.backend.aspect.ProfilesAspect.ProfileCache;
import com.yanivian.connect.common.guice.GuiceEndpoint;
import com.yanivian.connect.common.guice.GuiceEndpoint.AllowPost;
import com.yanivian.connect.frontend.proto.api.SyncDeviceContactsRequest;
import com.yanivian.connect.frontend.proto.api.SyncDeviceContactsResult;

@WebServlet(name = "SyncDeviceContactsEndpoint", urlPatterns = {"/contacts/sync"})
@AllowPost
public final class SyncDeviceContactsEndpoint extends GuiceEndpoint {

  private static final String PARAM_REQUEST = "request";

  @Inject
  private AuthHelper authHelper;
  @Inject
  private ProfilesAspect profilesAspect;

  // Servlets must have public no-arg constructors.
  public SyncDeviceContactsEndpoint() {}

  @Override
  protected void process(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    Optional<String> userID = authHelper.getVerifiedUserID(req, resp);
    if (!userID.isPresent()) {
      return;
    }

    SyncDeviceContactsRequest syncReq =
        parseJson(getRequiredParameter(req, PARAM_REQUEST), SyncDeviceContactsRequest.class);
    SyncDeviceContactsResult.Builder result = SyncDeviceContactsResult.newBuilder();

    if (syncReq.getPhoneNumbersCount() > 0) {
      ProfileCache profileCache = profilesAspect
          .getProfilesByPhoneNumber(ImmutableSet.copyOf(syncReq.getPhoneNumbersList()));
      result.addAllUsers(profileCache.getAllUsers(true)).build();
    }


    if (syncReq.getUserIDsCount() > 0) {
      ProfileCache profileCache =
          profilesAspect.getProfiles(ImmutableSet.copyOf(syncReq.getUserIDsList()));
      result.addAllUsers(profileCache.getAllUsers(true)).build();
    }

    writeJsonResponse(resp, result);
  }
}
