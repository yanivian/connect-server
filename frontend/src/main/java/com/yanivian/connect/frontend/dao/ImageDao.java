package com.yanivian.connect.frontend.dao;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.inject.Inject;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import com.google.common.base.Preconditions;
import com.yanivian.connect.frontend.dao.BlobDao.BlobNamespace;
import com.yanivian.connect.frontend.proto.model.Image;

public class ImageDao {

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
  public ImageModel createImage(InputStream inputStream, String userID) throws IOException {
    String id = UUID.randomUUID().toString();

    blobDao.createOrReplace(id, IMAGES, inputStream);
    String url = blobDao.getImageUrl(id, IMAGES);

    Entity entity = new Entity(ImageModel.KIND, id);
    return new ImageModel(entity, datastore).setUserID(userID)
        .setCreatedTimestampMillis(clock.millis()).setURL(url).save();
  }

  /** Fetches image metadata if present. */
  public Optional<ImageModel> getImage(String imageID) {
    Query imageQuery =
        new Query(ImageModel.KIND).setFilter(new Query.FilterPredicate(Entity.KEY_RESERVED_PROPERTY,
            Query.FilterOperator.EQUAL, toKey(imageID)));
    Entity entity = datastore.prepare(imageQuery).asSingleEntity();
    return Optional.ofNullable(entity == null ? null : new ImageModel(entity, datastore));
  }

  /** Irreversably deletes both the image metadata and the image blob. */
  public void deleteImage(ImageModel image, String actorID) throws IOException {
    Preconditions.checkState(Objects.equals(actorID, image.getUserID()));
    // TODO: Handle failure scenarios.
    blobDao.delete(image.getID(), IMAGES);
    datastore.delete(toKey(image.getID()));
  }

  public class ImageModel extends DatastoreModel<Image, ImageModel> {

    static final String KIND = "Image";
    private static final String PROPERTY_USER_ID = "UserID";
    private static final String PROPERTY_CREATED_TIMESTAMP_MILLIS = "CreatedTimestampMillis";
    private static final String PROPERTY_URL = "URL";

    private ImageModel(Entity entity, DatastoreService datastore) {
      super(entity, datastore);
    }

    @Override
    public Image toProto() {
      return Image.newBuilder().setID(getID()).setUserID(getUserID())
          .setCreatedTimestampMillis(clock.millis()).setURL(getURL()).build();
    }

    public String getURL() {
      return (String) entity.getProperty(PROPERTY_URL);
    }

    public String getUserID() {
      return (String) entity.getProperty(PROPERTY_USER_ID);
    }

    public long getCreatedTimestampMillis() {
      return (long) entity.getProperty(PROPERTY_CREATED_TIMESTAMP_MILLIS);
    }

    private ImageModel setURL(String url) {
      entity.setProperty(PROPERTY_URL, url);
      return this;
    }

    private ImageModel setUserID(String userID) {
      entity.setProperty(PROPERTY_USER_ID, userID);
      return this;
    }

    private ImageModel setCreatedTimestampMillis(long timestampMillis) {
      entity.setProperty(PROPERTY_CREATED_TIMESTAMP_MILLIS, timestampMillis);
      return this;
    }
  }

  static Key toKey(String imageID) {
    return KeyFactory.createKey(ImageModel.KIND, imageID);
  }
}
