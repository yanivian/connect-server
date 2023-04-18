package com.yanivian.connect.customer.dao;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.util.UUID;

import javax.inject.Inject;

import com.google.appengine.tools.cloudstorage.GcsFileOptions;
import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.appengine.tools.cloudstorage.GcsInputChannel;
import com.google.appengine.tools.cloudstorage.GcsOutputChannel;
import com.google.appengine.tools.cloudstorage.GcsService;
import com.google.common.io.ByteStreams;

public class BlobDao {

  private static final int READ_PREFETCH_BUFFER_SIZE = 2 * 1024 * 1024; // 2MB

  private final GcsService storage;

  @Inject
  BlobDao(GcsService storage) {
    this.storage = storage;
  }

  public void createOrReplace(String id, String bucketName, InputStream inputStream)
      throws IOException {
    GcsFilename gcsFile = new GcsFilename(bucketName, id);
    GcsFileOptions fileOptions = GcsFileOptions.getDefaultInstance();
    GcsOutputChannel outputChannel = storage.createOrReplace(gcsFile, fileOptions);
    try (OutputStream outputStream = Channels.newOutputStream(outputChannel)) {
      ByteStreams.copy(inputStream, outputStream);
    }
  }

  public void fetch(String id, String bucketName, OutputStream targetStream) throws IOException {
    GcsFilename gcsFile = new GcsFilename(bucketName, id);
    GcsInputChannel readChannel =
        storage.openPrefetchingReadChannel(gcsFile, 0, READ_PREFETCH_BUFFER_SIZE);
    ByteStreams.copy(Channels.newInputStream(readChannel), targetStream);
  }

  public boolean delete(String id, String bucketName) throws IOException {
    return storage.delete(new GcsFilename(bucketName, id));
  }
}
