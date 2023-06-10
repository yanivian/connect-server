package com.yanivian.connect.common.guice;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.inject.Qualifier;

public final class BindingAnnotations {

  // Not instantiable.
  private BindingAnnotations() {}

  @Qualifier
  @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
  @Retention(RetentionPolicy.RUNTIME)
  public @interface AsyncTaskQueue {
  }
}
