package com.yanivian.connect.frontend.dao;

import java.util.function.Function;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.Transaction;

public final class DatastoreUtil {

  // Not instantiable.
  private DatastoreUtil() {}

  public static <T> T newTransaction(DatastoreService datastore, Function<Transaction, T> logic) {
    Transaction txn = datastore.beginTransaction();
    try {
      return logic.apply(txn);
    } finally {
      if (txn.isActive()) {
        txn.rollback();
      }
    }
  }
}
