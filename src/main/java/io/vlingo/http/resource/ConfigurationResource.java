// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.http.resource;

import io.vlingo.actors.Definition;
import io.vlingo.actors.Stage;
import io.vlingo.common.compiler.DynaClassLoader;
import io.vlingo.common.compiler.DynaCompiler;
import io.vlingo.common.compiler.DynaCompiler.Input;
import io.vlingo.http.Method;
import io.vlingo.http.resource.Action.MatchResults;
import io.vlingo.http.resource.ResourceDispatcherGenerator.Result;

import java.lang.reflect.Constructor;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public abstract class ConfigurationResource<T> extends Resource {
  static final String DispatcherPostixName = "Dispatcher";

  private static DynaClassLoader classLoader = new DynaClassLoader(ConfigurationResource.class.getClassLoader());
  private static final DynaCompiler dynaCompiler = new DynaCompiler();

  final List<Action> actions;
  private final ResourceRequestHandler[] handlerPool;
  private final AtomicLong handlerPoolIndex;
  public final int handlerPoolSize;
  public final String name;
  public final Class<? extends ResourceHandler> resourceHandlerClass;

  public static ConfigurationResource<?> defining(
          final String resourceName,
          final Class<? extends ResourceHandler> resourceHandlerClass,
          final int handlerPoolSize,
          final List<Action> actions) {
    return newResourceFor(resourceName, resourceHandlerClass, handlerPoolSize, actions);
  }

  @SuppressWarnings("unchecked")
  static ConfigurationResource<?> newResourceFor(
          final String resourceName,
          final Class<? extends ResourceHandler> resourceHandlerClass,
          final int handlerPoolSize,
          final List<Action> actions) {

    try {
      final String targetClassname = resourceHandlerClass.getName() + DispatcherPostixName;

      Class<ConfigurationResource<?>> resourceClass = null;
      try {
        // this check is done primarily for testing to prevent duplicate class type in class loader
        resourceClass = (Class<ConfigurationResource<?>>) Class.forName(targetClassname);
      } catch (Exception e) {
        resourceClass = tryGenerateCompile(resourceHandlerClass, targetClassname, actions);
      }

      final Object[] ctorParams = new Object[] { resourceName, resourceHandlerClass, handlerPoolSize, actions };
      for (final Constructor<?> ctor : resourceClass.getConstructors()) {
        if (ctor.getParameterCount() == ctorParams.length) {
          final ConfigurationResource<?> resourecDispatcher = (ConfigurationResource<?>) ctor.newInstance(ctorParams);
          return resourecDispatcher;
        }
      }
      return resourceClass.newInstance();
    } catch (Exception e) {
      throw new IllegalArgumentException("Cannot create a resource from resource handler " + resourceHandlerClass.getName() + " because: " + e.getMessage());
    }
  }

  @SuppressWarnings("unchecked")
  static Class<? extends ResourceHandler> newResourceHandlerClassFor(final String resourceHandlerClassname) {
    try {
      final Class<? extends ResourceHandler> resourceHandlerClass = (Class<? extends ResourceHandler>) Class.forName(resourceHandlerClassname);
      confirmResourceHandler(resourceHandlerClass);
      return resourceHandlerClass;
    } catch (Exception e) {
      throw new IllegalArgumentException("The resource handler class " + resourceHandlerClassname + " cannot be loaded because: " + e.getMessage());
    }
  }

  private static void confirmResourceHandler(Class<?> resourceHandlerClass) {
    Class<?> superclass = resourceHandlerClass.getSuperclass();
    while (superclass != null) {
      if (superclass == ResourceHandler.class) {
        return;
      }
      superclass = superclass.getSuperclass();
    }
    throw new IllegalStateException("ConfigurationResource handler class must extends ResourceHandler: " + resourceHandlerClass.getName());
  }

  private static Class<ConfigurationResource<?>> tryGenerateCompile(
          final Class<? extends ResourceHandler> resourceHandlerClass,
          final String targetClassname,
          final List<Action> actions) {
    try (final ResourceDispatcherGenerator generator = ResourceDispatcherGenerator.forMain(actions, true)) {
      return tryGenerateCompile(resourceHandlerClass, generator, targetClassname);
    } catch (Exception emain) {
      try (final ResourceDispatcherGenerator generator = ResourceDispatcherGenerator.forTest(actions, true)) {
        return tryGenerateCompile(resourceHandlerClass, generator, targetClassname);
      } catch (Exception etest) {
        etest.printStackTrace();
        throw new IllegalArgumentException("ConfigurationResource dispatcher for " + resourceHandlerClass.getName() + " not created for main or test because: " + etest.getMessage(), etest);
      }
    }
  }

  private static Class<ConfigurationResource<?>> tryGenerateCompile(
          final Class<? extends ResourceHandler> resourceHandlerClass,
          final ResourceDispatcherGenerator generator,
          final String targetClassname) {
    try {
      final Result result = generator.generateFor(resourceHandlerClass.getName());
      final Input input = new Input(resourceHandlerClass, targetClassname, result.source, result.sourceFile, classLoader, generator.type(), true);
      final Class<ConfigurationResource<?>> resourceDispatcherClass = dynaCompiler.compile(input);
      return resourceDispatcherClass;
    } catch (Exception e) {
      throw new IllegalArgumentException("ConfigurationResource instance with dispatcher for " + resourceHandlerClass.getName() + " not created because: " + e.getMessage(), e);
    }
  }

  void allocateHandlerPool(final Stage stage) {
    for (int idx = 0; idx < handlerPoolSize; ++idx) {
      handlerPool[idx] =
              stage.actorFor(
                      Definition.has(
                              ResourceRequestHandlerActor.class,
                              Definition.parameters(resourceHandlerInstance(stage))),
                      ResourceRequestHandler.class);
    }
  }


  MatchResults matchWith(final Method method, final URI uri) {
    for (final Action action : actions) {
      final MatchResults matchResults = action.matchWith(method, uri);
      if (matchResults.isMatched()) {
        return matchResults;
      }
    }
    return Action.unmatchedResults;
  }

  protected ConfigurationResource(
          final String name,
          final Class<? extends ResourceHandler> resourceHandlerClass,
          final int handlerPoolSize,
          final List<Action> actions) {

    this.name = name;
    this.resourceHandlerClass = resourceHandlerClass;
    this.handlerPoolSize = handlerPoolSize;
    this.actions = Collections.unmodifiableList(actions);
    this.handlerPool = new ResourceRequestHandler[handlerPoolSize];
    this.handlerPoolIndex = new AtomicLong(0);
  }

  protected ResourceRequestHandler pooledHandler() {
    final int index = (int)(handlerPoolIndex.incrementAndGet() % handlerPoolSize);
    return handlerPool[index];
  }

  private ResourceHandler resourceHandlerInstance(final Stage stage) {
    try {
      for (final Constructor<?> ctor : resourceHandlerClass.getConstructors()) {
        if (ctor.getParameterCount() == 1) {
          return (ResourceHandler) ctor.newInstance(new Object[] { stage.world() } );
        }
      }
      return resourceHandlerClass.newInstance();
    } catch (Exception e) {
      throw new IllegalArgumentException("The instance for resource handler '" + resourceHandlerClass.getName() + "' cannot be created.");
    }
  }
}
