package com.yanivian.connect.frontend.endpoint;

import java.io.IOException;
import java.util.Optional;
import javax.inject.Inject;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.yanivian.connect.common.guice.GuiceEndpoint;
import com.yanivian.connect.common.guice.GuiceEndpoint.AllowPost;
import com.yanivian.connect.frontend.aspect.ConnectionsAspect;
import com.yanivian.connect.frontend.proto.api.AddConnectionResult;

@WebServlet(name = "AddConnectionEndpoint", urlPatterns = {"/connection/add"})
@AllowPost
public final class AddConnectionEndpoint extends GuiceEndpoint {

  private static final String PARAM_TARGET_USER_ID = "targetUserID";

  @Inject
  private AuthHelper authHelper;
  @Inject
  private ConnectionsAspect connectionsAspect;

  // Servlets must have public no-arg constructors.
  public AddConnectionEndpoint() {}

  @Override
  protected void process(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    Optional<String> userID = authHelper.getVerifiedUserID(req, resp);
    if (!userID.isPresent()) {
      return;
    }

    String targetUserID = getRequiredParameter(req, PARAM_TARGET_USER_ID);
    AddConnectionResult result = connectionsAspect.addConnection(userID.get(), targetUserID);

    writeJsonResponse(resp, result);
  }
}
