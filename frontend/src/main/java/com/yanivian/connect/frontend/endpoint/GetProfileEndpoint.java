package com.yanivian.connect.frontend.endpoint;

import java.io.IOException;
import java.util.Optional;
import javax.inject.Inject;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.yanivian.connect.common.guice.GuiceEndpoint;
import com.yanivian.connect.common.guice.GuiceEndpoint.AllowPost;
import com.yanivian.connect.frontend.aspect.ProfilesAspect;
import com.yanivian.connect.frontend.proto.model.Profile;

@WebServlet(name = "GetProfileEndpoint", urlPatterns = {"/profile/get"})
@AllowPost
public final class GetProfileEndpoint extends GuiceEndpoint {

  @Inject
  private AuthHelper authHelper;
  @Inject
  private ProfilesAspect profilesAspect;

  // Servlets must have public no-arg constructors.
  public GetProfileEndpoint() {}

  @Override
  protected void process(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    Optional<String> userID = authHelper.getVerifiedUserID(req, resp);
    if (!userID.isPresent()) {
      return;
    }
    Optional<Profile> profile = profilesAspect.getProfile(userID.get());
    if (!profile.isPresent()) {
      resp.sendError(404);
      return;
    }
    writeJsonResponse(resp, profile.get());
  }
}
