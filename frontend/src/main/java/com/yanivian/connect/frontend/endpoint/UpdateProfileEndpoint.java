package com.yanivian.connect.frontend.endpoint;

import java.io.IOException;
import java.util.Optional;
import javax.inject.Inject;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.google.appengine.repackaged.com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.yanivian.connect.common.guice.GuiceEndpoint;
import com.yanivian.connect.common.guice.GuiceEndpoint.AllowPost;
import com.yanivian.connect.frontend.dao.ImageDao;
import com.yanivian.connect.frontend.dao.ImageDao.ImageModel;
import com.yanivian.connect.frontend.dao.ProfileDao;
import com.yanivian.connect.frontend.dao.ProfileDao.ProfileModel;
import com.yanivian.connect.frontend.proto.model.Profile;

@WebServlet(name = "UpdateProfileEndpoint", urlPatterns = {"/profile/update"})
@AllowPost
public final class UpdateProfileEndpoint extends GuiceEndpoint {

  private static final String PARAM_NAME = "name";
  private static final String PARAM_EMAIL_ADDRESS = "emailAddress";
  private static final String PARAM_IMAGE = "image";

  @Inject
  private AuthHelper authHelper;
  @Inject
  private ProfileDao profileDao;
  @Inject
  private ImageDao imageDao;

  public UpdateProfileEndpoint() {}

  @Override
  protected void process(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    Optional<String> userID = authHelper.getVerifiedUserID(req, resp);
    if (!userID.isPresent()) {
      return;
    }

    Optional<ProfileModel> optionalProfileModel = profileDao.getProfileByUserId(userID.get());
    if (!optionalProfileModel.isPresent()) {
      resp.sendError(404, "Profile not found: " + userID.get());
      return;
    }
    ProfileModel profileModel = optionalProfileModel.get();

    profileModel.setName(getOptionalParameter(req, PARAM_NAME));
    profileModel.setEmailAddress(getOptionalParameter(req, PARAM_EMAIL_ADDRESS));

    Optional<String> image = getOptionalParameter(req, PARAM_IMAGE);
    profileModel.setImage(image);

    Optional<ImageModel> imageModel = Optional.empty();
    if (image.isPresent()) {
      imageModel = imageDao.getImage(image.get());
      Preconditions.checkState(
          imageModel.isPresent() && Objects.equal(userID.get(), imageModel.get().getUserID()),
          "Bad image in request: " + image.get());
    }

    profileModel.save();

    Profile profile = profileModel.toProto();
    if (imageModel.isPresent()) {
      profile = profile.toBuilder().setImage(imageModel.get().toProto()).build();
    }

    writeJsonResponse(resp, profile);
  }

  private static Optional<String> getOptionalParameter(HttpServletRequest req, String name) {
    String value = req.getParameter(name);
    return Strings.isNullOrEmpty(value) ? Optional.empty() : Optional.of(value);
  }
}
