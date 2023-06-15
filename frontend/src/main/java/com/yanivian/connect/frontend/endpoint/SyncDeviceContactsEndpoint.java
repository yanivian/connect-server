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

    ImmutableSet<String> phoneNumbers = ImmutableSet
        .copyOf(parseJson(getRequiredParameter(req, PARAM_REQUEST), SyncDeviceContactsRequest.class)
            .getPhoneNumbersList());
    ProfileCache profileCache = profilesAspect.getProfilesByPhoneNumber(phoneNumbers);
    SyncDeviceContactsResult result = SyncDeviceContactsResult.newBuilder()
        .addAllUsers(profileCache.getUsers(phoneNumbers, true)).build();
    writeJsonResponse(resp, result);
  }
}
