package com.yanivian.connect.customer.endpoint;

import java.io.IOException;
import java.util.Optional;
import javax.inject.Inject;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.yanivian.connect.common.guice.GuiceEndpoint;
import com.yanivian.connect.common.guice.GuiceEndpoint.AllowGet;
import com.yanivian.connect.common.guice.GuiceEndpoint.AllowPost;
import com.yanivian.connect.customer.dao.ProfileDao;
import com.yanivian.connect.customer.dao.ProfileDao.ProfileModel;

@WebServlet(name = "GetProfileEndpoint", urlPatterns = {"/profile/get"})
@AllowGet
@AllowPost
public final class GetProfileEndpoint extends GuiceEndpoint {

  private final AuthHelper authHelper;
  private final ProfileDao profileDao;

  @Inject
  GetProfileEndpoint(AuthHelper authHelper, ProfileDao profileDao) {
    this.authHelper = authHelper;
    this.profileDao = profileDao;
  }

  @Override
  protected void process(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    Optional<String> userID = authHelper.getVerifiedUserID(req, resp);
    if (!userID.isPresent()) {
      return;
    }

    Optional<ProfileModel> profileModel = profileDao.getProfileByUserId(userID.get());
    if (!profileModel.isPresent()) {
      resp.sendError(404, "Profile not found");
      return;
    }

    writeJsonResponse(resp, profileModel.get().toProto());
  }
}
