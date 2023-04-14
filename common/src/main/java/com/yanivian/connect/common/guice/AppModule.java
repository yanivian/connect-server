package com.yanivian.connect.common.guice;

import java.io.IOException;
import java.time.Clock;

import javax.inject.Singleton;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
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
}
