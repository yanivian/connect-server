package com.yanivian.connect.frontend.endpoint;

import java.io.IOException;
import java.util.Optional;
import javax.inject.Inject;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.yanivian.connect.common.guice.GuiceEndpoint;
import com.yanivian.connect.common.guice.GuiceEndpoint.AllowPost;
import com.yanivian.connect.frontend.dao.ImageDao;
import com.yanivian.connect.frontend.dao.ImageDao.ImageModel;
import com.yanivian.connect.frontend.dao.ProfileDao;
import com.yanivian.connect.frontend.dao.ProfileDao.ProfileModel;
import com.yanivian.connect.frontend.proto.model.Profile;

@WebServlet(name = "GetProfileEndpoint", urlPatterns = {"/profile/get"})
@AllowPost
public final class GetProfileEndpoint extends GuiceEndpoint {

  @Inject
  private AuthHelper authHelper;
  @Inject
  private ProfileDao profileDao;
  @Inject
  private ImageDao imageDao;

  public GetProfileEndpoint() {}

  @Override
  protected void process(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    Optional<String> userID = authHelper.getVerifiedUserID(req, resp);
    if (!userID.isPresent()) {
      return;
    }
    Optional<ProfileModel> profileModel = profileDao.getProfileByUserId(userID.get());
    if (!profileModel.isPresent()) {
      resp.sendError(404);
      return;
    }
    Profile profile = profileModel.get().toProto();

    Optional<ImageModel> imageModel = profileModel.get().getImage().flatMap(imageDao::getImage);
    if (imageModel.isPresent()) {
      profile = profile.toBuilder().setImage(imageModel.get().toProto()).build();
    }

    writeJsonResponse(resp, profile);
  }
}
