package com.yanivian.connect.common.guice;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletContextEvent;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;
import com.google.inject.servlet.GuiceServletContextListener;

public class AppListener extends GuiceServletContextListener {

  private final Supplier<Injector> injector = Suppliers.memoize(this::createInjector);

  @Override
  protected Injector getInjector() {
    return injector.get();
  }

  @Override
  public void contextInitialized(ServletContextEvent servletContextEvent) {
    servletContextEvent.getServletContext().setAttribute(GuiceEndpoint.GUICE_INJECTOR_ATTRIBUTE_NAME, injector.get());
    super.contextInitialized(servletContextEvent);
  }

  private Injector createInjector() {
    ModuleCollector moduleCollector = new ModuleCollector();
    collectModules(moduleCollector);
    return Guice.createInjector(Stage.PRODUCTION, moduleCollector.getCollectedModules());
  }

  /**
   * Override this method in your subclass to include additional modules.
   */
  protected void collectModules(ModuleCollector collector) {
    collector.add(new AppModule());
  }

  public static class ModuleCollector {

    private final List<Module> collectedModules = new ArrayList<>();

    public ModuleCollector add(Module... modules) {
      for (Module m : modules) {
        collectedModules.add(m);
      }
      return this;
    }

    private List<Module> getCollectedModules() {
      return collectedModules;
    }
  }
}
