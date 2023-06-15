package com.yanivian.connect.backend.taskqueue;

import javax.inject.Inject;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.TaskHandle;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.taskqueue.TaskOptions.Method;
import com.yanivian.connect.common.guice.BindingAnnotations.AsyncTaskQueue;

public final class AsyncTaskQueueAdapter {

  private final Queue taskQueue;

  @Inject
  AsyncTaskQueueAdapter(@AsyncTaskQueue Queue taskQueue) {
    this.taskQueue = taskQueue;
  }

  public TaskHandle notifyConnectionAdded(Transaction txn, String ownerUserID,
      String targetUserID) {
    return taskQueue.add(txn, TaskOptions.Builder.withMethod(Method.POST).url("/connection/added")
        .param("ownerUserID", ownerUserID).param("targetUserID", targetUserID));
  }
}