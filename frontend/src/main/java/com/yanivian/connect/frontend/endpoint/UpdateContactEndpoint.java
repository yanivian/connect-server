package com.yanivian.connect.frontend.endpoint;

import java.io.IOException;
import java.util.Optional;
import javax.inject.Inject;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.yanivian.connect.common.guice.GuiceEndpoint;
import com.yanivian.connect.common.guice.GuiceEndpoint.AllowPost;
import com.yanivian.connect.backend.dao.ContactDao;
import com.yanivian.connect.backend.dao.ContactDao.ContactModel;

@WebServlet(name = "UpdateContactEndpoint", urlPatterns = {"/contact/update"})
@AllowPost
public final class UpdateContactEndpoint extends GuiceEndpoint {

  private static final String PARAM_PHONE_NUMBER = "phoneNumber";
  private static final String PARAM_NAME = "name";

  @Inject
  private AuthHelper authHelper;
  @Inject
  private ContactDao contactDao;

  // Servlets must have public no-arg constructors.
  public UpdateContactEndpoint() {}

  @Override
  protected void process(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    Optional<String> userID = authHelper.getVerifiedUserID(req, resp);
    if (!userID.isPresent()) {
      return;
    }

    ContactModel contact = contactDao.createOrUpdateContact(userID.get(),
        getRequiredParameter(req, PARAM_PHONE_NUMBER), getRequiredParameter(req, PARAM_NAME));

    writeJsonResponse(resp, contact.toProto());
  }
}
