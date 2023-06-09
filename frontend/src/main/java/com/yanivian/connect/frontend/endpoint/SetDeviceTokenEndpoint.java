package com.yanivian.connect.frontend.endpoint;

import java.io.IOException;
import java.util.Optional;
import javax.inject.Inject;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.google.common.base.Preconditions;
import com.google.protobuf.Empty;
import com.yanivian.connect.common.guice.GuiceEndpoint;
import com.yanivian.connect.common.guice.GuiceEndpoint.AllowPost;
import com.yanivian.connect.frontend.aspect.ProfilesAspect;

@WebServlet(name = "SetDeviceTokenEndpoint", urlPatterns = {"/profile/setdevicetoken"})
@AllowPost
public final class SetDeviceTokenEndpoint extends GuiceEndpoint {

  private static final String PARAM_DEVICE_TOKEN = "deviceToken";

  @Inject
  private AuthHelper authHelper;
  @Inject
  private ProfilesAspect profilesAspect;

  // Servlets must have public no-arg constructors.
  public SetDeviceTokenEndpoint() {}

  @Override
  protected void process(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    Optional<String> userID = authHelper.getVerifiedUserID(req, resp);
    if (!userID.isPresent()) {
      return;
    }

    Optional<String> deviceToken = getOptionalParameter(req, PARAM_DEVICE_TOKEN);
    Preconditions.checkState(profilesAspect.setDeviceToken(userID.get(), deviceToken));

    writeJsonResponse(resp, Empty.getDefaultInstance());
  }
}
