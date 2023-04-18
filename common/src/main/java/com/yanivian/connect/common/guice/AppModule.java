package com.yanivian.connect.common.guice;

import java.io.IOException;
import java.time.Clock;
import javax.inject.Singleton;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.images.ImagesService;
import com.google.appengine.api.images.ImagesServiceFactory;
import com.google.appengine.tools.cloudstorage.GcsService;
import com.google.appengine.tools.cloudstorage.GcsServiceFactory;
import com.google.appengine.tools.cloudstorage.RetryParams;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;

public class AppModule extends AbstractModule {

  @Override
  protected void configure() {}

  @Provides
  @Singleton
  Clock provideClock() {
    return Clock.systemUTC();
  }

  @Provides
  @Singleton
  DatastoreService provideDatastoreService() {
    return DatastoreServiceFactory.getDatastoreService();
  }

  @Provides
  @Singleton
  FirebaseApp provideFirebaseApp() throws IOException {
    FirebaseOptions options =
        FirebaseOptions.builder().setCredentials(GoogleCredentials.getApplicationDefault()).build();
    return FirebaseApp.initializeApp(options);
  }

  @Provides
  @Singleton
  FirebaseAuth provideFirebaseAuth(FirebaseApp app) {
    return FirebaseAuth.getInstance(app);
  }

  @Provides
  @Singleton
  GcsService provideGcsService() {
    return GcsServiceFactory.createGcsService(new RetryParams.Builder().initialRetryDelayMillis(10)
        .retryMaxAttempts(10).totalRetryPeriodMillis(15000).build());
  }

  @Provides
  @Singleton
  ImagesService provideImagesService() {
    return ImagesServiceFactory.getImagesService();
  }
}
