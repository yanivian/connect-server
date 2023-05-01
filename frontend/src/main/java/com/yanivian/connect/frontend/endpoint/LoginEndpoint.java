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
import com.yanivian.connect.frontend.dao.ImageDao;
import com.yanivian.connect.frontend.dao.ImageDao.ImageModel;
import com.yanivian.connect.frontend.dao.ProfileDao;
import com.yanivian.connect.frontend.dao.ProfileDao.ProfileModel;
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
  private static final String PARAM_CLIENT = "client";

  /** Enumeration of supported clients. */
  enum Client {
    ANDROID, IOS, WEB;
  }

  @Inject
  private AuthHelper authHelper;
  @Inject
  private ProfileDao profileDao;
  @Inject
  private ImageDao imageDao;

  public LoginEndpoint() {}

  @Override
  protected void process(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    Optional<String> userID = authHelper.getVerifiedUserID(req, resp);
    if (!userID.isPresent()) {
      return;
    }

    Client client = Client.valueOf(req.getParameter(PARAM_CLIENT));
    LoginContext.Builder loginContext = LoginContext.newBuilder();
    loginContext.getCredentialsBuilder().setGoogleCloudApiKey(getGoogleCloudApiKey(client));

    String phoneNumber = req.getParameter(PARAM_PHONE_NUMBER);
    Preconditions.checkArgument(!Strings.isNullOrEmpty(phoneNumber),
        "Missing parameter: " + PARAM_PHONE_NUMBER);
    Profile profile = getOrCreateProfile(phoneNumber, userID);
    loginContext.setProfile(profile);

    writeJsonResponse(resp, loginContext.build());
  }

  private Profile getOrCreateProfile(String phoneNumber, Optional<String> userID) {
    ProfileModel profileModel = getOrCreateProfile(userID.get(), phoneNumber);
    Profile profile = profileModel.toProto();

    Optional<ImageModel> imageModel = profileModel.getImage().flatMap(imageDao::getImage);
    if (imageModel.isPresent()) {
      profile = profile.toBuilder().setImage(imageModel.get().toProto()).build();
    }
    return profile;
  }

  private ProfileModel getOrCreateProfile(String userID, String phoneNumber) {
    Optional<ProfileModel> profileModel = profileDao.getProfileByUserId(userID);
    if (profileModel.isPresent()) {
      logger.atError().log("Found profile.");
      return profileModel.get();
    }
    logger.atError().log("Creating and potentially replacing profile.");
    return profileDao.createProfile(userID, phoneNumber);
  }

  private static String getGoogleCloudApiKey(Client client) {
    switch (client) {
      case ANDROID:
        return "AIzaSyBA4xAfhAB0z4g4Es9q0na9H40XdiybohM";
      case IOS:
        return "AIzaSyDGJzblLeJUEs3N0XO9vkonIkmDoX4zdXw";
      case WEB:
        return "AIzaSyCFw0FVQvic4xrScJufvcG4pHw-Yddxk1I";
      default:
        throw new IllegalStateException("Unsupported client: " + client);
    }
  }
}
