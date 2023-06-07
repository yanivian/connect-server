package com.yanivian.connect.frontend.endpoint;

import java.io.IOException;
import java.util.Optional;
import javax.inject.Inject;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.yanivian.connect.common.guice.GuiceEndpoint;
import com.yanivian.connect.common.guice.GuiceEndpoint.AllowPost;
import com.yanivian.connect.frontend.dao.ConnectionDao;
import com.yanivian.connect.frontend.dao.ConnectionDao.ConnectionModel;
import com.yanivian.connect.frontend.proto.model.Connection.ConnectionState;

@WebServlet(name = "UpdateConnectionEndpoint", urlPatterns = {"/connection/update"})
@AllowPost
public final class UpdateConnectionEndpoint extends GuiceEndpoint {

  private static final String PARAM_TARGET_USER_ID = "targetUserID";
  private static final String PARAM_STATE = "state";

  @Inject
  private AuthHelper authHelper;
  @Inject
  private ConnectionDao connectionDao;

  // Servlets must have public no-arg constructors.
  public UpdateConnectionEndpoint() {}

  @Override
  protected void process(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    Optional<String> userID = authHelper.getVerifiedUserID(req, resp);
    if (!userID.isPresent()) {
      return;
    }

    String targetUserID = getRequiredParameter(req, PARAM_TARGET_USER_ID);
    ConnectionState state = ConnectionState.valueOf(getRequiredParameter(req, PARAM_STATE));

    ConnectionModel connection =
        connectionDao.createOrUpdateConnection(userID.get(), targetUserID, state);

    writeJsonResponse(resp, connection.toProto());
  }
}
