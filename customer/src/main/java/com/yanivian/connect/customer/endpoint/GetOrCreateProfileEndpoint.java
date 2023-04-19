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
import com.yanivian.connect.common.guice.GuiceEndpoint.AllowPost;
import com.yanivian.connect.customer.dao.ImageDao;
import com.yanivian.connect.customer.dao.ImageDao.ImageModel;
import com.yanivian.connect.customer.dao.ProfileDao;
import com.yanivian.connect.customer.dao.ProfileDao.ProfileModel;
import com.yanivian.connect.customer.proto.model.Profile;

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

    ProfileModel profileModel = profileDao.getProfileByUserId(userID.get())
        .orElse(profileDao.createProfile(userID.get(), getPhoneNumberOrFail(req)));
    Profile profile = profileModel.toProto();

    Optional<ImageModel> imageModel = profileModel.getImage().flatMap(imageDao::getImage);
    if (imageModel.isPresent()) {
      profile = profile.toBuilder().setImage(imageModel.get().toProto()).build();
    }

    writeJsonResponse(resp, profile);
  }

  private String getPhoneNumberOrFail(HttpServletRequest req) {
    String phoneNumber = req.getParameter(PARAM_PHONE_NUMBER);
    Preconditions.checkArgument(!Strings.isNullOrEmpty(phoneNumber),
        "Missing parameter: " + PARAM_PHONE_NUMBER);
    return phoneNumber;
  }
}
