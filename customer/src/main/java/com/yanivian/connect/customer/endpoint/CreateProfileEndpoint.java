package com.yanivian.connect.customer.endpoint;

import java.io.IOException;
import java.util.Optional;
import javax.inject.Inject;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.yanivian.connect.common.guice.GuiceEndpoint;
import com.yanivian.connect.common.guice.GuiceEndpoint.AllowGet;
import com.yanivian.connect.common.guice.GuiceEndpoint.AllowPost;
import com.yanivian.connect.customer.dao.ProfileDao;
import com.yanivian.connect.customer.dao.ProfileDao.ProfileModel;
import com.yanivian.connect.customer.proto.model.Profile;

@WebServlet(name = "CreateProfileEndpoint", urlPatterns = {"/profile/create"})
@AllowGet
@AllowPost
public final class CreateProfileEndpoint extends GuiceEndpoint {

  private static final String PARAM_PHONE_NUMBER = "phoneNumber";
  private static final String PARAM_NAME = "name";
  private static final String PARAM_EMAIL_ADDRESS = "emailAddress";

  private final AuthHelper authHelper;
  private final ProfileDao profileDao;

  @Inject
  CreateProfileEndpoint(AuthHelper authHelper, ProfileDao profileDao) {
    this.authHelper = authHelper;
    this.profileDao = profileDao;
  }

  @Override
  protected void process(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    Optional<String> userID = authHelper.getVerifiedUserID(req, resp);
    if (!userID.isPresent()) {
      return;
    }

    Optional<ProfileModel> optionalProfileModel = profileDao.getProfileByUserId(userID.get());
    if (optionalProfileModel.isPresent()) {
      resp.sendError(409, "Profile already exists: " + userID.get());
      return;
    }

    String phoneNumber = req.getParameter(PARAM_PHONE_NUMBER);
    Preconditions.checkArgument(!Strings.isNullOrEmpty(phoneNumber),
        "Missing parameter " + PARAM_PHONE_NUMBER);
    Optional<String> name = getOptionalParameter(req, PARAM_NAME);
    Optional<String> emailAddress = getOptionalParameter(req, PARAM_EMAIL_ADDRESS);
    Profile.Builder profile =
        Profile.newBuilder().setUserID(userID.get()).setPhoneNumber(phoneNumber);
    if (name.isPresent()) {
      profile.setName(name.get());
    }
    if (emailAddress.isPresent()) {
      profile.setEmailAddress(emailAddress.get());
    }

    ProfileModel profileModel = profileDao.createProfile(profile.build());

    writeJsonResponse(resp, profileModel.toProto());
  }

  private static Optional<String> getOptionalParameter(HttpServletRequest req, String name) {
    String value = req.getParameter(name);
    return Strings.isNullOrEmpty(value) ? Optional.empty() : Optional.of(value);
  }
}
