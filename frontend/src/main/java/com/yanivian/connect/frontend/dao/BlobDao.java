package com.yanivian.connect.frontend.dao;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import javax.inject.Inject;
import com.google.appengine.api.images.ImagesService;
import com.google.appengine.api.images.ServingUrlOptions;
import com.google.appengine.tools.cloudstorage.GcsFileOptions;
import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.appengine.tools.cloudstorage.GcsInputChannel;
import com.google.appengine.tools.cloudstorage.GcsOutputChannel;
import com.google.appengine.tools.cloudstorage.GcsService;
import com.google.common.io.ByteStreams;

public class BlobDao {

  public enum BlobNamespace {
    IMAGE("images-connect"),;

    private final String bucketName;

    private BlobNamespace(String bucketName) {
      this.bucketName = bucketName;
    }
  }

  private static final int READ_PREFETCH_BUFFER_SIZE = 2 * 1024 * 1024; // 2MB

  private final GcsService storage;
  private final ImagesService imagesService;

  @Inject
  BlobDao(GcsService storage, ImagesService imagesService) {
    this.storage = storage;
    this.imagesService = imagesService;
  }

  public void createOrReplace(String id, BlobNamespace namespace, InputStream inputStream)
      throws IOException {
    GcsFilename gcsFile = new GcsFilename(namespace.bucketName, id);
    GcsFileOptions fileOptions = GcsFileOptions.getDefaultInstance();
    GcsOutputChannel outputChannel = storage.createOrReplace(gcsFile, fileOptions);
    try (OutputStream outputStream = Channels.newOutputStream(outputChannel)) {
      ByteStreams.copy(inputStream, outputStream);
    }
  }

  public void fetch(String id, BlobNamespace namespace, OutputStream targetStream)
      throws IOException {
    GcsFilename gcsFile = new GcsFilename(namespace.bucketName, id);
    GcsInputChannel readChannel =
        storage.openPrefetchingReadChannel(gcsFile, 0, READ_PREFETCH_BUFFER_SIZE);
    ByteStreams.copy(Channels.newInputStream(readChannel), targetStream);
  }

  public boolean delete(String id, BlobNamespace namespace) throws IOException {
    return storage.delete(new GcsFilename(namespace.bucketName, id));
  }

  public String getImageUrl(String imageId) {
    String gcsFilename = String.format("/gs/%s/%s", BlobNamespace.IMAGE, imageId);
    return imagesService.getServingUrl(
        ServingUrlOptions.Builder.withGoogleStorageFileName(gcsFilename).secureUrl(true));
  }
}
