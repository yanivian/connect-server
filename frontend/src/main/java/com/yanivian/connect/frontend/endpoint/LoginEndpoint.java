package com.yanivian.connect.frontend.endpoint;

import java.io.IOException;
import java.util.Optional;
import javax.inject.Inject;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.yanivian.connect.common.guice.GuiceEndpoint;
import com.yanivian.connect.common.guice.GuiceEndpoint.AllowPost;
import com.yanivian.connect.frontend.aspect.ConnectionsAspect;
import com.yanivian.connect.frontend.aspect.ProfilesAspect;
import com.yanivian.connect.frontend.proto.api.LoginContext;
import com.yanivian.connect.frontend.proto.model.Profile;

/**
 * Provides {@link LoginContext} to clients. This includes the following:
 * <ul>
 * <li>Get user profile,creating an empty profile if needed.</li>
 * <li>>Get any credentials needed by the client.</li>
 * </ul>
 */
@WebServlet(name = "LoginEndpoint", urlPatterns = {"/user/login"})
@AllowPost
public final class LoginEndpoint extends GuiceEndpoint {

  private static final String PARAM_PHONE_NUMBER = "phoneNumber";

  @Inject
  private AuthHelper authHelper;
  @Inject
  private ProfilesAspect profilesAspect;
  @Inject
  private ConnectionsAspect connectionsAspect;

  // Servlets must have public no-arg constructors.
  public LoginEndpoint() {}

  @Override
  protected void process(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    Optional<String> optionalUserID = authHelper.getVerifiedUserID(req, resp);
    if (!optionalUserID.isPresent()) {
      return;
    }
    String userID = optionalUserID.get();

    // Credentials
    LoginContext.Builder loginContext = LoginContext.newBuilder();
    loginContext.getCredentialsBuilder()
        .setGoogleCloudApiKey("AIzaSyCJDWjyam35lSTHQD0Odg7fughH_VAa9qk")
        .setOpenAIApiKey("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");

    // Profile
    String phoneNumber = req.getParameter(PARAM_PHONE_NUMBER);
    Preconditions.checkArgument(!Strings.isNullOrEmpty(phoneNumber),
        "Missing parameter: " + PARAM_PHONE_NUMBER);
    Profile profile = profilesAspect.getOrCreateProfile(userID, phoneNumber);
    Preconditions.checkState(phoneNumber.equals(profile.getPhoneNumber()));
    loginContext.setProfile(profile);

    // Connections
    loginContext.setConnectionsSnapshot(connectionsAspect.getSnapshot(userID, phoneNumber));

    writeJsonResponse(resp, loginContext.build());
  }
}
