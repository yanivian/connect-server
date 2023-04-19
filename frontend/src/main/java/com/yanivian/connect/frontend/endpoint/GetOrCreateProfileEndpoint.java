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
import com.yanivian.connect.frontend.proto.model.Profile;

@WebServlet(name = "GetOrCreateProfileEndpoint", urlPatterns = {"/profile/getorcreate"})
@AllowPost
public final class GetOrCreateProfileEndpoint extends GuiceEndpoint {

  private static final String PARAM_PHONE_NUMBER = "phoneNumber";

  @Inject
  private AuthHelper authHelper;
  @Inject
  private ProfileDao profileDao;
  @Inject
  private ImageDao imageDao;

  public GetOrCreateProfileEndpoint() {}

  @Override
  protected void process(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    Optional<String> userID = authHelper.getVerifiedUserID(req, resp);
    if (!userID.isPresent()) {
      return;
    }

    String phoneNumber = req.getParameter(PARAM_PHONE_NUMBER);
    Preconditions.checkArgument(!Strings.isNullOrEmpty(phoneNumber),
        "Missing parameter: " + PARAM_PHONE_NUMBER);
    ProfileModel profileModel = getOrCreateProfile(userID.get(), phoneNumber);
    Profile profile = profileModel.toProto();

    Optional<ImageModel> imageModel = profileModel.getImage().flatMap(imageDao::getImage);
    if (imageModel.isPresent()) {
      profile = profile.toBuilder().setImage(imageModel.get().toProto()).build();
    }

    writeJsonResponse(resp, profile);
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
}
