package com.yanivian.connect.frontend.endpoint;

import java.io.IOException;
import java.util.Optional;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;

final class AuthHelper {

  private static final String PARAM_ID = "id";
  private static final String PARAM_TOKEN = "token";

  private final Logger logger = LogManager.getLogger(getClass());

  private final FirebaseAuth firebaseAuth;

  @Inject
  AuthHelper(FirebaseAuth firebaseAuth) {
    this.firebaseAuth = firebaseAuth;
  }

  /**
   * Performs ID token verification.
   * 
   * @see https://firebase.google.com/docs/auth/admin/verify-id-tokens
   * @param req
   * @param resp
   * @return
   * @throws IOException
   */
  Optional<String> getVerifiedUserID(HttpServletRequest req, HttpServletResponse resp)
      throws IOException {
    String userID = req.getParameter(PARAM_ID);
    Preconditions.checkArgument(!Strings.isNullOrEmpty(userID), "Missing parameter " + PARAM_ID);
    String token = req.getParameter(PARAM_TOKEN);
    Preconditions.checkArgument(!Strings.isNullOrEmpty(token), "Missing parameter " + PARAM_TOKEN);

    FirebaseToken decodedToken;
    try {
      decodedToken = firebaseAuth.verifyIdToken(token);
    } catch (FirebaseAuthException fae) {
      logger.atError().withThrowable(fae).log("Failed to verify token.");
      resp.sendError(403, "Bad token");
      return Optional.empty();
    }

    if (!decodedToken.getUid().equals(userID)) {
      logger.atError().log("Token with unexpected uid: %s", decodedToken.getUid());
      resp.sendError(403, "Bad token");
      return Optional.empty();
    }

    return Optional.of(userID);
  }
}
