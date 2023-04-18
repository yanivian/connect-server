package com.yanivian.connect.customer.endpoint;

import java.io.IOException;
import java.util.Optional;
import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import com.google.common.collect.ImmutableSet;
import com.yanivian.connect.common.guice.GuiceEndpoint;
import com.yanivian.connect.common.guice.GuiceEndpoint.AllowPost;
import com.yanivian.connect.customer.dao.ImageDao;
import com.yanivian.connect.customer.dao.ImageDao.ImageModel;

@WebServlet(name = "UploadImageEndpoint", urlPatterns = {"/image/upload"})
@MultipartConfig(maxFileSize = 10 * 1024 * 1024, // 10MB
    maxRequestSize = 20 * 1024 * 1024, // 20MB
    fileSizeThreshold = 5 * 1024 * 1024 // 5MB
)
@AllowPost
public class UploadImageEndpoint extends GuiceEndpoint {

  private static final ImmutableSet<String> SUPPORTED_FILE_EXTENSIONS =
      ImmutableSet.of("jpg", "jpeg", "png");

  @Inject
  private AuthHelper authHelper;
  @Inject
  private ImageDao imageDao;

  // Servlets must have public no-arg constructors.
  public UploadImageEndpoint() {}

  @Override
  protected void process(HttpServletRequest req, HttpServletResponse resp)
      throws IOException, ServletException {
    Optional<String> userID = authHelper.getVerifiedUserID(req, resp);
    if (!userID.isPresent()) {
      return;
    }

    Part filePart = req.getPart("image");

    // Check extension of file.
    final String fileName = filePart.getSubmittedFileName();
    if (fileName != null && !fileName.isEmpty() && fileName.contains(".")) {
      final String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
      if (!SUPPORTED_FILE_EXTENSIONS.contains(extension)) {
        throw new IllegalStateException("Unsupported file extension: " + extension);
      }
    }
    ImageModel model = imageDao.createImage(filePart.getInputStream(), userID.get());
    writeJsonResponse(resp, model.toProto());
  }
}
