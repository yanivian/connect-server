package com.yanivian.connect.backend.dao;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.inject.Inject;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Transaction;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.yanivian.connect.backend.dao.BlobDao.BlobNamespace;
import com.yanivian.connect.backend.proto.model.Image;

public final class ImageDao {

  private static final BlobNamespace IMAGES = BlobNamespace.IMAGE;

  private final DatastoreService datastore;
  private final BlobDao blobDao;
  private final Clock clock;

  @Inject
  ImageDao(DatastoreService datastore, BlobDao blobDao, Clock clock) {
    this.datastore = datastore;
    this.blobDao = blobDao;
    this.clock = clock;
  }

  /**
   * Uploads bytes representing an image as a blob and creates image metdata in datastore.
   *
   * @throws IOException
   */
  public ImageModel createImage(Transaction txn, InputStream inputStream, String userID)
      throws IOException {
    String id = UUID.randomUUID().toString();

    blobDao.createOrReplace(id, IMAGES, inputStream);
    String url = blobDao.getImageUrl(id, IMAGES);

    Entity entity = new Entity(ImageModel.KIND, id);
    return new ImageModel(entity).setUserID(userID).setURL(url).save(txn, datastore, clock);
  }

  /** Fetches image metadata if present. */
  public Optional<ImageModel> getImage(Transaction txn, String imageID) {
    try {
      Entity entity = datastore.get(txn, toKey(imageID));
      return Optional.of(new ImageModel(entity));
    } catch (EntityNotFoundException enfe) {
      return Optional.empty();
    }
  }

  /** Fetches image metadata for multiple images. */
  public ImmutableMap<String, ImageModel> getImages(Transaction txn,
      ImmutableSet<String> imageIDs) {
    Map<Key, Entity> entityMap = datastore.get(txn, Iterables.transform(imageIDs, ImageDao::toKey));
    return entityMap.entrySet().stream().collect(ImmutableMap.toImmutableMap(
        entry -> entry.getKey().getName(), entry -> new ImageModel(entry.getValue())));
  }

  /** Irreversably deletes both the image metadata and the image blob. */
  public void deleteImage(Transaction txn, ImageModel image, String actorID) throws IOException {
    Preconditions.checkState(Objects.equals(actorID, image.getUserID()));
    // TODO: Handle failure scenarios.
    blobDao.delete(image.getID(), IMAGES);
    datastore.delete(txn, image.getKey());
  }

  public static class ImageModel extends DatastoreModel<ImageModel> {

    static final String KIND = "Image";
    private static final String PROPERTY_USER_ID = "UserID";
    private static final String PROPERTY_URL = "URL";

    private ImageModel(Entity entity) {
      super(entity);
    }

    /** Returns a protobuf model representing the underlying entity. */
    public Image toProto() {
      return Image.newBuilder().setID(getID()).setUserID(getUserID())
          .setTimestampMillis(getTimestampMillis()).setURL(getURL()).build();
    }

    public String getID() {
      return getKey().getName();
    }

    public String getURL() {
      return (String) entity.getProperty(PROPERTY_URL);
    }

    public String getUserID() {
      return (String) entity.getProperty(PROPERTY_USER_ID);
    }

    private ImageModel setURL(String url) {
      entity.setUnindexedProperty(PROPERTY_URL, url);
      return this;
    }

    private ImageModel setUserID(String userID) {
      entity.setProperty(PROPERTY_USER_ID, userID);
      return this;
    }
  }

  static Key toKey(String imageID) {
    return KeyFactory.createKey(ImageModel.KIND, imageID);
  }
}
