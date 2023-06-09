package com.yanivian.connect.frontend.endpoint;

import java.io.IOException;
import java.util.Optional;
import javax.inject.Inject;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.yanivian.connect.common.guice.GuiceEndpoint;
import com.yanivian.connect.common.guice.GuiceEndpoint.AllowPost;
import com.yanivian.connect.backend.aspect.ProfilesAspect;
import com.yanivian.connect.backend.proto.model.Profile;

@WebServlet(name = "UpdateProfileEndpoint", urlPatterns = {"/profile/update"})
@AllowPost
public final class UpdateProfileEndpoint extends GuiceEndpoint {

  private static final String PARAM_NAME = "name";
  private static final String PARAM_EMAIL_ADDRESS = "emailAddress";
  private static final String PARAM_IMAGE = "image";

  @Inject
  private AuthHelper authHelper;
  @Inject
  private ProfilesAspect profilesAspect;

  // Servlets must have public no-arg constructors.
  public UpdateProfileEndpoint() {}

  @Override
  protected void process(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    Optional<String> userID = authHelper.getVerifiedUserID(req, resp);
    if (!userID.isPresent()) {
      return;
    }

    Optional<Profile> profile =
        profilesAspect.updateProfile(userID.get(), getOptionalParameter(req, PARAM_NAME),
            getOptionalParameter(req, PARAM_EMAIL_ADDRESS), getOptionalParameter(req, PARAM_IMAGE));
    if (!profile.isPresent()) {
      resp.sendError(404, "Profile not found: " + userID.get());
      return;
    }
    writeJsonResponse(resp, profile.get());
  }
}
